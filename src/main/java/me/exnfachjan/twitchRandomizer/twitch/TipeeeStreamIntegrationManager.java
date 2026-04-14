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
 * Supports multiple accounts via "accounts" string: "APIKEY1;APIKEY2" or "Channel1:APIKEY1;Channel2:APIKEY2"
 * API key: https://tipeeestream.com/dashboard/stream
 */
public class TipeeeStreamIntegrationManager {

    private static final String TIPEEE_WSS_BASE = "wss://sso-cf.tipeeestream.com:443/socket.io/?EIO=3&transport=websocket&access_token=";

    private final JavaPlugin plugin;
    private final OkHttpClient httpClient;
    private final List<TipeeeConnection> connections = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private boolean enabled = false;
    private double amountPerTrigger = 5.0;
    private boolean debug = false;
    private String lastAccounts = "";

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

    public void start(boolean enabled, String accounts, double amountPerTrigger, boolean debug) {
        this.enabled = enabled;
        this.amountPerTrigger = amountPerTrigger;
        this.debug = debug;
        this.lastAccounts = accounts == null ? "" : accounts;

        if (!enabled) {
            if (debug) plugin.getLogger().info("[Tipeee] Tipeeestream deaktiviert (donations.yml).");
            return;
        }

        List<AccountEntry> parsed = parseAccounts(accounts);
        if (parsed.isEmpty()) {
            plugin.getLogger().info("[Tipeee] Kein API-Key in donations.yml konfiguriert.");
            return;
        }
        for (AccountEntry acc : parsed) {
            TipeeeConnection conn = new TipeeeConnection(acc.channelName, acc.apiKey);
            connections.add(conn);
            conn.start();
        }
    }

    public void stop() {
        for (TipeeeConnection conn : connections) conn.stop();
        connections.clear();
        plugin.getLogger().info("[Tipeee] Alle Tipeeestream-Verbindungen getrennt.");
    }

    public void applyConfig(boolean enabled, String accounts, double amountPerTrigger, boolean debug) {
        this.debug = debug;
        String acc = accounts == null ? "" : accounts;
        boolean changed = this.enabled != enabled
                || !Objects.equals(this.lastAccounts, acc)
                || this.amountPerTrigger != amountPerTrigger;
        this.amountPerTrigger = amountPerTrigger;
        if (!changed) {
            if (debug) plugin.getLogger().info("[Tipeee] applyConfig: Keine relevante Änderung.");
            return;
        }
        stop();
        start(enabled, accounts, amountPerTrigger, debug);
    }

    public boolean getEnabled() { return enabled; }
    public double getAmountPerTrigger() { return amountPerTrigger; }

    // ─────────────────────────────────────────────────────────────────────────
    // Account parsing
    // accounts format: "APIKEY" or "Channel:APIKEY" or "Ch1:KEY1;Ch2:KEY2"
    // ─────────────────────────────────────────────────────────────────────────

    private List<AccountEntry> parseAccounts(String raw) {
        List<AccountEntry> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        String[] entries = raw.split(";");
        for (String entry : entries) {
            String e = entry.trim();
            if (e.isBlank() || e.equals("YOUR_TIPEEESTREAM_APIKEY")) continue;
            // Format: "Channel:APIKEY"  or just "APIKEY"
            // Aber APIKEY selbst kann auch Sonderzeichen enthalten, daher nur am ersten ":" splitten
            int colonIdx = e.indexOf(':');
            String channelName, apiKey;
            if (colonIdx > 0 && colonIdx < e.length() - 1) {
                channelName = e.substring(0, colonIdx).trim();
                apiKey = e.substring(colonIdx + 1).trim();
            } else {
                // Kein Channel-Name angegeben – verwende generischen Namen
                channelName = "Tipeee#" + (result.size() + 1);
                apiKey = e;
            }
            if (!apiKey.isBlank()) result.add(new AccountEntry(channelName, apiKey));
        }
        return result;
    }

    private record AccountEntry(String channelName, String apiKey) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isTimerRunning() {
        try {
            if (plugin instanceof TwitchRandomizer t) {
                TimerManager tm = t.getTimerManager();
                return tm != null && tm.isRunning() && !(t.getPauseService() != null && t.getPauseService().isPaused());
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
    // TipeeeConnection (eine Verbindung pro Account)
    // ─────────────────────────────────────────────────────────────────────────

    private class TipeeeConnection {
        private final String channelName;
        private final String apiKey;
        private volatile WebSocket wsSocket;
        private volatile boolean shouldReconnect = false;
        private int reconnectAttempts = 0;
        private ScheduledFuture<?> reconnectTask;
        private ScheduledFuture<?> pingTask;

        TipeeeConnection(String channelName, String apiKey) {
            this.channelName = channelName;
            this.apiKey = apiKey;
        }

        void start() { shouldReconnect = true; connect(); }

        void stop() {
            shouldReconnect = false;
            cancelReconnect();
            cancelPing();
            WebSocket ws = wsSocket;
            wsSocket = null;
            if (ws != null) try { ws.close(1000, "Plugin stopping"); } catch (Throwable ignored) {}
        }

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
            plugin.getLogger().info("[Tipeee:" + channelName + "] Reconnect in " + delay + "s (Versuch " + reconnectAttempts + ")...");
            reconnectTask = scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        }

        private void cancelReconnect() {
            if (reconnectTask != null && !reconnectTask.isDone()) { reconnectTask.cancel(false); reconnectTask = null; }
        }

        private void cancelPing() {
            if (pingTask != null && !pingTask.isDone()) { pingTask.cancel(false); pingTask = null; }
        }

        private void handleMessage(String raw, WebSocket ws) {
            if (debug) plugin.getLogger().info("[Tipeee:" + channelName + "] <<< " + raw);

            if (raw.startsWith("0")) {
                String auth = "42[\"join-room\",{\"room\":\"" + apiKey + "\",\"username\":\"twitchrandomizer\"}]";
                ws.send(auth);
                if (debug) plugin.getLogger().info("[Tipeee:" + channelName + "] Auth gesendet.");
                cancelPing();
                pingTask = scheduler.scheduleAtFixedRate(() -> { if (wsSocket != null) wsSocket.send("2"); }, 25, 25, TimeUnit.SECONDS);
                return;
            }
            if (raw.equals("2")) { ws.send("3"); return; }
            if (!raw.startsWith("42")) return;

            String content = raw.substring(2);

            if (content.contains("\"authenticated\"")) {
                plugin.getLogger().info("[Tipeee:" + channelName + "] Erfolgreich authentifiziert!");
                reconnectAttempts = 0;
                return;
            }
            if (content.contains("\"unauthorized\"")) {
                plugin.getLogger().severe("[Tipeee:" + channelName + "] Auth fehlgeschlagen! API-Key prüfen.");
                shouldReconnect = false;
                return;
            }

            // Donation/Tip event
            if (!content.contains("\"donation\"") && !content.contains("\"tip\"")) return;
            if (content.contains("\"event:update\"")) return;

            String username = extractJsonString(content, "username");
            if (username == null || username.isBlank()) username = "TipeeeStreamTip";

            // Channel-Tag nur bei mehreren Connections
            String taggedUsername;
            if (connections.size() > 1) {
                taggedUsername = "role:donation:" + username + " \u00a77(" + channelName + ")\u00a7r";
            } else {
                taggedUsername = "role:donation:" + username;
            }

            double amount = extractJsonDouble(content, "amount");

            if (debug) plugin.getLogger().info("[Tipeee:" + channelName + "] Tip von " + username + ": " + amount + " (min=" + amountPerTrigger + ")");
            if (amount < amountPerTrigger) {
                if (debug) plugin.getLogger().info("[Tipeee:" + channelName + "] Zu gering, ignoriert.");
                return;
            }
            if (!isTimerRunning()) {
                if (debug) plugin.getLogger().info("[Tipeee:" + channelName + "] Timer läuft nicht, ignoriert.");
                return;
            }

            int count = (int) Math.ceil(amount / amountPerTrigger);
            TwitchIntegrationManager twitch = getTwitch();
            if (twitch != null) {
                twitch.enqueueMultiple(count, taggedUsername);
                plugin.getLogger().info("[Tipeee:" + channelName + "] Tip: " + username + " -> " + amount + " -> +" + count + " Event(s) in Queue.");
            }
        }

        private class TipeeeWebSocketListener extends WebSocketListener {
            @Override public void onOpen(WebSocket ws, Response r) {
                wsSocket = ws;
                plugin.getLogger().info("[Tipeee:" + channelName + "] WebSocket verbunden.");
            }
            @Override public void onMessage(WebSocket ws, String text) { handleMessage(text, ws); }
            @Override public void onMessage(WebSocket ws, ByteString bytes) { handleMessage(bytes.utf8(), ws); }
            @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(1000, null); }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (wsSocket != ws && wsSocket != null) return;
                wsSocket = null; cancelPing();
                if (code == 1000) return;
                plugin.getLogger().warning("[Tipeee:" + channelName + "] Getrennt (Code " + code + "): " + reason);
                scheduleReconnect();
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                if (wsSocket != ws && wsSocket != null) return;
                wsSocket = null; cancelPing();
                plugin.getLogger().warning("[Tipeee:" + channelName + "] Fehler: " + t.getMessage());
                scheduleReconnect();
            }
        }
    }
}