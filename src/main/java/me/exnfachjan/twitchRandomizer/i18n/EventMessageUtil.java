package me.exnfachjan.twitchRandomizer.i18n;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class EventMessageUtil {

    private EventMessageUtil() {}

    public static void halfHeart(TwitchRandomizer plugin, Player p, String byUser) {
        String key = (byUser == null || byUser.isBlank())
                ? "events.damage.half_heart.solo"
                : "events.damage.half_heart.by";
        Map<String, String> ph = new HashMap<>();
        if (byUser != null) ph.put("user", byUser);
        p.sendMessage(plugin.getMessages().tr(p, key, ph));
    }

    public static void spawn(TwitchRandomizer plugin, Player p, String byUser, String entityName, int amount) {
        String key = (byUser == null || byUser.isBlank())
                ? "events.spawn.solo"
                : "events.spawn.by";
        Map<String, String> ph = new HashMap<>();
        if (byUser != null) ph.put("user", byUser);
        ph.put("amount", String.valueOf(amount));
        ph.put("entity", pretty(entityName));
        p.sendMessage(plugin.getMessages().tr(p, key, ph));
    }

    public static void igniteShort(TwitchRandomizer plugin, Player p, String byUser) {
        String key = (byUser == null || byUser.isBlank())
                ? "events.ignite.short.solo"
                : "events.ignite.short.by";
        Map<String, String> ph = new HashMap<>();
        if (byUser != null) ph.put("user", byUser);
        p.sendMessage(plugin.getMessages().tr(p, key, ph));
    }

    public static void giveItem(TwitchRandomizer plugin, Player p, String byUser, Material item, int amount) {
        String key = (byUser == null || byUser.isBlank())
                ? "events.give.item.solo"
                : "events.give.item.by";
        Map<String, String> ph = new HashMap<>();
        if (byUser != null) ph.put("user", byUser);
        ph.put("amount", String.valueOf(amount));
        ph.put("item", pretty(item.name()));
        p.sendMessage(plugin.getMessages().tr(p, key, ph));
    }

    public static void teleportRandom(TwitchRandomizer plugin, Player p, String byUser) {
        String key = (byUser == null || byUser.isBlank())
                ? "events.teleport.random.solo"
                : "events.teleport.random.by";
        Map<String, String> ph = new HashMap<>();
        if (byUser != null) ph.put("user", byUser);
        p.sendMessage(plugin.getMessages().tr(p, key, ph));
    }

    private static String pretty(String enumName) {
        // ZOMBIE → Zombie, DETECTOR_RAIL → Detector Rail
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            if (i + 1 < parts.length) sb.append(' ');
        }
        return sb.toString();
    }
}