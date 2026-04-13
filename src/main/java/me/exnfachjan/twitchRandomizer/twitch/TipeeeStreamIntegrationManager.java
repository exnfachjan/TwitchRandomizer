package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import okhttp3.*;
import okio.ByteString;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tipeeestream donation integration via WebSocket.
 * Config is read from donations.yml (shared with StreamElements).
 * API key: https://tipeeestream.com/dashboard/stream
 */
public class TipeeeStreamIntegrationManager {

    // Official Tipeeestream socket URL — API key goes as query param per their docs:
    // https://api.tipeeestream.com/api-doc/socketio
    private static final String TIPEEE_WSS_BASE = "wss://sso-cf.tipeeestream.com:443/socket.io/?EIO=3&transport=websocket&access_token=";

    private final JavaPlugin plugin;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private volatile boolean enabled = false;
    private volatile double amountPerTrigger = 5.0;
    private volatile boolean debug = false;

    private volatile String apiKey = "";
    private volatile WebSocket wsSocket;
    private volatile boolean shouldReconnect = false;
    private int reconnectAttempts = 0;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> pingTask;

    public TipeeeStreamIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(0, TimeUnit.SECONDS)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API (called by DonationsManager)
    // ─────────────────────────────────────────────────────────────────────────

    public void start(boolean enabled, String apiKey, double amountPerTrigger, boolean debug) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.amountPerTrigger = amountPerTrigger;
        this.debug = debug;

        if (!enabled) {
            if (debug) plugin.getLogger().info("[Tipeee] Tipeeestream deaktiviert (donations.yml).");
            return;
        }
        if (this.apiKey.isBlank() || this.apiKey.equals("YOUR_TIPEEESTREAM_APIKEY")) {
            plugin.getLogger().info("[Tipeee] Kein API-Key in donations.yml konfiguriert.");
            return;
        }
        shouldReconnect = true;
        connect();
    }

    public void stop() {
        shouldReconnect = false;
        cancelReconnect();
        cancelPing();
        WebSocket ws = wsSocket;
        wsSocket = null;
        if (ws != null) try { ws.close(1000, "Plugin stopping"); } catch (Throwable ignored) {}
        plugin.getLogger().info("[Tipeee] Verbindung getrennt.");
    }

    public void applyConfig(boolean enabled, String apiKey, double amountPerTrigger, boolean debug) {
        boolean changed = this.enabled != enabled
                || !Objects.equals(this.apiKey, apiKey == null ? "" : apiKey.trim())
                || this.amountPerTrigger != amountPerTrigger;

        this.amountPerTrigger = amountPerTrigger;
        this.debug = debug;

        if (!changed) {
            if (debug) plugin.getLogger().info("[Tipeee] applyConfig: Keine relevante Änderung.");
            return;
        }

        stop();
        start(enabled, apiKey, amountPerTrigger, debug);
    }

    public boolean getEnabled() { return enabled; }
    public double getAmountPerTrigger() { return amountPerTrigger; }

    // ─────────────────────────────────────────────────────────────────────────
    // WebSocket
    // ─────────────────────────────────────────────────────────────────────────

    private void connect() {
        httpClient.newWebSocket(
                new Request.Builder().url(TIPEEE_WSS_BASE + apiKey).build(),
                new TipeeeWebSocketListener());
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        cancelReconnect();
        long delay = Math.min(5L * (1L << Math.min(reconnectAttempts, 3)), 60L);
        reconnectAttempts++;
        plugin.getLogger().info("[Tipeee] Reconnect in " + delay + "s (Versuch " + reconnectAttempts + ")...");
        reconnectTask = scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        if (reconnectTask != null && !reconnectTask.isDone()) { reconnectTask.cancel(false); reconnectTask = null; }
    }

    private void cancelPing() {
        if (pingTask != null && !pingTask.isDone()) { pingTask.cancel(false); pingTask = null; }
    }

    private void handleMessage(String raw, WebSocket ws) {
        if (debug) plugin.getLogger().info("[Tipeee] <<< " + raw);

        // Socket.io handshake
        if (raw.startsWith("0")) {
            // Send authentication
            String auth = "42[\"join-room\",{\"room\":\"" + apiKey + "\",\"username\":\"twitchrandomizer\"}]";
            ws.send(auth);
            if (debug) plugin.getLogger().info("[Tipeee] Auth gesendet.");
            cancelPing();
            pingTask = scheduler.scheduleAtFixedRate(() -> {
                if (wsSocket != null) wsSocket.send("2");
            }, 25, 25, TimeUnit.SECONDS);
            return;
        }
        if (raw.equals("2")) { ws.send("3"); return; }
        if (raw.startsWith("40")) {
            plugin.getLogger().info("[Tipeee] Verbunden!");
            reconnectAttempts = 0;
            return;
        }
        if (!raw.startsWith("42")) return;

        String content = raw.substring(2);

        // Donation event
        if (!content.contains("\"new-event\"") && !content.contains("\"donation\"")) return;
        if (!content.contains("\"type\":\"donation\"") && !content.contains("\"formattedAmount\"")) return;

        String username = extractJsonString(content, "username");
        if (username == null || username.isBlank()) username = "TipeeeTip";

        // Tipeeestream sends amount as string like "5.00"
        double amount = extractJsonDouble(content, "amount");
        if (amount <= 0) {
            String amtStr = extractJsonString(content, "formattedAmount");
            if (amtStr != null) {
                try { amount = Double.parseDouble(amtStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
            }
        }

        String taggedUsername = "role:donation:" + username;

        if (debug) plugin.getLogger().info("[Tipeee] Donation von " + username + ": " + amount + " (min=" + amountPerTrigger + ")");
        if (amount < amountPerTrigger) {
            if (debug) plugin.getLogger().info("[Tipeee] Zu gering, ignoriert.");
            return;
        }
        if (!isTimerRunning()) {
            if (debug) plugin.getLogger().info("[Tipeee] Timer läuft nicht, ignoriert.");
            return;
        }

        int count = (int) Math.ceil(amount / amountPerTrigger);
        TwitchIntegrationManager twitch = getTwitch();
        if (twitch != null) {
            twitch.enqueueMultiple(count, taggedUsername);
            plugin.getLogger().info("[Tipeee] Donation: " + username + " -> " + amount + " -> +" + count + " Event(s) in Queue.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isTimerRunning() {
        try {
            if (plugin instanceof TwitchRandomizer t) {
                TimerManager tm = t.getTimerManager();
                return tm != null && tm.isRunning()
                        && !(t.getPauseService() != null && t.getPauseService().isPaused());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private TwitchIntegrationManager getTwitch() {
        if (plugin instanceof TwitchRandomizer t) return t.getTwitch();
        return null;
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private double extractJsonDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0.0;
        int start = idx + search.length();
        if (start < json.length() && json.charAt(start) == '"') {
            String strVal = extractJsonString(json, key);
            if (strVal != null) { try { return Double.parseDouble(strVal); } catch (Exception ignored) {} }
            return 0.0;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        try { return Double.parseDouble(json.substring(start, end)); } catch (Exception e) { return 0.0; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebSocket Listener
    // ─────────────────────────────────────────────────────────────────────────

    private class TipeeeWebSocketListener extends WebSocketListener {
        @Override public void onOpen(WebSocket ws, Response r) {
            wsSocket = ws;
            plugin.getLogger().info("[Tipeee] WebSocket verbunden.");
        }
        @Override public void onMessage(WebSocket ws, String text) { handleMessage(text, ws); }
        @Override public void onMessage(WebSocket ws, ByteString bytes) { handleMessage(bytes.utf8(), ws); }
        @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(1000, null); }
        @Override public void onClosed(WebSocket ws, int code, String reason) {
            if (wsSocket != ws && wsSocket != null) return;
            wsSocket = null; cancelPing();
            if (code == 1000) return;
            plugin.getLogger().warning("[Tipeee] Getrennt (Code " + code + "): " + reason);
            scheduleReconnect();
        }
        @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
            if (wsSocket != ws && wsSocket != null) return;
            wsSocket = null; cancelPing();
            plugin.getLogger().warning("[Tipeee] Fehler: " + t.getMessage());
            scheduleReconnect();
        }
    }
}