package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.CheerEvent;
import com.github.twitch4j.chat.events.channel.SubscriptionEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TwitchIntegrationManager {
    private final JavaPlugin plugin;
    private TwitchClient twitchClient;

    // Aktuelle Verbindungskonfiguration
    private String currentChannel = null;
    private String currentToken = null; // immer normalisiert (ohne "oauth:")

    // Toggles (Basis-Flags)
    private boolean subsEnabled;
    private boolean chatTriggerEnabled;

    // Bits-Trigger
    private boolean bitsEnabled;
    private int bitsPerTrigger;

    // Chat-/Sim-Triggers
    private static final String TEST_TRIGGER = "!test";
    private static final String CMD_GIFT = "!gift";
    private static final String CMD_GIFTBOMB = "!giftbomb";
    private boolean simGiftEnabled;
    private boolean simGiftBombEnabled;
    private int simGiftBombDefaultCount;

    // Queue + Worker
    private final Queue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask queueWorker;

    // Verhindere doppeltes Nachladen der Queue bei Reconnect
    private boolean queueLoaded = false;

    // Cooldown (Ticks) zwischen Dispatches – aus Sekunden (Config) berechnet
    private int gapTicks = 20; // default 1.0s
    private int ticksSinceLastDispatch = 0;
    private boolean cooledAndReady = true;

    // Optionales Debug-Logging
    private boolean debug = false;

    // Persistenz
    private final File queueFile;

    public TwitchIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.queueFile = new File(plugin.getDataFolder(), "queue.txt");
    }

    public void start(String channel, String oauthToken, boolean legacyChatTriggerFlag) {
        String normToken = normalizeToken(oauthToken);
        if (channel == null || channel.isBlank() || normToken.isBlank()) {
            plugin.getLogger().warning("Twitch nicht konfiguriert (twitch.channel/oauth_token). Überspringe Verbindung.");
            return;
        }

        // === Config lesen ===
        this.subsEnabled        = plugin.getConfig().getBoolean("twitch.triggers.subscriptions.enabled", true);
        this.chatTriggerEnabled = plugin.getConfig().getBoolean("twitch.triggers.chat_test.enabled", legacyChatTriggerFlag);

        // Bits-Trigger aus Config
        this.bitsEnabled    = plugin.getConfig().getBoolean("twitch.triggers.bits.enabled", false);
        this.bitsPerTrigger = Math.max(1, plugin.getConfig().getInt("twitch.triggers.bits.bits_per_trigger", 500));

        // Intervall (Sek -> Ticks)
        String raw = plugin.getConfig().getString("twitch.trigger_interval_seconds", "1.0");
        double seconds;
        try { seconds = Double.parseDouble(raw.replace(",", ".")); }
        catch (Exception e) { seconds = 1.0; plugin.getLogger().warning("Ungültiger Wert für twitch.trigger_interval_seconds: '"+raw+"'. Fallback 1.0s."); }
        if (seconds < 0.05) seconds = 0.05;
        this.gapTicks = Math.max(1, (int) Math.round(seconds * 20.0));

        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);

        // Simulations-Trigger aus der Config
        this.simGiftEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
        this.simGiftBombEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
        this.simGiftBombDefaultCount = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

        // === Client bauen: NUR Chat (kein PubSub/Helix) ===
        OAuth2Credential chatCredential = buildCredential(normToken);
        this.twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(chatCredential)
                .build();

        // Chat beitreten
        twitchClient.getChat().joinChannel(channel);
        this.currentChannel = channel;
        this.currentToken = normToken;

        plugin.getLogger().info("Mit Twitch-Chat verbunden: #" + channel);
        plugin.getLogger().info("Trigger-Intervall: " + seconds + "s (" + gapTicks + " Ticks)");
        plugin.getLogger().info(
                "Triggers: subs=" + subsEnabled
                        + ", chat_test=" + chatTriggerEnabled
                        + ", bits=" + bitsEnabled + " (bits_per_trigger=" + bitsPerTrigger + ")"
        );
        plugin.getLogger().info("Sim-Trigger (nur bei debug=true): gift=" + (debug && simGiftEnabled)
                + ", giftbomb=" + (debug && simGiftBombEnabled) + " (default_count=" + simGiftBombDefaultCount + ")");

        // Queue laden (nur einmal pro Serverlauf)
        if (!queueLoaded) {
            loadQueue();
            queueLoaded = true;
        }

        // Subs
        twitchClient.getEventManager().onEvent(SubscriptionEvent.class, event -> {
            if (!subsEnabled) return;
            if (!isTimerRunning()) return;
            String user = resolveUserFromSubscription(event);
            enqueue("randomevent " + user);
        });

        // Bits / Cheers (anonyme Cheers ignorieren)
        twitchClient.getEventManager().onEvent(CheerEvent.class, event -> {
            if (!bitsEnabled) return;
            if (!isTimerRunning()) return;

            int bits = Math.max(0, event.getBits());
            if (bits < bitsPerTrigger) return;

            String user = resolveAuthorFromCheer(event);
            if (isLikelyAnonymousCheer(event, user)) {
                if (debug) plugin.getLogger().info("[Twitch] Cheer ignoriert (anonym) – bits=" + bits);
                return;
            }

            int count = bits / bitsPerTrigger;
            enqueueMultiple(count, user);
            if (debug) plugin.getLogger().info("[Twitch] Cheer: " + user + " -> " + bits + " bits -> +" + count + " Events (queue=" + commandQueue.size() + ")");
        });

        // Chat: !test, !gift, !giftbomb
        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            if (!isTimerRunning()) return;

            String msg = event.getMessage();
            if (msg == null) return;

            String author = resolveAuthorFromChat(event);
            String trimmed = msg.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);

            // WICHTIG: Flags live aus der Config lesen -> Änderungen greifen sofort
            boolean chatTestEnabledNow = plugin.getConfig().getBoolean(
                    "twitch.triggers.chat_test.enabled",
                    plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false)
            );
            boolean debugNow = plugin.getConfig().getBoolean("twitch.debug", false);
            boolean simGiftNow = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
            boolean simGiftBombNow = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
            int simGiftBombDefaultNow = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

            if (chatTestEnabledNow && lower.equals(TEST_TRIGGER)) {
                enqueue("randomevent " + author);
                return;
            }

            if (debugNow && simGiftNow && lower.equals(CMD_GIFT)) {
                enqueue("randomevent " + author);
                return;
            }

            if (debugNow && simGiftBombNow && lower.startsWith(CMD_GIFTBOMB)) {
                int n = simGiftBombDefaultNow;
                String[] parts = trimmed.split("\\s+");
                if (parts.length > 1) {
                    try { n = Math.max(1, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
                }
                enqueueMultiple(n, author);
            }
        });

        // Worker starten
        startQueueWorker();
    }

    public void stop() {
        if (queueWorker != null) {
            try { queueWorker.cancel(); } catch (Exception ignored) {}
            queueWorker = null;
        }
        if (twitchClient != null) {
            try { twitchClient.getChat().close(); } catch (Exception ignored) {}
            try { twitchClient.close(); } catch (Exception ignored) {}
            twitchClient = null;
        }
        saveQueueAsync();
    }

    // Config live anwenden (Reconnect, wenn Channel/Token geändert oder nicht verbunden)
    public void applyConfig() {
        // Flags/Interval aktualisieren (auch ohne Reconnect wirksam)
        this.subsEnabled        = plugin.getConfig().getBoolean("twitch.triggers.subscriptions.enabled", true);
        this.chatTriggerEnabled = plugin.getConfig().getBoolean("twitch.triggers.chat_test.enabled",
                plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false));
        this.bitsEnabled        = plugin.getConfig().getBoolean("twitch.triggers.bits.enabled", false);
        this.bitsPerTrigger     = Math.max(1, plugin.getConfig().getInt("twitch.triggers.bits.bits_per_trigger", 500));
        this.debug              = plugin.getConfig().getBoolean("twitch.debug", false);

        String raw = plugin.getConfig().getString("twitch.trigger_interval_seconds", "1.0");
        double seconds;
        try { seconds = Double.parseDouble(raw.replace(",", ".")); }
        catch (Exception e) { seconds = 1.0; }
        if (seconds < 0.05) seconds = 0.05;
        this.gapTicks = Math.max(1, (int) Math.round(seconds * 20.0));

        // WICHTIG: Debug-Sim-Flags live übernehmen
        this.simGiftEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
        this.simGiftBombEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
        this.simGiftBombDefaultCount = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

        // Reconnect erforderlich?
        String newChannel = plugin.getConfig().getString("twitch.channel", "");
        String newTokenRaw = plugin.getConfig().getString("twitch.oauth_token", "");
        String newToken = normalizeToken(newTokenRaw);
        boolean haveCreds = newChannel != null && !newChannel.isBlank() && !newToken.isBlank();

        boolean needRestart = false;
        if (twitchClient == null && haveCreds) {
            needRestart = true;
        } else if (twitchClient != null) {
            if (!haveCreds) {
                // keine gültigen Credentials mehr -> trennen
                stop();
                return;
            }
            if (!Objects.equals(currentChannel, newChannel) || !Objects.equals(currentToken, newToken)) {
                needRestart = true;
            }
        }

        if (needRestart) {
            stop();
            boolean legacy = plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false);
            start(newChannel, newTokenRaw, legacy);
        }
    }

    /* ===== Helpers ===== */

    private boolean isTimerRunning() {
        try {
            if (plugin instanceof TwitchRandomizer t) {
                TimerManager tm = t.getTimerManager();
                return tm != null && tm.isRunning() && !((t.getPauseService()!=null) && t.getPauseService().isPaused());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // Display-Name aus IRC-Tags; dann Login (IRC); dann EventUser-Login
    private String resolveAuthorFromChat(ChannelMessageEvent event) {
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) return display;

            if (event.getMessageEvent() != null) {
                String loginFromIrc = event.getMessageEvent().getUserName();
                if (loginFromIrc != null && !loginFromIrc.isBlank()) return loginFromIrc;
            }

            if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                return event.getUser().getName();
            }
        } catch (Throwable ignored) {}

        if (debug) plugin.getLogger().warning("[Twitch] Konnte Autor nicht ermitteln – fallback 'unknown'.");
        return "unknown";
    }

    // Für Cheers (ähnlich wie Chat)
    private String resolveAuthorFromCheer(CheerEvent event) {
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) return display;

            if (event.getMessageEvent() != null) {
                String loginFromIrc = event.getMessageEvent().getUserName();
                if (loginFromIrc != null && !loginFromIrc.isBlank()) return loginFromIrc;
            }

            if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                return event.getUser().getName();
            }
        } catch (Throwable ignored) {}

        if (debug) plugin.getLogger().warning("[Twitch] Konnte Cheer-User nicht ermitteln – fallback 'unknown'.");
        return "unknown";
    }

    private String resolveUserFromSubscription(SubscriptionEvent event) {
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) return display;

            if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                return event.getUser().getName();
            }
        } catch (Throwable ignored) {}

        if (debug) plugin.getLogger().warning("[Twitch] Konnte Sub-User nicht ermitteln – fallback 'unknown'.");
        return "unknown";
    }

    // Anonyme Cheers robust erkennen
    private boolean isLikelyAnonymousCheer(CheerEvent event, String user) {
        if (event.getUser() == null) return true;
        String low = user == null ? "" : user.toLowerCase(Locale.ROOT);
        if (low.isBlank()) return true;
        if (low.contains("anonymous")) return true;
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && display.toLowerCase(Locale.ROOT).contains("anonymous")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // Token-Handhabung
    private String normalizeToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.toLowerCase(Locale.ROOT).startsWith("oauth:")) {
            t = t.substring("oauth:".length());
        }
        return t;
    }

    private OAuth2Credential buildCredential(String raw) {
        String token = normalizeToken(raw);
        return new OAuth2Credential("twitch", token);
    }

    private void enqueue(String cmd) {
        commandQueue.offer(cmd);
        if (debug) plugin.getLogger().info("[Twitch] Enqueue: " + cmd + " (queue=" + commandQueue.size() + ")");
        saveQueueAsync();
    }

    public void enqueueMultiple(int count, String byUserNullable) {
        if (count <= 0) return;
        String base = (byUserNullable != null && !byUserNullable.isBlank())
                ? "randomevent " + byUserNullable.trim()
                : "randomevent";
        for (int i = 0; i < count; i++) {
            commandQueue.offer(base);
        }
        if (debug) plugin.getLogger().info("[Twitch] EnqueueMultiple: +" + count + " as '" + base + "' (queue=" + commandQueue.size() + ")");
        saveQueueAsync();
    }

    private void startQueueWorker() {
        this.queueWorker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isTimerRunning()) return;

            if (ticksSinceLastDispatch < gapTicks) ticksSinceLastDispatch++;
            else cooledAndReady = true;

            if (!cooledAndReady) return;

            String cmd = commandQueue.poll();
            if (cmd == null) return;

            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (debug) plugin.getLogger().info("[Twitch] Dispatch: " + cmd + " -> " + ok + " (queue=" + commandQueue.size() + ")");
            ticksSinceLastDispatch = 0;
            cooledAndReady = false;

            saveQueueAsync();

        }, 1L, 1L);
    }

    /* ===== Persistenz ===== */

    private void loadQueue() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            if (!queueFile.exists()) {
                plugin.getLogger().info("Keine bestehende Queue-Datei gefunden (skip).");
                return;
            }
            int loaded = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(queueFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    commandQueue.offer(line);
                    loaded++;
                }
            }
            plugin.getLogger().info("Queue geladen: " + loaded + " Einträge.");
        } catch (Exception e) {
            plugin.getLogger().warning("Konnte Queue nicht laden: " + e.getMessage());
        }
    }

    private void saveQueueAsync() {
        java.util.List<String> snapshot = new ArrayList<>(commandQueue);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(queueFile, false), StandardCharsets.UTF_8))) {
                    for (String s : snapshot) {
                        bw.write(s);
                        bw.newLine();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Konnte Queue nicht speichern: " + e.getMessage());
            }
        });
    }

    /* ===== Public API ===== */

    public int getQueueSize() { return commandQueue.size(); }

    public int getTicksUntilNextDispatch() {
        return cooledAndReady ? 0 : Math.max(0, gapTicks - ticksSinceLastDispatch);
    }

    public int clearQueue() {
        int n = commandQueue.size();
        commandQueue.clear();
        ticksSinceLastDispatch = 0;
        cooledAndReady = true;
        saveQueueAsync();
        if (debug) plugin.getLogger().info("[Twitch] Queue cleared (" + n + ")");
        return n;
    }
}
