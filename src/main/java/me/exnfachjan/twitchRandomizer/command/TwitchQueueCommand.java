package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class TwitchQueueCommand implements CommandExecutor {

    private final TwitchRandomizer plugin;

    public TwitchQueueCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length < 2 || !args[0].equalsIgnoreCase("add")) {
            sender.sendMessage(ChatColor.YELLOW + "Benutzung: /twitchqueue add <anzahl> [nutzername]");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Ungültige Anzahl: " + args[1]);
            return true;
        }

        String byUser = null;
        if (args.length >= 3) {
            byUser = args[2].trim();
            if (byUser.isEmpty()) byUser = null;
        }

        TwitchIntegrationManager tim = plugin.getTwitch();
        if (tim == null) {
            sender.sendMessage(ChatColor.RED + "Twitch-Integration nicht verfügbar.");
            return true;
        }

        tim.enqueueMultiple(amount, byUser);
        sender.sendMessage(ChatColor.GREEN + "Zur Queue hinzugefügt: " + amount
                + (byUser != null ? (" × " + byUser) : "") + ". Aktuell in Queue: " + tim.getQueueSize());
        return true;
    }
}