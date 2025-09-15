package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.gui.ConfigGui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuiCommand implements CommandExecutor {
    private final ConfigGui gui;

    public GuiCommand(ConfigGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Nur In-Game.");
            return true;
        }

        if (!(p.hasPermission("twitchrandomizer.gui") || p.hasPermission("twitchrandomizer.gui.open"))) {
            p.sendMessage(ChatColor.RED + "Keine Berechtigung: twitchrandomizer.gui.open");
            return true;
        }

        gui.openMain(p);
        return true;
    }
}