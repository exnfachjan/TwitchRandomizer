package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import okhttp3.*;
import okio.ByteString;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * StreamElements WebSocket Integration.
 *
 * JWT-Tokens werden aus "streamelements.yml" im Plugin-Ordner gelesen –
 * diese Datei wird NIE von Bukkit's saveConfig() überschrieben.
 *
 * Format der streamelements.yml:
 *   enabled: true
 *   accounts: "exnfachjan:JWT1;freund:JWT2"
 *   amount_per_trigger: 5.0
 */
public class StreamElementsIntegrationManager {

    private static final String SE_WSS = "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket";
    private static final String SE_FILE = "streamelements.yml";

    private final JavaPlugin plugin;
    private final File seFile;
    private final OkHttpClient httpClient;
    private final List<SEConnection> connections = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Gecachte Config-Werte
    private boolean enabled = false;
    private boolean tipsEnabled = true;
    private double amountPerTrigger = 5.0;
    private boolean debug = false;
    private List<String> lastTokens = new ArrayList<>();

    public StreamElementsIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.seFile = new File(plugin.getDataFolder(), SE_FILE);
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(0, TimeUnit.SECONDS)
                .build();
        ensureFileExists();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void start() {
        SEConfig cfg = loadSEFile();
        this.enabled = cfg.enabled;
        this.tipsEnabled = cfg.tipsEnabled;
        this.amountPerTrigger = cfg.amountPerTrigger;
        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);

        if (!enabled) {
            if (debug) plugin.getLogger().info("[SE] StreamElements deaktiviert (streamelements.yml).");
            return;
        }
        List<AccountEntry> accounts = cfg.accounts;
        if (accounts.isEmpty()) {
            plugin.getLogger().info("[SE] Keine JWT-Tokens in streamelements.yml konfiguriert.");
            return;
        }
        for (AccountEntry acc : accounts) {
            SEConnection conn = new SEConnection(acc.channel, acc.jwtToken);
            connections.add(conn);
            conn.start();
        }
        lastTokens = accounts.stream().map(a -> a.jwtToken).toList();
    }

    public void stop() {
        for (SEConnection conn : connections) conn.stop();
        connections.clear();
        plugin.getLogger().info("[SE] Alle StreamElements-Verbindungen getrennt.");
    }

    public void applyConfig() {
        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);
        SEConfig cfg = loadSEFile();
        boolean prevEnabled = this.enabled;
        List<String> newTokens = cfg.accounts.stream().map(a -> a.jwtToken).toList();

        boolean enabledChanged = prevEnabled != cfg.enabled;
        boolean tokensChanged = !lastTokens.equals(newTokens);

        if (!enabledChanged && !tokensChanged) {
            if (debug) plugin.getLogger().info("[SE] applyConfig: Keine relevante Änderung, skip.");
            return;
        }

        if (debug) plugin.getLogger().info("[SE] applyConfig: Änderung erkannt, neu verbinden...");

        for (SEConnection conn : connections) conn.stop();
        connections.clear();

        this.enabled = cfg.enabled;
        this.tipsEnabled = cfg.tipsEnabled;
        this.amountPerTrigger = cfg.amountPerTrigger;

        if (!enabled || cfg.accounts.isEmpty()) {
            lastTokens = new ArrayList<>();
            return;
        }

        final List<AccountEntry> toConnect = cfg.accounts;
        scheduler.schedule(() -> {
            if (!connections.isEmpty()) return;
            for (AccountEntry acc : toConnect) {
                SEConnection conn = new SEConnection(acc.channel, acc.jwtToken);
                connections.add(conn);
                conn.start();
            }
            lastTokens = newTokens;
        }, 500, TimeUnit.MILLISECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // streamelements.yml direkt lesen (niemals Bukkit saveConfig verwenden)
    // ─────────────────────────────────────────────────────────────────────────

    private void ensureFileExists() {
        if (seFile.exists()) return;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(seFile), StandardCharsets.UTF_8))) {
            pw.println("# StreamElements Configuration");
            pw.println("# This file is NOT managed by the plugin automatically.");
            pw.println("# Edit it manually and run /trconfig se reload ingame.");
            pw.println("#");
            pw.println("# JWT Token: StreamElements Dashboard -> My Account -> Channels -> JWT Token");
            pw.println("# https://streamelements.com/dashboard/account/channels");
            pw.println("#");
            pw.println("# For multiple streamers use semicolons:");
            pw.println("# accounts: \"Channel1:JWT1;Channel2:JWT2\"");
            pw.println();
            pw.println("enabled: false");
            pw.println("accounts: \"YOUR_CHANNEL:YOUR_JWT_TOKEN\"");
            pw.println("amount_per_trigger: 5.0");
            pw.println("tips_enabled: true");
            plugin.getLogger().info("[SE] streamelements.yml erstellt. Bitte JWT-Token eintragen.");
        } catch (Exception e) {
            plugin.getLogger().warning("[SE] Konnte streamelements.yml nicht erstellen: " + e.getMessage());
        }
    }

    private SEConfig loadSEFile() {
        SEConfig result = new SEConfig();
        if (!seFile.exists()) { ensureFileExists(); return result; }

        try {
            List<String> lines = Files.readAllLines(seFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || line.isBlank()) continue;

                if (line.startsWith("enabled:")) {
                    result.enabled = parseBool(line.substring("enabled:".length()));
                } else if (line.startsWith("tips_enabled:")) {
                    result.tipsEnabled = parseBool(line.substring("tips_enabled:".length()));
                } else if (line.startsWith("amount_per_trigger:")) {
                    result.amountPerTrigger = parseDouble(line.substring("amount_per_trigger:".length()), 5.0);
                } else if (line.startsWith("accounts:")) {
                    String raw = line.substring("accounts:".length()).trim();
                    // Anführungszeichen entfernen
                    if (raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
                    else if (raw.startsWith("'") && raw.endsWith("'")) raw = raw.substring(1, raw.length() - 1);
                    result.accounts = parseAccounts(raw);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SE] Fehler beim Lesen von streamelements.yml: " + e.getMessage());
        }
        return result;
    }

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

    private boolean parseBool(String s) {
        return "true".equalsIgnoreCase(s.trim());
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); }
        catch (Exception e) { return def; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hilfsklassen
    // ─────────────────────────────────────────────────────────────────────────

    private static class SEConfig {
        boolean enabled = false;
        boolean tipsEnabled = true;
        double amountPerTrigger = 5.0;
        List<AccountEntry> accounts = new ArrayList<>();
    }

    private record AccountEntry(String channel, String jwtToken) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Timer-Check
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
    // SEConnection
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
            if (content.contains("\"authenticated\"")) { plugin.getLogger().info("[SE:" + channelName + "] Erfolgreich authentifiziert!"); reconnectAttempts = 0; return; }
            if (content.contains("\"unauthorized\"")) { plugin.getLogger().severe("[SE:" + channelName + "] Auth fehlgeschlagen! JWT-Token prüfen."); shouldReconnect = false; return; }
            if (!tipsEnabled) return;
            if (!content.contains("\"tip\"") && !content.contains("\"donation\"")) return;
            if (content.contains("\"event:update\"")) return;

            String username = extractJsonString(content, "username");
            if (username == null || username.isBlank()) username = "StreamElementsTip";
            String taggedUsername = "role:donation:" + username;
            double amount = extractJsonDouble(content, "amount");

            if (debug) plugin.getLogger().info("[SE:" + channelName + "] Tip von " + username + ": " + amount + " (min=" + amountPerTrigger + ")");
            if (amount < amountPerTrigger) { if (debug) plugin.getLogger().info("[SE:" + channelName + "] Zu gering, ignoriert."); return; }
            if (!isTimerRunning()) { if (debug) plugin.getLogger().info("[SE:" + channelName + "] Timer läuft nicht, ignoriert."); return; }

            int count = (int) (amount / amountPerTrigger);
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