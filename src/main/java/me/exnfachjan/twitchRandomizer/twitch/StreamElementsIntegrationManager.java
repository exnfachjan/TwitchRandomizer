package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import okhttp3.*;
import okio.ByteString;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * StreamElements WebSocket Integration (via OkHttp – bereits im Classpath durch Twitch4J).
 * Verbindet sich mit wss://realtime.streamelements.com und lauscht auf Tip-Events.
 * Benötigt einen StreamElements JWT-Token (Dashboard → Mein Konto → Channels → JWT Token).
 */
public class StreamElementsIntegrationManager {

    private static final String SE_WSS = "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket";

    private final JavaPlugin plugin;
    // OkHttpClient wird einmal erstellt und nie neu erstellt (Singleton pro Instanz)
    private final OkHttpClient httpClient;
    private volatile WebSocket wsSocket;

    // Config
    private boolean enabled;
    private String jwtToken;
    private boolean tipsEnabled;
    private double amountPerTrigger;
    private boolean debug;

    // Reconnect
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> reconnectTask;
    private volatile boolean shouldReconnect = false;
    private int reconnectAttempts = 0;

    // Heartbeat
    private ScheduledFuture<?> pingTask;

    public StreamElementsIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // Einmalig erstellen, nie wieder neu — verhindert doppelte Verbindungen
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(0, TimeUnit.SECONDS) // OkHttp-eigenes Ping deaktivieren, wir machen es selbst
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void start() {
        readConfig();
        if (!enabled || jwtToken == null || jwtToken.isBlank()) {
            if (debug) plugin.getLogger().info("[SE] StreamElements deaktiviert oder kein JWT-Token.");
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
        if (ws != null) {
            try { ws.close(1000, "Plugin stopping"); } catch (Throwable ignored) {}
        }
        plugin.getLogger().info("[SE] StreamElements-Verbindung getrennt.");
    }

    public void applyConfig() {
        readConfig();

        // Bestehende Verbindung sauber schließen
        shouldReconnect = false;
        cancelReconnect();
        cancelPing();
        WebSocket ws = wsSocket;
        wsSocket = null;
        if (ws != null) {
            try { ws.close(1000, "Config reload"); } catch (Throwable ignored) {}
        }

        if (!enabled) {
            if (debug) plugin.getLogger().info("[SE] SE-Integration deaktiviert.");
            return;
        }

        // Kurz warten damit der alte Socket Zeit hat sich zu schließen
        scheduler.schedule(() -> {
            shouldReconnect = true;
            reconnectAttempts = 0;
            connect();
        }, 500, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // Intern
    // -------------------------------------------------------------------------

    private void readConfig() {
        this.enabled = plugin.getConfig().getBoolean("streamelements.enabled", false);
        this.jwtToken = plugin.getConfig().getString("streamelements.jwt_token", "").trim();
        this.tipsEnabled = plugin.getConfig().getBoolean("streamelements.triggers.tips.enabled", true);
        this.amountPerTrigger = plugin.getConfig().getDouble("streamelements.triggers.tips.amount_per_trigger", 5.0);
        if (this.amountPerTrigger <= 0) this.amountPerTrigger = 5.0;
        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);
    }

    private void connect() {
        if (httpClient == null) return;
        Request request = new Request.Builder().url(SE_WSS).build();
        httpClient.newWebSocket(request, new SEWebSocketListener());
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        cancelReconnect();
        long delay = Math.min(5L * (1L << Math.min(reconnectAttempts, 3)), 60L);
        reconnectAttempts++;
        plugin.getLogger().info("[SE] Reconnect in " + delay + "s (Versuch " + reconnectAttempts + ")...");
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

    /**
     * Parst eingehende Socket.IO-Nachrichten.
     * Typen: 0=connect-info, 2=ping, 42=event-array
     */
    private void handleMessage(String raw, WebSocket ws) {
        if (debug) plugin.getLogger().info("[SE] <<< " + raw);

        // Socket.IO Handshake (Typ 0)
        if (raw.startsWith("0")) {
            String authMsg = "42[\"authenticate\",{\"method\":\"jwt\",\"token\":\"" + jwtToken + "\"}]";
            ws.send(authMsg);
            if (debug) plugin.getLogger().info("[SE] Auth gesendet.");
            // Ping-Task starten
            cancelPing();
            pingTask = scheduler.scheduleAtFixedRate(() -> {
                if (wsSocket != null) wsSocket.send("2");
            }, 25, 25, TimeUnit.SECONDS);
            return;
        }

        // Ping → Pong
        if (raw.equals("2")) {
            ws.send("3");
            return;
        }

        // Socket.IO Event: 42["name", {...}]
        if (!raw.startsWith("42")) return;
        String content = raw.substring(2);

        if (content.contains("\"authenticated\"")) {
            plugin.getLogger().info("[SE] Erfolgreich authentifiziert!");
            reconnectAttempts = 0;
            return;
        }

        if (content.contains("\"unauthorized\"")) {
            plugin.getLogger().severe("[SE] Authentifizierung fehlgeschlagen! JWT-Token prüfen.");
            shouldReconnect = false;
            return;
        }

        if (!tipsEnabled) return;
        if (!content.contains("\"tip\"") && !content.contains("\"donation\"")) return;

        String username = extractJsonString(content, "username");
        if (username == null || username.isBlank()) username = "StreamElementsTip";

        double amount = extractJsonDouble(content, "amount");

        if (debug) plugin.getLogger().info("[SE] Tip von " + username + ": " + amount
                + " (amountPerTrigger=" + amountPerTrigger + ")");

        if (amount < amountPerTrigger) {
            if (debug) plugin.getLogger().info("[SE] Tip zu gering, ignoriert.");
            return;
        }

        if (!isTimerRunning()) {
            if (debug) plugin.getLogger().info("[SE] Timer läuft nicht, Tip ignoriert.");
            return;
        }

        int count = (int) (amount / amountPerTrigger);
        TwitchIntegrationManager twitch = getTwitch();
        if (twitch != null) {
            twitch.enqueueMultiple(count, username);
            plugin.getLogger().info("[SE] Tip: " + username + " -> " + amount
                    + " -> +" + count + " Event(s) in Queue.");
        }
    }

    // -------------------------------------------------------------------------
    // Einfaches JSON-Parsing (keine externe Lib nötig)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // OkHttp WebSocket Listener
    // -------------------------------------------------------------------------

    private class SEWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            wsSocket = webSocket;
            plugin.getLogger().info("[SE] WebSocket verbunden.");
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
            // Nur reagieren wenn dies noch die aktive Verbindung ist
            if (wsSocket != webSocket && wsSocket != null) return;
            wsSocket = null;
            cancelPing();
            if (code == 1000) {
                // Sauberes Schließen (von uns initiiert) – kein Reconnect nötig
                return;
            }
            plugin.getLogger().warning("[SE] Verbindung getrennt (Code " + code + "): " + reason);
            scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (wsSocket != webSocket && wsSocket != null) return;
            wsSocket = null;
            cancelPing();
            plugin.getLogger().warning("[SE] WebSocket-Fehler: " + t.getMessage());
            scheduleReconnect();
        }
    }
}