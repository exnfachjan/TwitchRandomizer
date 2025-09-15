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
 * Hinweis: Direkte Referenzen auf TwitchIntegrationManager wurden entfernt,
 * damit die Klasse nicht am Paket-Refactor scheitert.
 */
public class DeathRuleListener implements Listener {

    private final TwitchRandomizer plugin;

    public DeathRuleListener(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false)) return;

        Player player = event.getEntity();
        // Nach 1 Tick ausführen, damit Bukkit den Death-Cycle abschließen kann
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false)) return;

        Player player = event.getPlayer();
        // Sicherstellen, dass er auch nach dem Respawn Spectator ist
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }
}