package me.exnfachjan.twitchRandomizer.stats;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DragonKillListener implements Listener {

    private final TwitchRandomizer plugin;
    private final Messages i18n;

    public DragonKillListener(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getMessages();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon)) return;

        // Stats sammeln BEVOR Timer gestoppt / Queue geleert wird
        int    subs   = collectSubs();
        double euro   = collectEuro();
        int    bits   = collectBits();
        long   time   = plugin.getTimerManager() != null ? plugin.getTimerManager().getElapsedSeconds() : 0L;
        int    deaths = plugin.getDeathCounter()  != null ? plugin.getDeathCounter().get() : 0;
        // Events getriggert = Subs + ceil(Bits/bitsPerEvent) + ceil(Euro/euroPerEvent)
        // Einfachste Annäherung: direkt den dispatched-Counter aus TwitchIntegrationManager
        int eventsTriggered = collectEventsTriggered();

        List<String> channels = new ArrayList<>();
        try {
            List<String> ch = plugin.getConfig().getStringList("twitch.channels");
            if (ch != null && !ch.isEmpty()) channels.addAll(ch);
            else { String s = plugin.getConfig().getString("twitch.channel",""); if (s!=null&&!s.isBlank()) channels.add(s); }
        } catch (Throwable ignored) {}

        // 1) Timer auf 0 zurücksetzen (pausiert ihn gleichzeitig)
        try {
            if (plugin.getTimerManager() != null) {
                plugin.getTimerManager().reset();
                plugin.getLogger().info("[Dragon] Timer zurückgesetzt.");
            }
        } catch (Throwable ignored) {}

        // 2a) Deathcounter auf 0
        try {
            if (plugin.getDeathCounter() != null) {
                plugin.getDeathCounter().clear();
                plugin.getDeathCounter().broadcastActionbar();
                plugin.getLogger().info("[Dragon] Deathcounter zurückgesetzt.");
            }
        } catch (Throwable ignored) {}

        // 2b) Queue leeren
        try {
            if (plugin.getTwitch() != null) {
                plugin.getTwitch().clearQueue();
                plugin.getLogger().info("[Dragon] Queue geleert.");
            }
        } catch (Throwable ignored) {}

        // 3) Stats broadcasten
        broadcastGlobal("stats.dragon.header",    Map.of());
        broadcastGlobal("stats.dragon.time",      Map.of("time",   formatTime(time)));
        broadcastGlobal("stats.dragon.deaths",    Map.of("deaths", String.valueOf(deaths)));
        broadcastGlobal("stats.dragon.events",    Map.of("events", String.valueOf(eventsTriggered)));
        broadcastGlobal("stats.dragon.subs",      Map.of("subs",   String.valueOf(subs)));
        broadcastGlobal("stats.dragon.donations", Map.of("euro",   String.format(Locale.US,"%.2f",euro)));
        broadcastGlobal("stats.dragon.bits",      Map.of("bits",   String.valueOf(bits)));

        // Per-Channel-Aufschlüsselung bewusst weggelassen – Gesamtübersicht reicht

        saveStats(channels, subs, euro, bits, time, deaths, eventsTriggered);
    }

    // ── Aggregation ──────────────────────────────────────────────────────────

    private int    collectSubs()                   { try { return plugin.getTwitch()!=null ? plugin.getTwitch().getTotalSubsThisRun()       : 0;   } catch (Throwable ignored) { return 0;   } }
    private double collectEuro()                   { try { return plugin.getDonations()!=null ? plugin.getDonations().getTotalEuroThisRun()  : 0.0; } catch (Throwable ignored) { return 0.0; } }
    private int    collectBits()                   { try { return plugin.getTwitch()!=null ? plugin.getTwitch().getTotalBitsThisRun()       : 0;   } catch (Throwable ignored) { return 0;   } }
    private int    collectEventsTriggered()        { try { return plugin.getTwitch()!=null ? plugin.getTwitch().getTotalEventsDispatchedThisRun() : 0; } catch (Throwable ignored) { return 0; } }
    private int    collectSubsForChannel(String ch){ try { return plugin.getTwitch()!=null ? plugin.getTwitch().getSubsForChannel(ch)       : 0;   } catch (Throwable ignored) { return 0;   } }
    private double collectEuroForChannel(String ch){ try { return plugin.getDonations()!=null ? plugin.getDonations().getEuroForChannel(ch) : 0.0; } catch (Throwable ignored) { return 0.0; } }
    private int    collectBitsForChannel(String ch){ try { return plugin.getTwitch()!=null ? plugin.getTwitch().getBitsForChannel(ch)       : 0;   } catch (Throwable ignored) { return 0;   } }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcastGlobal(String key, Map<String,String> ph) {
        if (Bukkit.getOnlinePlayers().isEmpty()) { plugin.getLogger().info("[Stats] " + key + " " + ph); return; }
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(i18n.tr(p, key, ph));
    }

    // ── stats.yml ─────────────────────────────────────────────────────────────

    private void saveStats(List<String> channels, int subs, double euro, int bits, long timeSec, int deaths, int eventsTriggered) {
        try {
            File f = new File(plugin.getDataFolder(), "stats.yml");
            YamlConfiguration cfg = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
            String runKey = "run_" + System.currentTimeMillis();
            cfg.set(runKey+".timestamp",         new Date().toString());
            cfg.set(runKey+".time_seconds",       timeSec);
            cfg.set(runKey+".deaths",             deaths);
            cfg.set(runKey+".total.subs",         subs);
            cfg.set(runKey+".total.euro",         euro);
            cfg.set(runKey+".total.bits",         bits);
            cfg.set(runKey+".total.events_dispatched", eventsTriggered);
            for (String ch : channels) {
                cfg.set(runKey+".channels."+ch+".subs",  collectSubsForChannel(ch));
                cfg.set(runKey+".channels."+ch+".euro",  collectEuroForChannel(ch));
                cfg.set(runKey+".channels."+ch+".bits",  collectBitsForChannel(ch));
            }
            cfg.save(f);
            plugin.getLogger().info("[Stats] Run-Daten gespeichert: " + runKey);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Stats] Konnte stats.yml nicht speichern: " + ex.getMessage());
        }
    }

    private String formatTime(long totalSec) {
        long h = totalSec/3600, m = (totalSec%3600)/60, s = totalSec%60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}