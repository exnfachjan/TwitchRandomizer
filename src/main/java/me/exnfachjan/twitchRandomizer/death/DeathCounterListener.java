package me.exnfachjan.twitchRandomizer.death;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class DeathCounterListener implements Listener {

    private final DeathCounterManager counter;

    public DeathCounterListener(DeathCounterManager counter) {
        this.counter = counter;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        counter.increment();
        counter.broadcastActionbar();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Zeige dem Spieler seinen aktuellen Counter in der Actionbar beim Join
        counter.sendActionbar(event.getPlayer());
    }
}