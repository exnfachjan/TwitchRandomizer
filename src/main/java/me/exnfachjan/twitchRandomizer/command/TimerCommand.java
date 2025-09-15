package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.timer.TimerManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class TimerCommand implements CommandExecutor {

    private final TimerManager timer;

    public TimerCommand(TimerManager timer) {
        this.timer = timer;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /timer <start|stop|resume|reset>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                timer.start();
                sender.sendMessage(ChatColor.GREEN + "Timer gestartet.");
            }
            case "stop" -> {
                timer.stop();
                sender.sendMessage(ChatColor.GREEN + "Timer pausiert.");
            }
            case "resume" -> {
                timer.resume();
                sender.sendMessage(ChatColor.GREEN + "Timer fortgesetzt.");
            }
            case "reset" -> {
                timer.reset();
                sender.sendMessage(ChatColor.GREEN + "Timer zurückgesetzt.");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /timer <start|stop|resume|reset>");
        }
        return true;
    }
}