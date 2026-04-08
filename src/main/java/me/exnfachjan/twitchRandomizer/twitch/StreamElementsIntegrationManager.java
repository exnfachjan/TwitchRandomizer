package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import okhttp3.*;
import okio.ByteString;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

/**
 * StreamElements WebSocket Integration (via OkHttp – bereits im Classpath durch Twitch4J).
 * Unterstützt mehrere JWT-Tokens (einen pro Streamer/Channel).
 *
 * Config-Format:
 *   streamelements:
 *     enabled: true
 *     accounts:
 *       - channel: exnfachjan
 *         jwt_token: "eyJ..."
 *       - channel: freund
 *         jwt_token: "eyJ..."
 *     triggers:
 *       tips:
 *         enabled: true
 *         amount_per_trigger: 5.0
 *
 * Rückwärtskompatibel: Einzelner jwt_token auf oberster Ebene wird weiterhin unterstützt.
 */
public class StreamElementsIntegrationManager {

    private static final String SE_WSS = "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket";

    private final JavaPlugin plugin;
    private final OkHttpClient httpClient;

    // Eine SEConnection pro Account
    private final List<SEConnection> connections = new CopyOnWriteArrayList<>();

    // Config
    private boolean enabled;
    private boolean tipsEnabled;
    private double amountPerTrigger;
    private boolean debug;

    // Änderungs-Tracking für applyConfig
    private boolean lastEnabled = false;
    private List<String> lastTokens = new ArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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

    public void start() {
        readConfig();
        if (!enabled) {
            if (debug) plugin.getLogger().info("[SE] StreamElements deaktiviert.");
            return;
        }
        List<AccountEntry> accounts = readAccounts();
        if (accounts.isEmpty()) {
            plugin.getLogger().info("[SE] Keine JWT-Tokens konfiguriert.");
            return;
        }
        for (AccountEntry acc : accounts) {
            SEConnection conn = new SEConnection(acc.channel, acc.jwtToken);
            connections.add(conn);
            conn.start();
        }
        lastEnabled = true;
        lastTokens = accounts.stream().map(a -> a.jwtToken).toList();
    }

    public void stop() {
        for (SEConnection conn : connections) conn.stop();
        connections.clear();
        plugin.getLogger().info("[SE] Alle StreamElements-Verbindungen getrennt.");
    }

    public void applyConfig() {
        boolean prevEnabled = this.enabled;
        List<String> prevTokens = new ArrayList<>(this.lastTokens);
        readConfig();
        List<AccountEntry> accounts = readAccounts();
        List<String> newTokens = accounts.stream().map(a -> a.jwtToken).toList();

        boolean enabledChanged = prevEnabled != this.enabled;
        boolean tokensChanged = !prevTokens.equals(newTokens);

        if (!enabledChanged && !tokensChanged) {
            if (debug) plugin.getLogger().info("[SE] applyConfig: Keine relevante Änderung, skip.");
            return;
        }

        if (debug) plugin.getLogger().info("[SE] applyConfig: Änderung erkannt, neu verbinden...");

        // Alle alten Verbindungen schließen
        for (SEConnection conn : connections) conn.stop();
        connections.clear();

        if (!enabled || accounts.isEmpty()) {
            if (debug) plugin.getLogger().info("[SE] SE deaktiviert oder keine Tokens.");
            lastEnabled = false;
            lastTokens = new ArrayList<>();
            return;
        }

        // Kurz warten, dann neu verbinden
        scheduler.schedule(() -> {
            for (AccountEntry acc : accounts) {
                SEConnection conn = new SEConnection(acc.channel, acc.jwtToken);
                connections.add(conn);
                conn.start();
            }
            lastEnabled = true;
            lastTokens = newTokens;
        }, 500, TimeUnit.MILLISECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Config lesen
    // ─────────────────────────────────────────────────────────────────────────

    private void readConfig() {
        this.enabled = plugin.getConfig().getBoolean("streamelements.enabled", false);
        this.tipsEnabled = plugin.getConfig().getBoolean("streamelements.triggers.tips.enabled", true);
        this.amountPerTrigger = plugin.getConfig().getDouble("streamelements.triggers.tips.amount_per_trigger", 5.0);
        if (this.amountPerTrigger <= 0) this.amountPerTrigger = 5.0;
        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);
    }

    private List<AccountEntry> readAccounts() {
        List<AccountEntry> result = new ArrayList<>();

        // Neues Format: streamelements.accounts Liste
        List<Map<?, ?>> accountList = plugin.getConfig().getMapList("streamelements.accounts");
        if (accountList != null && !accountList.isEmpty()) {
            for (Map<?, ?> entry : accountList) {
                Object channelObj = entry.get("channel");
                Object tokenObj = entry.get("jwt_token");
                String channel = (channelObj != null) ? String.valueOf(channelObj) : "unknown";
                String token = (tokenObj != null) ? String.valueOf(tokenObj) : "";
                if (token != null && !token.isBlank() && !token.equals("null")) {
                    result.add(new AccountEntry(channel, token.trim()));
                }
            }
        }

        // Rückwärtskompatibilität: einzelner jwt_token auf oberster Ebene
        if (result.isEmpty()) {
            String single = plugin.getConfig().getString("streamelements.jwt_token", "");
            if (single != null && !single.isBlank()) {
                result.add(new AccountEntry("default", single.trim()));
            }
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hilfsmethoden
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
            if (strVal != null) {
                try { return Double.parseDouble(strVal); } catch (Exception ignored) {}
            }
            return 0.0;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        try { return Double.parseDouble(json.substring(start, end)); } catch (Exception e) { return 0.0; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account-Datenhaltung
    // ─────────────────────────────────────────────────────────────────────────

    private record AccountEntry(String channel, String jwtToken) {}

    // ─────────────────────────────────────────────────────────────────────────
    // SEConnection – eine WebSocket-Verbindung pro Account
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

        void start() {
            shouldReconnect = true;
            connect();
        }

        void stop() {
            shouldReconnect = false;
            cancelReconnect();
            cancelPing();
            WebSocket ws = wsSocket;
            wsSocket = null;
            if (ws != null) {
                try { ws.close(1000, "Plugin stopping"); } catch (Throwable ignored) {}
            }
        }

        private void connect() {
            Request request = new Request.Builder().url(SE_WSS).build();
            httpClient.newWebSocket(request, new SEWebSocketListener());
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
            if (reconnectTask != null && !reconnectTask.isDone()) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
        }

        private void cancelPing() {
            if (pingTask != null && !pingTask.isDone()) {
                pingTask.cancel(false);
                pingTask = null;
            }
        }

        private void handleMessage(String raw, WebSocket ws) {
            if (debug) plugin.getLogger().info("[SE:" + channelName + "] <<< " + raw);

            if (raw.startsWith("0")) {
                String authMsg = "42[\"authenticate\",{\"method\":\"jwt\",\"token\":\"" + jwtToken + "\"}]";
                ws.send(authMsg);
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Auth gesendet.");
                cancelPing();
                pingTask = scheduler.scheduleAtFixedRate(() -> {
                    if (wsSocket != null) wsSocket.send("2");
                }, 25, 25, TimeUnit.SECONDS);
                return;
            }

            if (raw.equals("2")) {
                ws.send("3");
                return;
            }

            if (!raw.startsWith("42")) return;
            String content = raw.substring(2);

            if (content.contains("\"authenticated\"")) {
                plugin.getLogger().info("[SE:" + channelName + "] Erfolgreich authentifiziert!");
                reconnectAttempts = 0;
                return;
            }

            if (content.contains("\"unauthorized\"")) {
                plugin.getLogger().severe("[SE:" + channelName + "] Authentifizierung fehlgeschlagen! JWT-Token prüfen.");
                shouldReconnect = false;
                return;
            }

            if (!tipsEnabled) return;
            if (!content.contains("\"tip\"") && !content.contains("\"donation\"")) return;
            // event:update ignorieren (nur das eigentliche "event" verarbeiten)
            if (content.contains("\"event:update\"")) return;

            String username = extractJsonString(content, "username");
            if (username == null || username.isBlank()) username = "StreamElementsTip";
            // Blau markieren (donation-Rolle)
            String taggedUsername = "role:donation:" + username;

            double amount = extractJsonDouble(content, "amount");

            if (debug) plugin.getLogger().info("[SE:" + channelName + "] Tip von " + username + ": " + amount
                    + " (amountPerTrigger=" + amountPerTrigger + ")");

            if (amount < amountPerTrigger) {
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Tip zu gering, ignoriert.");
                return;
            }

            if (!isTimerRunning()) {
                if (debug) plugin.getLogger().info("[SE:" + channelName + "] Timer läuft nicht, Tip ignoriert.");
                return;
            }

            int count = (int) (amount / amountPerTrigger);
            TwitchIntegrationManager twitch = getTwitch();
            if (twitch != null) {
                twitch.enqueueMultiple(count, taggedUsername);
                plugin.getLogger().info("[SE:" + channelName + "] Tip: " + username + " -> " + amount
                        + " -> +" + count + " Event(s) in Queue.");
            }
        }

        private class SEWebSocketListener extends WebSocketListener {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                wsSocket = webSocket;
                plugin.getLogger().info("[SE:" + channelName + "] WebSocket verbunden.");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text, webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleMessage(bytes.utf8(), webSocket);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (wsSocket != webSocket && wsSocket != null) return;
                wsSocket = null;
                cancelPing();
                if (code == 1000) return; // Sauberes Schließen von uns
                plugin.getLogger().warning("[SE:" + channelName + "] Verbindung getrennt (Code " + code + "): " + reason);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (wsSocket != webSocket && wsSocket != null) return;
                wsSocket = null;
                cancelPing();
                plugin.getLogger().warning("[SE:" + channelName + "] WebSocket-Fehler: " + t.getMessage());
                scheduleReconnect();
            }
        }
    }
}