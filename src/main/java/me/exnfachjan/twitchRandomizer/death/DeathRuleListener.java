package me.exnfachjan.twitchRandomizer.death;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Setzt Spieler nach dem Tod automatisch in den Spectator-Modus,
 * wenn challenge.auto_spectator_on_death = true.
 *
 * ÄNDERUNG: Wenn ein Spieler stirbt, werden ALLE online Spieler in Spectator
 * gesetzt – nicht nur der Tote. Dadurch pausiert die Queue sobald alle im
 * Spectator sind (GamePauseService → pause_if_all_spectator).
 */
public class DeathRuleListener implements Listener {

    private final TwitchRandomizer plugin;

    public DeathRuleListener(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false)) return;

        // Nach 1 Tick ausführen, damit Bukkit den Death-Cycle abschließen kann
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Den gestorbenen Spieler in Spectator setzen
            Player deadPlayer = event.getEntity();
            if (deadPlayer.isOnline()) {
                deadPlayer.setGameMode(GameMode.SPECTATOR);
            }

            // ALLE anderen online Spieler ebenfalls in Spectator setzen
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(deadPlayer.getUniqueId())) {
                    other.setGameMode(GameMode.SPECTATOR);
                }
            }
            // Sobald alle im Spectator sind, greift GamePauseService.pause_if_all_spectator
            // und pausiert Timer + Queue automatisch.
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false)) return;

        Player player = event.getPlayer();
        // Sicherstellen, dass er auch nach dem Respawn Spectator bleibt
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }
}