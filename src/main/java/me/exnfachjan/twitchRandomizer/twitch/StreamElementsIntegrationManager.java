package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.websocket.*;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * StreamElements WebSocket Integration.
 * Verbindet sich mit wss://realtime.streamelements.com und lauscht auf Tip-Events.
 * Benötigt einen StreamElements JWT-Token (Account → Settings → Tokens).
 */
public class StreamElementsIntegrationManager {

    private static final String SE_WSS = "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket";

    private final JavaPlugin plugin;
    private Session wsSession;

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

    // Heartbeat (Socket.IO ping/pong)
    private ScheduledFuture<?> pingTask;

    public StreamElementsIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
        closeSession();
        scheduler.shutdownNow();
        plugin.getLogger().info("[SE] StreamElements-Verbindung getrennt.");
    }

    public void applyConfig() {
        stop();
        // Neuen Scheduler-Pool wurde schon shutdownNow'd — wir müssen neu starten,
        // also direkt reconnect via start()
        start();
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
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(new SEEndpoint(), URI.create(SE_WSS));
            // Session wird in onOpen gesetzt
        } catch (Exception e) {
            plugin.getLogger().warning("[SE] Verbindung fehlgeschlagen: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        cancelReconnect();
        // Exponential Backoff: 5s, 10s, 20s, 40s, max 60s
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

    private void closeSession() {
        if (wsSession != null && wsSession.isOpen()) {
            try { wsSession.close(); } catch (Exception ignored) {}
        }
        wsSession = null;
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
     * Parst eingehende Socket.IO / StreamElements JSON-Nachrichten.
     * Socket.IO Nachrichten haben ein numerisches Präfix:
     *   0  = connect-info
     *   2  = ping → wir antworten mit 3 (pong)
     *   42 = event array: ["event-name", {data}]
     */
    private void handleMessage(String raw, Session session) {
        if (debug) plugin.getLogger().info("[SE] <<< " + raw);

        // Socket.IO Handshake: Typ 0 = Verbindungsinfo
        if (raw.startsWith("0")) {
            // Authentifizieren
            String authMsg = "42[\"authenticate\",{\"method\":\"jwt\",\"token\":\"" + jwtToken + "\"}]";
            sendWs(session, authMsg);
            if (debug) plugin.getLogger().info("[SE] Auth gesendet.");
            return;
        }

        // Ping → Pong
        if (raw.equals("2")) {
            sendWs(session, "3");
            return;
        }

        // Socket.IO Event: 42["name", {...}]
        if (!raw.startsWith("42")) return;

        // Einfaches String-Parsing ohne externe JSON-Lib
        String content = raw.substring(2); // Präfix "42" entfernen

        // Event-Name extrahieren
        if (content.contains("\"authenticated\"")) {
            plugin.getLogger().info("[SE] Erfolgreich authentifiziert!");
            reconnectAttempts = 0;
            return;
        }

        if (content.contains("\"unauthorized\"")) {
            plugin.getLogger().severe("[SE] Authentifizierung fehlgeschlagen! Bitte JWT-Token prüfen.");
            shouldReconnect = false; // Kein Reconnect bei falschem Token
            return;
        }

        // Tip-Event prüfen
        if (!tipsEnabled) return;
        if (!content.contains("\"tip\"") && !content.contains("\"donation\"")) return;

        // Username extrahieren: "username":"..."
        String username = extractJsonString(content, "username");
        if (username == null || username.isBlank()) username = "StreamElementsTip";

        // Betrag extrahieren: "amount":12.5
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

    private void sendWs(Session session, String msg) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(msg);
                if (debug) plugin.getLogger().info("[SE] >>> " + msg);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SE] Senden fehlgeschlagen: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Einfaches JSON-Parsing (keine externe Lib nötig)
    // -------------------------------------------------------------------------

    /** Extrahiert "key":"value" aus einem JSON-String. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /** Extrahiert "key":12.5 aus einem JSON-String. */
    private double extractJsonDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0.0;
        int start = idx + search.length();
        // Manche Werte kommen als "amount":"5.00" (String) statt Zahl
        if (start < json.length() && json.charAt(start) == '"') {
            String strVal = extractJsonString(json, key);
            if (strVal != null) {
                try { return Double.parseDouble(strVal); } catch (Exception ignored) {}
            }
            return 0.0;
        }
        // Numerischer Wert
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        try { return Double.parseDouble(json.substring(start, end)); } catch (Exception e) { return 0.0; }
    }

    // -------------------------------------------------------------------------
    // WebSocket Endpoint (innere Klasse)
    // -------------------------------------------------------------------------

    @ClientEndpoint
    public class SEEndpoint {

        @OnOpen
        public void onOpen(Session session) {
            wsSession = session;
            plugin.getLogger().info("[SE] WebSocket verbunden.");
            // Socket.IO Ping alle 25 Sekunden senden
            cancelPing();
            pingTask = scheduler.scheduleAtFixedRate(() -> sendWs(session, "2"), 25, 25, TimeUnit.SECONDS);
        }

        @OnMessage
        public void onMessage(String message, Session session) {
            handleMessage(message, session);
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            wsSession = null;
            cancelPing();
            plugin.getLogger().warning("[SE] Verbindung getrennt: " + reason.getReasonPhrase());
            scheduleReconnect();
        }

        @OnError
        public void onError(Session session, Throwable error) {
            plugin.getLogger().warning("[SE] WebSocket-Fehler: " + error.getMessage());
        }
    }
}