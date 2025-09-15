package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.gui.ConfigGui;
import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TrGuiCommand implements CommandExecutor {
    private final TwitchRandomizer plugin;
    private final ConfigGui gui;

    public TrGuiCommand(TwitchRandomizer plugin, ConfigGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Nur In-Game.");
            return true;
        }

        // Erlaube Super-Node ODER die spezifische Open-Permission
        if (!(p.hasPermission("twitchrandomizer.gui") || p.hasPermission("twitchrandomizer.gui.open"))) {
            p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.gui.open");
            return true;
        }

        gui.openMain(p);
        return true;
    }
}