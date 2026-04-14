package me.exnfachjan.twitchRandomizer.death;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Zählt Tode / Versuche je nach Modus:
 *
 * auto_spectator_on_death = true  → "Tries / Versuche"
 *   Nur 1x pro Team-Tod zählen. Da bei jedem Spielertod alle in Spectator
 *   gehen (= 1 Versuch verbraucht), darf nur der erste Tod des Events zählen.
 *   Wir zählen daher ausschließlich den Tod des Spielers, der stirbt – aber
 *   NUR wenn noch kein anderer Spieler bereits im Spectator ist
 *   (= erster Tod dieses Versuchs).
 *
 * auto_spectator_on_death = false → "Deaths / Tode"
 *   Jeden einzelnen Spielertod zählen, wie bisher.
 */
public class DeathCounterListener implements Listener {

    private final DeathCounterManager counter;
    private final TwitchRandomizer plugin;

    public DeathCounterListener(DeathCounterManager counter, TwitchRandomizer plugin) {
        this.counter = counter;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        boolean spectatorMode = plugin.getConfig().getBoolean("challenge.auto_spectator_on_death", false);

        if (spectatorMode) {
            // Tries-Modus: Nur zählen, wenn dies der erste Tod des aktuellen Versuchs ist.
            // "Erster Tod" = kein online Spieler ist bereits im Spectator-Modus
            // (Der gestorbene Spieler ist zu diesem Zeitpunkt noch DEAD, nicht Spectator)
            boolean anyAlreadySpectator = org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(event.getEntity().getUniqueId()))
                    .anyMatch(p -> p.getGameMode() == org.bukkit.GameMode.SPECTATOR);

            if (!anyAlreadySpectator) {
                // Erster Tod dieses Versuchs → Versuch zählen
                counter.increment();
                counter.broadcastActionbar();
            }
            // Weitere Tode desselben Versuchs (theoretisch nicht möglich da alle
            // gleichzeitig in Spectator gehen, aber zur Sicherheit ignorieren)
        } else {
            // Deaths-Modus: Jeden Tod zählen
            counter.increment();
            counter.broadcastActionbar();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Zeige dem Spieler seinen aktuellen Counter in der Actionbar beim Join
        counter.sendActionbar(event.getPlayer());
    }
}