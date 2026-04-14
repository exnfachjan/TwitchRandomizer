package me.exnfachjan.twitchRandomizer.timer;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

    private BukkitTask particleTask = null;
    private double particleAngle = 0.0;

    public TimerManager(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.dataFile = new File(plugin.getDataFolder(), "timer.yml");
        loadState();
        startActionbarLoop();
        if (!running) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) {
                    freezeTime();
                    startParticleEffect();
                }
            }, 1L);
        }
    }

    public void start() {
        if (!running) {
            running = true;
            onTimerStarted();
            saveState();
        }
    }

    public void stop() {
        if (running && shouldTimerRun()) {
            running = false;
            showOnce();
            onTimerStopped();
            saveState();
        }
    }

    public void resume() { start(); }

    public void reset() {
        boolean wasRunning = running;
        running = false;
        elapsedSeconds = 0;
        showOnce();
        if (wasRunning) onTimerStopped();
        else stopFreezeEffects();
        saveState();
    }

    public boolean isRunning() { return running; }
    public long getElapsedSeconds() { return elapsedSeconds; }

    public void shutdown() {
        stopFreezeEffects();
        saveState();
        if (task != null) { task.cancel(); task = null; }
    }

    private void onTimerStarted() {
        stopFreezeEffects();
        unfreezeTime();
        playTimerSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private void onTimerStopped() {
        freezeTime();
        startParticleEffect();
        playTimerSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
    }

    @SuppressWarnings("deprecation")
    private void freezeTime() {
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0);
            }
        } catch (Throwable ignored) {}

        try {
            for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                    .flatMap(w -> w.getEntities().stream())
                    .toList()) {
                if (entity instanceof Player) continue;
                try { entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); } catch (Throwable ignored2) {}
                if (entity instanceof org.bukkit.entity.LivingEntity le) {
                    le.setAI(false);
                    le.setInvulnerable(true);
                }
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void unfreezeTime() {
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 3);
            }
        } catch (Throwable ignored) {}

        try {
            for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                    .flatMap(w -> w.getEntities().stream())
                    .toList()) {
                if (entity instanceof Player) continue;
                if (entity instanceof org.bukkit.entity.LivingEntity le) {
                    le.setInvulnerable(false);
                    try {
                        org.bukkit.NamespacedKey safeKey = new org.bukkit.NamespacedKey(plugin, "safe_creeper");
                        if (!le.getPersistentDataContainer().has(safeKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                            le.setAI(true);
                        }
                    } catch (Throwable ignored2) {
                        le.setAI(true);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void startParticleEffect() {
        stopParticleTask();
        particleAngle = 0.0;

        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (running) { cancel(); return; }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    spawnSpiralParticles(p, particleAngle);
                }
                particleAngle += 15.0;
                if (particleAngle >= 360.0) particleAngle -= 360.0;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnSpiralParticles(Player p, double baseAngle) {
        Location center = p.getLocation().add(0, 1.0, 0);
        double radius = 1.3;
        int arms = 3;

        for (int arm = 0; arm < arms; arm++) {
            double armOffset = (360.0 / arms) * arm;
            double angle = Math.toRadians(baseAngle + armOffset);

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double yOffset = Math.sin(Math.toRadians(baseAngle * 2 + armOffset)) * 0.6;

            Location loc = center.clone().add(x, yOffset, z);
            p.getWorld().spawnParticle(Particle.SMALL_FLAME, loc, 1, 0, 0, 0, 0);

            double angle2 = Math.toRadians(baseAngle + armOffset + 8);
            double x2 = Math.cos(angle2) * (radius * 0.85);
            double z2 = Math.sin(angle2) * (radius * 0.85);
            double yOffset2 = Math.sin(Math.toRadians(baseAngle * 2 + armOffset + 8)) * 0.5;
            Location loc2 = center.clone().add(x2, yOffset2, z2);
            p.getWorld().spawnParticle(Particle.SMALL_FLAME, loc2, 1, 0, 0, 0, 0);
        }
    }

    private void stopParticleTask() {
        if (particleTask != null) {
            try { particleTask.cancel(); } catch (Throwable ignored) {}
            particleTask = null;
        }
    }

    private void stopFreezeEffects() {
        stopParticleTask();
        unfreezeTime();
    }

    private void playTimerSound(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() && running) {
                running = false;
                showOnce();
                onTimerStopped();
                saveState();
            }
        });
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!running) {
            org.bukkit.entity.LivingEntity le = e.getEntity();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running && le.isValid()) {
                    le.setAI(false);
                    le.setInvulnerable(true);
                    le.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!running) {
            org.bukkit.Chunk chunk = e.getChunk();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) {
                    for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                        if (entity instanceof Player) continue;
                        try { entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0)); } catch (Throwable ignored) {}
                        if (entity instanceof org.bukkit.entity.LivingEntity le) {
                            le.setAI(false);
                            le.setInvulnerable(true);
                        }
                    }
                }
            }, 2L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (running) return;
        if (e.getEntity() instanceof Player) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (running) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.sendActionBar(net.kyori.adventure.text.Component.text(
            messages.tr(p, "timer.paused_action_blocked"), net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (running) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.sendActionBar(net.kyori.adventure.text.Component.text(
            messages.tr(p, "timer.paused_action_blocked"), net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            e.getPlayer().sendActionBar(buildActionbar(e.getPlayer()));
            if (!running) {
                if (particleTask == null) {
                    startParticleEffect();
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!running) freezeTime();
                }, 5L);
            }
        });
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

        // Label dynamisch: "Tries/Versuche" wenn spectator-Modus, sonst "Deaths/Tode"
        boolean spectatorMode = plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false);
        String deathLabelKey = spectatorMode ? "actionbar.tries_label" : "actionbar.deaths_label";

        Component sep = Component.text("  |  ", NamedTextColor.DARK_GRAY);
        Component queueC = Component.text(messages.tr(p, "actionbar.queue_label") + ": ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(queue), NamedTextColor.WHITE));
        Component next = Component.text(messages.tr(p, "actionbar.next_label") + ": ", NamedTextColor.AQUA)
                .append(Component.text(nextStr, NamedTextColor.WHITE));
        Component deathsC = Component.text("💀 " + messages.tr(p, deathLabelKey) + ": ", NamedTextColor.WHITE)
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