package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.twitch.TwitchIntegrationManager;
import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

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

        Messages i18n = plugin.getMessages();
        Player p = (sender instanceof Player) ? (Player) sender : null;

        if (args.length < 2 || !args[0].equalsIgnoreCase("add")) {
            sender.sendMessage(p != null
                    ? i18n.tr(p, "commands.queue.usage")
                    : "§eUsage: /twitchqueue add <amount> [username]");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(p != null
                    ? i18n.tr(p, "commands.queue.invalid_amount", Map.of("input", args[1]))
                    : "§cInvalid amount: " + args[1]);
            return true;
        }

        String byUser = null;
        if (args.length >= 3) {
            byUser = args[2].trim();
            if (byUser.isEmpty()) byUser = null;
        }

        TwitchIntegrationManager tim = plugin.getTwitch();
        if (tim == null) {
            sender.sendMessage(p != null
                    ? i18n.tr(p, "commands.queue.twitch_unavailable")
                    : "§cTwitch integration not available.");
            return true;
        }

        tim.enqueueMultiple(amount, byUser);

        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("user", byUser != null ? byUser : "");
        ph.put("queue_size", String.valueOf(tim.getQueueSize()));

        String key = (byUser != null)
                ? "commands.queue.added_with_user"
                : "commands.queue.added";

        sender.sendMessage(p != null
                ? i18n.tr(p, key, ph)
                : "§aAdded to queue: " + amount
                  + (byUser != null ? (" × " + byUser) : "")
                  + ". Currently in queue: " + tim.getQueueSize());
        return true;
    }
}