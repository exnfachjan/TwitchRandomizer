package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.death.DeathCounterManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ClearDeathCommand implements CommandExecutor {

    private final DeathCounterManager counter;

    public ClearDeathCommand(DeathCounterManager counter) {
        this.counter = counter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        counter.clear();
        counter.broadcastActionbar();
        sender.sendMessage(ChatColor.GREEN + "Deathcounter wurde auf 0 zurückgesetzt.");
        return true;
    }
}