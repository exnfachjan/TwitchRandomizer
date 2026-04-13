package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import okhttp3.*;
import okio.ByteString;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

/**
 * StreamElements donation integration via WebSocket.
 * Config is supplied by DonationsManager (from donations.yml).
 */
public class StreamElementsIntegrationManager {

    private static final String SE_WSS = "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket";

    private final JavaPlugin plugin;
    private final OkHttpClient httpClient;
    private final List<SEConnection> connections = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private boolean enabled = false;
    private double amountPerTrigger = 5.0;
    private boolean debug = false;
    private List<String> lastTokens = new ArrayList<>();
    private String lastAccounts = "";

    public StreamElementsIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(0, TimeUnit.SECONDS)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void start(boolean enabled, String accounts, double amountPerTrigger, boolean debug) {
        this.enabled = enabled;
        this.amountPerTrigger = amountPerTrigger;
        this.debug = debug;
        this.lastAccounts = accounts == null ? "" : accounts;

        if (!enabled) {
            if (debug) plugin.getLogger().info("[SE] StreamElements deaktiviert (donations.yml).");
            return;
        }
        List<AccountEntry> parsed = parseAccounts(accounts);
        if (parsed.isEmpty()) {
            plugin.getLogger().info("[SE] Keine JWT-Tokens in donations.yml konfiguriert.");
            return;
        }
        for (AccountEntry acc : parsed) {
            SEConnection conn = new SEConnection(acc.channel, acc.jwtToken);
            connections.add(conn);
            conn.start();
        }
        lastTokens = parsed.stream().map(a -> a.jwtToken).toList();
    }

    public void stop() {
        for (SEConnection conn : connections) conn.stop();
        connections.clear();
        plugin.getLogger().info("[SE] Alle StreamElements-Verbindungen getrennt.");
    }

    public void applyConfig(boolean enabled, String accounts, double amountPerTrigger, boolean debug) {
        this.debug = debug;
        String acc = accounts == null ? "" : accounts;

        boolean enabledChanged = this.enabled != enabled;
        boolean accountsChanged = !Objects.equals(this.lastAccounts, acc);
        boolean amountChanged = this.amountPerTrigger != amountPerTrigger;

        // Amount only change: just update value, no reconnect needed
        if (!enabledChanged && !accountsChanged) {
            this.amountPerTrigger = amountPerTrigger;
            if (debug && amountChanged) plugin.getLogger().info("[SE] amountPerTrigger aktualisiert: " + amountPerTrigger);
            return;
        }

        if (debug) plugin.getLogger().info("[SE] applyConfig: Änderung erkannt, neu verbinden...");
        stop();
        start(enabled, accounts, amountPerTrigger, debug);
    }

    public boolean getEnabled() { return enabled; }
    public double getAmountPerTrigger() { return amountPerTrigger; }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<AccountEntry> parseAccounts(String raw) {
        List<AccountEntry> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : raw.split(";")) {
            entry = entry.trim();
            if (entry.isBlank()) continue;
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon >= entry.length() - 1) {
                plugin.getLogger().warning("[SE] Ungültiger accounts-Eintrag (Format: Channel:JWT): " + entry);
                continue;
            }
            String channel = entry.substring(0, colon).trim();
            String token = entry.substring(colon + 1).trim();
            if (token.isBlank() || token.equals("YOUR_JWT_TOKEN")) continue;
            if (seen.add(token)) {
                result.add(new AccountEntry(channel, token));
            } else {
                plugin.getLogger().warning("[SE] Doppelter Token für '" + channel + "' ignoriert.");
            }
        }
        return result;
    }

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

    private record AccountEntry(String channel, String jwtToken) {}

    // ─────────────────────────────────────────────────────────────────────────
    // SE Connection
    // ─────────────────────────────────────────────────────────────────────────

    private class SEConnection {
        private final String channelName;
        private final String jwtToken;
        private volatile WebSocket wsSocket;
        private volatile boolean shouldReconnect = false;
        private int reconnectAttempts = 0;
        private ScheduledFuture<?> reconnectTask;
        private ScheduledFuture<?> pingTask;

        SEConnection(String channelName, String jwtToken) {
            this.channelName = channelName;
            this.jwtToken = jwtToken;
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
            httpClient.newWebSocket(new Request.Builder().url(SE_WSS).build(), new SEWebSocketListener());
        }

        private void scheduleReconnect() {
            if (!shouldReconnect) return;
            cancelReconnect();
            long delay = Math.min(5L * (1L << Math.min(reconnectAttempts, 3)), 60L);
            reconnectAttempts++;
            plugin.getLogger().info("[SE:" + channelName + "] Reconnect in " + delay + "s (Versuch " + reconnectAttempts + ")...");
            reconnectTask = scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
        }

        private void cancelReconnect() {
            if (reconnectTask != null && !reconnectTask.isDone()) { reconnectTask.cancel(false); reconnectTask = null; }
        }

        private void cancelPing() {
            if (pingTask != null && !pingTask.isDone()) { pingTask.cancel(false); pingTask = null; }
        }

        private void handleMessage(String raw, WebSocket ws) {
            if (debug) plugin.getLogger().info("[SE:" + channelName + "] <<< " + raw);

            if (raw.startsWith("0")) {
                ws.send("42[\"authenticate\",{\"method\":\"jwt\",\"token\":\"" + jwtToken + "\"}]");
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Auth gesendet.");
                cancelPing();
                pingTask = scheduler.scheduleAtFixedRate(() -> { if (wsSocket != null) wsSocket.send("2"); }, 25, 25, TimeUnit.SECONDS);
                return;
            }
            if (raw.equals("2")) { ws.send("3"); return; }
            if (!raw.startsWith("42")) return;

            String content = raw.substring(2);
            if (content.contains("\"authenticated\"")) {
                plugin.getLogger().info("[SE:" + channelName + "] Erfolgreich authentifiziert!");
                reconnectAttempts = 0;
                return;
            }
            if (content.contains("\"unauthorized\"")) {
                plugin.getLogger().severe("[SE:" + channelName + "] Auth fehlgeschlagen! JWT-Token prüfen.");
                shouldReconnect = false;
                return;
            }
            if (!content.contains("\"tip\"") && !content.contains("\"donation\"")) return;
            if (content.contains("\"event:update\"")) return;

            String username = extractJsonString(content, "username");
            if (username == null || username.isBlank()) username = "StreamElementsTip";
            String taggedUsername = "role:donation:" + username;
            double amount = extractJsonDouble(content, "amount");

            if (debug) plugin.getLogger().info("[SE:" + channelName + "] Tip von " + username + ": " + amount + " (min=" + amountPerTrigger + ")");
            if (amount < amountPerTrigger) {
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Zu gering, ignoriert.");
                return;
            }
            if (!isTimerRunning()) {
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Timer läuft nicht, ignoriert.");
                return;
            }

            int count = (int) Math.ceil(amount / amountPerTrigger);
            TwitchIntegrationManager twitch = getTwitch();
            if (twitch != null) {
                twitch.enqueueMultiple(count, taggedUsername);
                plugin.getLogger().info("[SE:" + channelName + "] Tip: " + username + " -> " + amount + " -> +" + count + " Event(s) in Queue.");
            }
        }

        private class SEWebSocketListener extends WebSocketListener {
            @Override public void onOpen(WebSocket ws, Response r) { wsSocket = ws; plugin.getLogger().info("[SE:" + channelName + "] WebSocket verbunden."); }
            @Override public void onMessage(WebSocket ws, String text) { handleMessage(text, ws); }
            @Override public void onMessage(WebSocket ws, ByteString bytes) { handleMessage(bytes.utf8(), ws); }
            @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(1000, null); }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (wsSocket != ws && wsSocket != null) return;
                wsSocket = null; cancelPing();
                if (code == 1000) return;
                plugin.getLogger().warning("[SE:" + channelName + "] Getrennt (Code " + code + "): " + reason);
                scheduleReconnect();
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                if (wsSocket != ws && wsSocket != null) return;
                wsSocket = null; cancelPing();
                plugin.getLogger().warning("[SE:" + channelName + "] Fehler: " + t.getMessage());
                scheduleReconnect();
            }
        }
    }
}