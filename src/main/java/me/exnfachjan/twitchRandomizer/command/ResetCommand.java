package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.reset.ResetManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ResetCommand implements CommandExecutor {

    private final TwitchRandomizer plugin;

    public ResetCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        String sub = (args.length >= 1) ? args[0].toLowerCase(Locale.ROOT) : "";

        // Force: darf immer (mit spezieller Permission), unabhängig von Pending-Dialog
        if ("force".equals(sub)) {
            if (sender instanceof Player p && !p.hasPermission("twitchrandomizer.reset.force")) {
                p.sendMessage("§cKeine Berechtigung: twitchrandomizer.reset.force");
                return true;
            }
            runReset(sender);
            return true;
        }

        // Confirm: nur wenn ein Pending-Dialog aktiv ist
        if ("confirm".equals(sub)) {
            if (!plugin.hasPendingResetConfirmation()) {
                if (sender instanceof Player p) {
                    p.sendMessage(plugin.getMessages().tr(p, "reset.confirm_no_pending",
                            Map.of("command0", "reset")));
                } else {
                    sender.sendMessage("No reset dialog active. Start with /reset");
                }
                return true;
            }
            // OK – ausführen und Pending löschen
            plugin.clearResetConfirmation();
            if (sender instanceof Player p) {
                p.sendMessage(plugin.getMessages().tr(p, "reset.confirm_ok"));
            } else {
                sender.sendMessage("Executing reset...");
            }
            runReset(sender);
            return true;
        }

        // Kein Subcommand: Starte Bestätigungsdialog
        int window = Math.max(1, plugin.getConfig().getInt("reset.confirm_window_seconds", 30));
        String requester = (sender instanceof Player p) ? p.getName() : "Console";

        if (plugin.hasPendingResetConfirmation()) {
            // Bereits aktiv – Hinweis mit Restzeit
            int left = plugin.secondsLeftForResetConfirmation();
            if (sender instanceof Player p) {
                p.sendMessage(plugin.getMessages().tr(p, "reset.confirm_already_pending",
                        Map.of("requester", String.valueOf(plugin.getResetConfirmRequester()),
                                "command", "reset confirm",
                                "seconds", String.valueOf(left))));
            } else {
                sender.sendMessage("A reset dialog is already pending. Confirm with /reset confirm (" + left + "s left).");
            }
            return true;
        }

        plugin.startResetConfirmation(requester, window);
        if (sender instanceof Player p) {
            p.sendMessage(plugin.getMessages().tr(p, "reset.confirm_prompt",
                    Map.of("command", "reset confirm",
                            "seconds", String.valueOf(window))));
        } else {
            sender.sendMessage("Please confirm within " + window + "s using /reset confirm");
        }
        return true;
    }

    private void runReset(CommandSender sender) {
        long seed = ThreadLocalRandom.current().nextLong();
        try {
            ResetManager reset = plugin.getResetManager();
            reset.prepareWorldReset(sender, seed);
        } catch (Throwable t) {
            plugin.getLogger().severe("Reset failed: " + t.getMessage());
            t.printStackTrace();
            sender.sendMessage("§cAn unexpected error occurred while executing /reset. See console for details.");
        }
    }
}