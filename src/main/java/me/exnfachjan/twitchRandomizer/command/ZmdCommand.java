package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// /zmd reset (Beispiel-Subcommand; Permission: twitchrandomizer.zmd.reset)
public class ZmdCommand implements CommandExecutor {
    private final TwitchRandomizer plugin;

    public ZmdCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Nur In-Game.");
                return true;
            }
            if (!p.hasPermission("twitchrandomizer.zmd.reset")) {
                p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.zmd.reset");
                return true;
            }
            if (plugin.getTimerManager() != null) {
                plugin.getTimerManager().reset();
            }
            p.sendMessage(ChatColor.GREEN + "Reset ausgeführt.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Benutzung: /zmd reset");
        return true;
    }
}