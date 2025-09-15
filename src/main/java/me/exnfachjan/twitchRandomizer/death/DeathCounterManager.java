package me.exnfachjan.twitchRandomizer.death;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class DeathCounterManager {

    private final TwitchRandomizer plugin;
    private static final String PATH = "stats.deaths";

    public DeathCounterManager(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.contains(PATH)) {
            cfg.set(PATH, 0);
            plugin.saveConfig();
        }
    }

    public int get() {
        return plugin.getConfig().getInt(PATH, 0);
    }

    public void set(int value) {
        if (value < 0) value = 0;
        plugin.getConfig().set(PATH, value);
        plugin.saveConfig();
    }

    public void clear() { set(0); }
    public void increment() { set(get() + 1); }

    public void broadcastActionbar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendActionbar(p);
        }
    }

    public void sendActionbar(Player p) {
        String label = plugin.getMessages() != null
                ? plugin.getMessages().tr(p, "actionbar.deaths_label")
                : "Deaths";
        String msg = ChatColor.WHITE + "💀 " + label + ": " + ChatColor.WHITE + get();
        sendActionbarLegacy(p, msg);
    }

    private void sendActionbarLegacy(Player p, String msg) {
        try {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
        } catch (Throwable t) {
            p.sendMessage(msg);
        }
    }
}