package me.exnfachjan.twitchRandomizer;

import me.exnfachjan.twitchRandomizer.command.*;
import me.exnfachjan.twitchRandomizer.death.*;
import me.exnfachjan.twitchRandomizer.gui.*;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import me.exnfachjan.twitchRandomizer.config.SessionConfig;
import me.exnfachjan.twitchRandomizer.reset.ResetManager;
import me.exnfachjan.twitchRandomizer.pause.GamePauseService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TwitchRandomizer extends JavaPlugin {

    private TimerManager timerManager;
    private TwitchIntegrationManager twitch;

    private GamePauseService pauseService;
    private RandomEventCommand randomEventExecutor;
    private DeathCounterManager deathCounter;
    private Messages messages;

    private SessionConfig sessionConfig;
    private ResetManager resetManager;

    // ==== Reset-Bestätigung (In-Memory) ====
    private long resetConfirmUntilMs = 0L;
    private String resetConfirmRequester = null;

    /**
     * Gibt die Default-Event-Gewichte zurück, wie sie im Projekt verwendet werden sollen.
     * Diese Werte sind hartcodiert und können NICHT über die config.yml überschrieben werden!
     */
    public Map<String, Integer> getDefaultWeights() {
        Map<String, Integer> defaults = new HashMap<>();
        defaults.put("spawn_mobs", 35);
        defaults.put("potion", 15);
        defaults.put("give_item", 20);
        defaults.put("clear_inventory", 4);
        defaults.put("teleport", 5);
        defaults.put("damage_half_heart", 10);
        defaults.put("fire", 10);
        defaults.put("inv_shuffle", 8);
        defaults.put("hot_potato", 7);
        defaults.put("no_crafting", 6);
        defaults.put("safe_creepers", 5);
        defaults.put("floor_is_lava", 7);
        defaults.put("nasa_call", 5);
        defaults.put("slippery_ground", 6);
        defaults.put("hell_is_calling", 6);
        defaults.put("tnt_rain", 4);
        defaults.put("anvil_rain", 6);
        defaults.put("skyblock", 7);
        defaults.put("fake_totem", 5);
        return defaults;
    }

    @Override
    public void onLoad() {
        try {
            this.sessionConfig = new SessionConfig(getDataFolder());
            this.resetManager = new ResetManager(this, sessionConfig);
            this.resetManager.executeWorldResetIfNecessary();
        } catch (Throwable t) {
            getLogger().warning("[Reset] Early reset onLoad failed: " + t.getMessage());
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        } catch (Throwable t) {
            getLogger().warning("Could not register BungeeCord plugin channel: " + t.getMessage());
        }

        this.messages = new Messages(this);
        this.messages.load();

        this.timerManager = new TimerManager(this);
        this.pauseService = new GamePauseService(this);
        getServer().getPluginManager().registerEvents(timerManager, this);
        getServer().getPluginManager().registerEvents(new DeathRuleListener(this), this);

        this.deathCounter = new DeathCounterManager(this);
        this.deathCounter.load();
        getServer().getPluginManager().registerEvents(new DeathCounterListener(this.deathCounter), this);

        ConfigGui configGui = new ConfigGui(this);
        getServer().getPluginManager().registerEvents(new GuiListener(this, configGui), this);

        PluginCommand randomEventCmd = getCommand("randomevent");
        if (randomEventCmd != null) {
            this.randomEventExecutor = new RandomEventCommand(this);
            randomEventCmd.setExecutor(this.randomEventExecutor);
        }

        PluginCommand timerCmd = getCommand("timer");
        if (timerCmd != null) timerCmd.setExecutor(new TimerCommand(timerManager));

        PluginCommand resetCmd = getCommand("reset");
        if (resetCmd != null) resetCmd.setExecutor(new ResetCommand(this));

        // NEU: Mehrere Channels unterstützen
        List<String> channels = getConfig().getStringList("twitch.channels");
        if (channels == null || channels.isEmpty()) {
            // Fallback für alte Config (nur einzelner Channel)
            String single = getConfig().getString("twitch.channel", "");
            if (single != null && !single.isBlank()) {
                channels = List.of(single);
            }
        }
        String token   = getConfig().getString("twitch.oauth_token", "");
        boolean enableChatTrigger = getConfig().getBoolean("twitch.chat_trigger.enabled", false);

        this.twitch = new TwitchIntegrationManager(this);
        // Wichtig: Passe auch TwitchIntegrationManager an!
        twitch.start(channels, token, enableChatTrigger);

        PluginCommand cfgCmd = getCommand("trconfig");
        if (cfgCmd != null) {
            ConfigCommand cfg = new ConfigCommand(this);
            cfgCmd.setExecutor(cfg);
            cfgCmd.setTabCompleter(cfg);
        }

        TwitchQueueCommand queueExecutor = new TwitchQueueCommand(this);
        PluginCommand tq = getCommand("twitchqueue");
        if (tq != null) tq.setExecutor(queueExecutor);
        PluginCommand queueCmd = getCommand("queue");
        if (queueCmd != null) queueCmd.setExecutor(queueExecutor);

        PluginCommand clearDeathCmd = getCommand("cleardeath");
        if (clearDeathCmd != null) clearDeathCmd.setExecutor(new ClearDeathCommand(this.deathCounter));

        PluginCommand trguiCmd = getCommand("trgui");
        if (trguiCmd != null) trguiCmd.setExecutor(new TrGuiCommand(this, configGui));
    }

    @Override
    public void onDisable() {
        if (twitch != null) twitch.stop();
        if (timerManager != null) timerManager.shutdown();
        if (messages != null) messages.savePlayerLocales();
    }

    public Messages getMessages() { return messages; }
    public TimerManager getTimerManager() { return timerManager; }
    public TwitchIntegrationManager getTwitch() { return twitch; }
    public RandomEventCommand getRandomEventExecutor() { return randomEventExecutor; }
    public DeathCounterManager getDeathCounter() { return deathCounter; }
    public ResetManager getResetManager() { return resetManager; }
    public SessionConfig getSessionConfig() { return sessionConfig; }

    public void applyDynamicConfig() {
        // IMMER im Main-Thread ausführen, damit Bukkit- & Twitch4J-Aufrufe safe sind
        org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
            try {
                if (twitch != null) {
                    twitch.applyConfig(); // prüft Änderungen & reconnectet bei Bedarf
                }
            } catch (Throwable ignored) {}

            try {
                if (randomEventExecutor != null) {
                    randomEventExecutor.reloadWeights(); // RandomEvent-Gewichte neu laden
                }
            } catch (Throwable ignored) {}

            try {
                if (messages != null) {
                    messages.load(); // i18n live neu laden
                }
            } catch (Throwable e) {
                getLogger().warning("i18n reload failed: " + e.getMessage());
            }
        });
    }

    // ==== Reset Confirmation API ====
    public synchronized void startResetConfirmation(String requester, int windowSeconds) {
        if (windowSeconds < 1) windowSeconds = 1;
        this.resetConfirmUntilMs = System.currentTimeMillis() + windowSeconds * 1000L;
        this.resetConfirmRequester = requester;
    }

    public synchronized boolean hasPendingResetConfirmation() {
        return System.currentTimeMillis() <= resetConfirmUntilMs;
    }

    public synchronized int secondsLeftForResetConfirmation() {
        long left = resetConfirmUntilMs - System.currentTimeMillis();
        if (left <= 0) return 0;
        return (int) Math.ceil(left / 1000.0);
    }

    public synchronized String getResetConfirmRequester() {
        return resetConfirmRequester;
    }

    public synchronized void clearResetConfirmation() {
        this.resetConfirmUntilMs = 0L;
        this.resetConfirmRequester = null;
    }

    public GamePauseService getPauseService() { return pauseService; }

    public void onGlobalPauseStateChanged(boolean paused) {
        // No-op: Timer tick/actionbar already reflect pause via GamePauseService.
    }
}