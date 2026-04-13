package me.exnfachjan.twitchRandomizer.twitch;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.command.RandomEventCommand;
import me.exnfachjan.twitchRandomizer.events.RandomEvents;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.CheerEvent;
import com.github.twitch4j.chat.events.channel.SubscriptionEvent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public class TwitchIntegrationManager {
    private final JavaPlugin plugin;
    private TwitchClient twitchClient;

    private List<String> currentChannels = new ArrayList<>();
    private String currentToken = null;

    private boolean subsEnabled;
    private boolean chatTriggerEnabled;

    private boolean bitsEnabled;

    private static final String TEST_TRIGGER = "!test";
    private static final String CMD_GIFT = "!gift";
    private static final String CMD_GIFTBOMB = "!giftbomb";
    private boolean simGiftEnabled;
    private boolean simGiftBombEnabled;
    private int simGiftBombDefaultCount;

    private final Queue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask queueWorker;

    private boolean queueLoaded = false;

    private int gapTicks = 20;
    private int ticksSinceLastDispatch = 0;
    private boolean cooledAndReady = true;

    private boolean debug = false;

    private final File queueFile;

    private final Set<UUID> deathScreenPlayers = ConcurrentHashMap.newKeySet();

    public TwitchIntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.queueFile = new File(plugin.getDataFolder(), "queue.txt");
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerDeath(PlayerDeathEvent event) {
                deathScreenPlayers.add(event.getEntity().getUniqueId());
            }
            @EventHandler
            public void onPlayerRespawn(PlayerRespawnEvent event) {
                deathScreenPlayers.remove(event.getPlayer().getUniqueId());
            }
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                deathScreenPlayers.remove(event.getPlayer().getUniqueId());
            }
        }, plugin);
    }

    private boolean isAnyPlayerInDeathScreen() {
        if (deathScreenPlayers.isEmpty()) return false;
        deathScreenPlayers.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            return p == null || !p.isDead();
        });
        return !deathScreenPlayers.isEmpty();
    }

    private boolean isTimerRunningOrDeathscreen() {
        try {
            if (plugin instanceof TwitchRandomizer t) {
                TimerManager tm = t.getTimerManager();
                if (tm != null && tm.isRunning() && !((t.getPauseService() != null) && t.getPauseService().isPaused())) {
                    return true;
                }
                if (isAnyPlayerInDeathScreen()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Currency helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns bitsPerEvent from DonationsManager (min 100). */
    private int getBitsPerEvent() {
        try {
            if (plugin instanceof TwitchRandomizer t && t.getDonations() != null) {
                return t.getDonations().getBitsPerEvent();
            }
        } catch (Throwable ignored) {}
        return 500;
    }

    /** Returns eventsPerSub from DonationsManager (min 1). */
    private int getEventsPerSub() {
        try {
            if (plugin instanceof TwitchRandomizer t && t.getDonations() != null) {
                return t.getDonations().getEventsPerSub();
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Start / Stop / Apply
    // ─────────────────────────────────────────────────────────────────────────

    public void start(List<String> channels, String oauthToken, boolean legacyChatTriggerFlag) {
        String normToken = normalizeToken(oauthToken);
        if (channels == null || channels.isEmpty() || normToken.isBlank()) {
            plugin.getLogger().warning("Twitch nicht konfiguriert (twitch.channels/oauth_token). Überspringe Verbindung.");
            return;
        }

        this.subsEnabled = plugin.getConfig().getBoolean("twitch.triggers.subscriptions.enabled", true);
        this.chatTriggerEnabled = plugin.getConfig().getBoolean("twitch.triggers.chat_test.enabled", legacyChatTriggerFlag);
        this.bitsEnabled = plugin.getConfig().getBoolean("twitch.triggers.bits.enabled", false);

        String raw = plugin.getConfig().getString("twitch.trigger_interval_seconds", "1.0");
        double seconds;
        try { seconds = Double.parseDouble(raw.replace(",", ".")); }
        catch (Exception e) { seconds = 1.0; plugin.getLogger().warning("Ungültiger Wert für twitch.trigger_interval_seconds: '" + raw + "'. Fallback 1.0s."); }
        if (seconds < 0.05) seconds = 0.05;
        this.gapTicks = Math.max(1, (int) Math.round(seconds * 20.0));

        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);

        this.simGiftEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
        this.simGiftBombEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
        this.simGiftBombDefaultCount = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

        OAuth2Credential chatCredential = buildCredential(normToken);
        this.twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(chatCredential)
                .build();

        for (String channel : channels) {
            if (channel != null && !channel.isBlank()) {
                twitchClient.getChat().joinChannel(channel);
                plugin.getLogger().info("Mit Twitch-Chat verbunden: #" + channel);
            }
        }
        this.currentChannels = new ArrayList<>(channels);
        this.currentToken = normToken;

        plugin.getLogger().info("Trigger-Intervall: " + seconds + "s (" + gapTicks + " Ticks)");
        plugin.getLogger().info("Triggers: subs=" + subsEnabled
                + ", chat_test=" + chatTriggerEnabled
                + ", bits=" + bitsEnabled);

        if (!queueLoaded) {
            loadQueue();
            queueLoaded = true;
        }

        twitchClient.getEventManager().onEvent(SubscriptionEvent.class, event -> {
            if (!subsEnabled) return;
            if (!isTimerRunningOrDeathscreen()) return;
            String user = resolveUserFromSubscription(event);
            int eventsPerSub = getEventsPerSub();
            enqueueMultiple(eventsPerSub, user);
            if (debug) plugin.getLogger().info("[Twitch] Sub von " + user + " -> +" + eventsPerSub + " Events (eventsPerSub=" + eventsPerSub + ")");
        });

        twitchClient.getEventManager().onEvent(CheerEvent.class, event -> {
            if (!bitsEnabled) return;
            if (!isTimerRunningOrDeathscreen()) return;

            int bits = Math.max(0, event.getBits());
            int bitsPerEvent = getBitsPerEvent();
            if (bits < bitsPerEvent) return;

            String user = resolveAuthorFromCheer(event);
            if (isLikelyAnonymousCheer(event, user)) {
                if (debug) plugin.getLogger().info("[Twitch] Cheer ignoriert (anonym) – bits=" + bits);
                return;
            }

            int count = bits / bitsPerEvent;
            enqueueMultiple(count, user);
            if (debug) plugin.getLogger().info("[Twitch] Cheer: " + user + " -> " + bits + " bits -> +" + count + " Events (bitsPerEvent=" + bitsPerEvent + ", queue=" + commandQueue.size() + ")");
        });

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            if (!isTimerRunningOrDeathscreen()) return;

            String msg = event.getMessage();
            if (msg == null) return;

            String author = resolveAuthorFromChat(event);
            String trimmed = msg.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);

            boolean chatTestEnabledNow = plugin.getConfig().getBoolean(
                    "twitch.triggers.chat_test.enabled",
                    plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false));
            boolean simGiftNow = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
            boolean simGiftBombNow = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
            int simGiftBombDefaultNow = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

            if (chatTestEnabledNow && lower.equals(TEST_TRIGGER)) {
                enqueue("randomevent " + author);
                return;
            }

            if (simGiftNow && lower.equals(CMD_GIFT)) {
                enqueue("randomevent " + author);
                return;
            }

            if (simGiftBombNow && lower.startsWith(CMD_GIFTBOMB)) {
                int n = simGiftBombDefaultNow;
                String[] parts = trimmed.split("\\s+");
                if (parts.length > 1) {
                    try { n = Math.max(1, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
                }
                enqueueMultiple(n, author);
            }
        });

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

    public void applyConfig() {
        this.subsEnabled = plugin.getConfig().getBoolean("twitch.triggers.subscriptions.enabled", true);
        this.chatTriggerEnabled = plugin.getConfig().getBoolean("twitch.triggers.chat_test.enabled",
                plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false));
        this.bitsEnabled = plugin.getConfig().getBoolean("twitch.triggers.bits.enabled", false);
        this.debug = plugin.getConfig().getBoolean("twitch.debug", false);

        String raw = plugin.getConfig().getString("twitch.trigger_interval_seconds", "1.0");
        double seconds;
        try { seconds = Double.parseDouble(raw.replace(",", ".")); }
        catch (Exception e) { seconds = 1.0; }
        if (seconds < 0.05) seconds = 0.05;
        this.gapTicks = Math.max(1, (int) Math.round(seconds * 20.0));

        this.simGiftEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_gift.enabled", false);
        this.simGiftBombEnabled = plugin.getConfig().getBoolean("twitch.triggers.sim_giftbomb.enabled", false);
        this.simGiftBombDefaultCount = Math.max(1, plugin.getConfig().getInt("twitch.triggers.sim_giftbomb.default_count", 5));

        List<String> newChannels = plugin.getConfig().getStringList("twitch.channels");
        if (newChannels == null || newChannels.isEmpty()) {
            String fallback = plugin.getConfig().getString("twitch.channel", "");
            if (fallback != null && !fallback.isBlank()) {
                newChannels = List.of(fallback);
            }
        }
        String newTokenRaw = plugin.getConfig().getString("twitch.oauth_token", "");
        String newToken = normalizeToken(newTokenRaw);
        boolean haveCreds = newChannels != null && !newChannels.isEmpty() && !newToken.isBlank();

        boolean needRestart = false;
        if (twitchClient == null && haveCreds) {
            needRestart = true;
        } else if (twitchClient != null) {
            if (!haveCreds) {
                stop();
                return;
            }
            if (!Objects.equals(currentChannels, newChannels) || !Objects.equals(currentToken, newToken)) {
                needRestart = true;
            }
        }

        if (needRestart) {
            stop();
            boolean legacy = plugin.getConfig().getBoolean("twitch.chat_trigger.enabled", false);
            start(newChannels, newTokenRaw, legacy);
        }
    }

    private boolean isTimerRunning() {
        try {
            if (plugin instanceof TwitchRandomizer t) {
                TimerManager tm = t.getTimerManager();
                return tm != null && tm.isRunning() && !((t.getPauseService() != null) && t.getPauseService().isPaused());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void startQueueWorker() {
        this.queueWorker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isAnyPlayerInDeathScreen()) return;
            if (!isTimerRunning()) return;

            try {
                RandomEventCommand randomEventExecutor = null;
                RandomEvents randomEvents = null;
                List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

                if (plugin instanceof TwitchRandomizer tr) {
                    randomEventExecutor = tr.getRandomEventExecutor();
                    if (randomEventExecutor != null) {
                        randomEvents = randomEventExecutor.events;
                    }
                }
                int[] weights = randomEventExecutor != null ? randomEventExecutor.getWeights() : null;

                if (weights == null || weights.length == 0) return;

                int[] weightsForPick = Arrays.copyOf(weights, weights.length);

                boolean anyGroundActive = false;
                boolean noCraftingActive = false;
                if (randomEvents != null && !players.isEmpty()) {
                    for (Player player : players) {
                        if (randomEvents.isAnyGroundEventActive(player)) anyGroundActive = true;
                        if (randomEvents.isNoCraftingActive(player)) noCraftingActive = true;
                    }
                }
                if (anyGroundActive) {
                    weightsForPick[11] = 0;
                    weightsForPick[13] = 0;
                }
                if (noCraftingActive) {
                    weightsForPick[9] = 0;
                }

                int pickable = Arrays.stream(weightsForPick).sum();
                if (pickable <= 0) return;
            } catch (Throwable t) {
                plugin.getLogger().warning("[Twitch] Fehler beim Queue-Event-Check: " + t.getMessage());
                return;
            }

            if (ticksSinceLastDispatch < gapTicks) ticksSinceLastDispatch += 2;
            else cooledAndReady = true;

            if (!cooledAndReady) return;

            String cmd = commandQueue.poll();
            if (cmd == null) return;

            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (debug) plugin.getLogger().info("[Twitch] Dispatch: " + cmd + " -> " + ok + " (queue=" + commandQueue.size() + ")");
            ticksSinceLastDispatch = 0;
            cooledAndReady = false;

            saveQueueAsync();

        }, 1L, 2L);
    }

    private String resolveAuthorFromChat(ChannelMessageEvent event) {
        String name = "unknown";
        String role = "";
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) name = display;
            else if (event.getMessageEvent() != null) {
                String loginFromIrc = event.getMessageEvent().getUserName();
                if (loginFromIrc != null && !loginFromIrc.isBlank()) name = loginFromIrc;
            } else if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                name = event.getUser().getName();
            }
            role = resolveTwitchRoleFromIRC(event.getMessageEvent());
        } catch (Throwable ignored) {}
        return colorizeByRole(name, role);
    }

    private String resolveAuthorFromCheer(CheerEvent event) {
        String name = "unknown";
        String role = "";
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) name = display;
            else if (event.getMessageEvent() != null) {
                String loginFromIrc = event.getMessageEvent().getUserName();
                if (loginFromIrc != null && !loginFromIrc.isBlank()) name = loginFromIrc;
            } else if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                name = event.getUser().getName();
            }
            role = resolveTwitchRoleFromIRC(event.getMessageEvent());
        } catch (Throwable ignored) {}
        return colorizeByRole(name, role);
    }

    private String resolveUserFromSubscription(SubscriptionEvent event) {
        String name = "unknown";
        String role = "";
        try {
            String display = (event.getMessageEvent() != null)
                    ? event.getMessageEvent().getTagValue("display-name").orElse(null)
                    : null;
            if (display != null && !display.isBlank()) name = display;
            else if (event.getUser() != null && event.getUser().getName() != null && !event.getUser().getName().isBlank()) {
                name = event.getUser().getName();
            }
            role = resolveTwitchRoleFromIRC(event.getMessageEvent());
        } catch (Throwable ignored) {}
        return colorizeByRole(name, role);
    }

    private String resolveTwitchRoleFromIRC(Object msgEvent) {
        if (msgEvent == null) return "";
        try {
            java.lang.reflect.Method m = msgEvent.getClass().getMethod("getTagValue", String.class);
            @SuppressWarnings("unchecked")
            java.util.Optional<String> opt = (java.util.Optional<String>) m.invoke(msgEvent, "badges");
            String badges = opt.orElse("");
            if (badges == null || badges.isBlank()) return "";
            String lower = badges.toLowerCase(Locale.ROOT);
            if (lower.contains("broadcaster")) return "broadcaster";
            if (lower.contains("moderator")) return "moderator";
            if (lower.contains("vip")) return "vip";
        } catch (Throwable ignored) {}
        return "";
    }

    private String colorizeByRole(String name, String role) {
        if (role != null && !role.isEmpty()) {
            return "role:" + role + ":" + name;
        }
        return name;
    }

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

    public void enqueue(String cmd) {
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
        if (!plugin.isEnabled()) {
            saveQueueSync();
            return;
        }
        java.util.List<String> snapshot = new ArrayList<>(commandQueue);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveQueueSync(snapshot));
    }

    private void saveQueueSync() {
        saveQueueSync(new ArrayList<>(commandQueue));
    }

    private void saveQueueSync(java.util.List<String> snapshot) {
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
    }

    public int getQueueSize() {
        return commandQueue.size();
    }

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