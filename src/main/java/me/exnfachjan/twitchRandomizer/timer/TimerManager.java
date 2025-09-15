package me.exnfachjan.twitchRandomizer.timer;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TimerManager implements Listener {

    private final TwitchRandomizer plugin;
    private final Messages messages;
    private BukkitRunnable task;
    private long elapsedSeconds = 0;
    private boolean running = false;

    private final File dataFile;
    private long lastAutosaveSecond = 0;

    public TimerManager(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.dataFile = new File(plugin.getDataFolder(), "timer.yml");
        loadState();
        startActionbarLoop();
    }

    public void start() {
        if (!running) {
            running = true;
            saveState();
        }
    }

    public void stop() {
        if (running && shouldTimerRun()) {
            running = false;
            showOnce();
            saveState();
        }
    }

    public void resume() { start(); }

    public void reset() {
        running = false;
        elapsedSeconds = 0;
        showOnce();
        saveState();
    }

    public boolean isRunning() { return running; }

    public long getElapsedSeconds() { return elapsedSeconds; }

    public void shutdown() {
        saveState();
        if (task != null) { task.cancel(); task = null; }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() && running) {
                running = false;
                showOnce();
                saveState();
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> e.getPlayer().sendActionBar(buildActionbar(e.getPlayer())));
    }

    private void startActionbarLoop() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (running && shouldTimerRun()) {
                    elapsedSeconds++;
                    if (elapsedSeconds - lastAutosaveSecond >= 60) {
                        saveState();
                        lastAutosaveSecond = elapsedSeconds;
                    }
                }
                broadcastActionBar();
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    private boolean shouldTimerRun() {

        try {
            if (plugin.getPauseService() != null && plugin.getPauseService().isPaused()) {
                return false;
            }
        } catch (Throwable ignored) {}
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return false;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
                return true;
            }
        }
        return false;
    }

    private Component buildActionbar(Player p) {
        Component left;
        if (running) {
            long h = elapsedSeconds / 3600;
            long m = (elapsedSeconds % 3600) / 60;
            long s = elapsedSeconds % 60;
            String time = String.format("%02d:%02d:%02d", h, m, s);
            Map<String, String> ph = new HashMap<>();
            ph.put("time", time);
            left = Component.text(messages.tr(p, "actionbar.timer.running_prefix", ph), NamedTextColor.GOLD);
        } else {
            left = Component.text(messages.tr(p, "actionbar.timer.paused"), NamedTextColor.RED);
        }

        int queue = 0;
        String nextStr = messages.tr(p, "actionbar.next_none");
        try {
            TwitchIntegrationManager tim = plugin.getTwitch();
            if (tim != null) {
                queue = Math.max(0, tim.getQueueSize());
                int ticks = Math.max(0, tim.getTicksUntilNextDispatch());
                int secs = (int) Math.ceil(ticks / 20.0);
                if (queue > 0) nextStr = secs + "s";
            }
        } catch (Throwable ignored) {}

        int deaths = Math.max(0, plugin.getConfig().getInt("stats.deaths", 0));

        Component sep = Component.text("  |  ", NamedTextColor.DARK_GRAY);
        Component queueC = Component.text(messages.tr(p, "actionbar.queue_label") + ": ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(queue), NamedTextColor.WHITE));
        Component next = Component.text(messages.tr(p, "actionbar.next_label") + ": ", NamedTextColor.AQUA)
                .append(Component.text(nextStr, NamedTextColor.WHITE));
        Component deathsC = Component.text("💀 " + messages.tr(p, "actionbar.deaths_label") + ": ", NamedTextColor.WHITE)
                .append(Component.text(String.valueOf(deaths), NamedTextColor.WHITE));

        return left.append(sep).append(queueC).append(sep).append(next).append(sep).append(deathsC);
    }

    private void broadcastActionBar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(buildActionbar(p));
        }
    }

    private void showOnce() {
        broadcastActionBar();
    }

    private void loadState() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!dataFile.exists()) { saveState(); return; }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        this.elapsedSeconds = cfg.getLong("elapsedSeconds", 0L);
        this.running = false;
        saveState();
    }

    private void saveState() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("elapsedSeconds", elapsedSeconds);
        cfg.set("running", running);
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Konnte timer.yml nicht speichern: " + e.getMessage()); }
    }
}