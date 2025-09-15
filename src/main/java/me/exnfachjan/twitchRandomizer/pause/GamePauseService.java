package me.exnfachjan.twitchRandomizer.pause;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks "global pause" conditions such as death screen or all players being spectators.
 * Timer increments and Twitch queue processing should respect isPaused().
 */
public final class GamePauseService implements Listener {

    private final TwitchRandomizer plugin;

    // Players currently in the death screen (PlayerDeathEvent -> PlayerRespawnEvent)
    private final Set<UUID> inDeathScreen = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final boolean pauseOnDeath;
    private final boolean pauseIfAllSpectators;

    public GamePauseService(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.pauseOnDeath = plugin.getConfig().getBoolean("challenge.pause_on_death", true);
        this.pauseIfAllSpectators = plugin.getConfig().getBoolean("challenge.pause_if_all_spectator", true);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isPaused() {
        if (pauseOnDeath && !inDeathScreen.isEmpty()) return true;
        if (pauseIfAllSpectators && allOnlineAreSpectator()) return true;
        return false;
    }

    private boolean allOnlineAreSpectator() {
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            any = true;
            if (p.getGameMode() != GameMode.SPECTATOR) return false;
        }
        // If nobody is online, prefer pausing
        return !any || true;
    }

    // ==== Events ====

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!pauseOnDeath) return;
        inDeathScreen.add(e.getEntity().getUniqueId());
        notifyChange();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!pauseOnDeath) return;
        inDeathScreen.remove(e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::notifyChange);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        inDeathScreen.remove(e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::notifyChange);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        inDeathScreen.remove(e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::notifyChange);
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent e) {
        Bukkit.getScheduler().runTask(plugin, this::notifyChange);
    }

    private void notifyChange() {
        try { plugin.onGlobalPauseStateChanged(isPaused()); } catch (Throwable ignored) {}
    }
}
