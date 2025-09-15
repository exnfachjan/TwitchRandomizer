package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import me.exnfachjan.twitchRandomizer.events.RandomEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.bukkit.block.Block;

import java.util.*;
import java.util.stream.Collectors;

public class RandomEventCommand implements CommandExecutor {

    private final TwitchRandomizer plugin;
    private final Random rng = new Random();
    private final RandomEvents events;

    // Öffentlich, damit GUI/Weights darauf zugreifen können
    public static final List<String> EVENT_KEYS_ORDER = List.of(
            "spawn_mobs",
            "potion",
            "give_item",
            "clear_inventory",
            "teleport",
            "damage_half_heart",
            "fire",
            "inv_shuffle",
            "hot_potato",
            "no_crafting",
            "safe_creepers",
            "floor_is_lava",
            "nasa_call",
            "slippery_ground",
            "hell_is_calling"
    );

    private int[] weights;
    private Integer lastIndex = null;

    public RandomEventCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.events = new RandomEvents(plugin);
        // Listener für Craft/Explosion etc.
        plugin.getServer().getPluginManager().registerEvents(this.events, plugin);
        this.weights = loadWeightsFromConfig();
    }

    public void reloadWeights() {
        this.weights = loadWeightsFromConfig();
        plugin.getLogger().info("RandomEvent-Gewichte neu geladen.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // "by:<name>" oder "<name>"
        String byUser = null;
        if (args.length >= 1) {
            String first = args[0] != null ? args[0].trim() : "";
            if (!first.isEmpty()) {
                if (first.regionMatches(true, 0, "by:", 0, 3)) {
                    first = first.substring(3).trim();
                }
                if (!first.isEmpty()) {
                    byUser = first;
                }
            }
        }

        List<Player> players = Bukkit.getOnlinePlayers().stream().collect(Collectors.toList());
        if (players.isEmpty()) {
            sender.sendMessage("No players online – skipping event.");
            return true;
        }

        int event = pickWeightedIndex(this.weights, this.lastIndex);
        this.lastIndex = event;

        for (Player player : players) {
            // Alle Events werden jetzt in RandomEvents behandelt, immer mit Twitch-Username-Parameter
            switch (event) {
                case 0 -> events.triggerSpawnMobs(player, byUser);
                case 1 -> events.triggerPotion(player, byUser);
                case 2 -> events.triggerGiveItem(player, byUser);
                case 3 -> events.triggerClearInventory(player, byUser);
                case 4 -> events.triggerTeleport(player, byUser);
                case 5 -> events.triggerDamageHalfHeart(player, byUser);
                case 6 -> events.triggerFire(player, byUser);
                case 7 -> events.triggerInvShuffle(player, byUser);
                case 8 -> events.triggerHotPotato(player, byUser);
                case 9 -> events.triggerNoCrafting(player, byUser);
                case 10 -> events.triggerSafeCreepers(player, byUser);
                case 11 -> events.triggerFloorIsLava(player, byUser);
                case 12 -> events.triggerNasaCall(player, byUser);
                case 13 -> events.triggerSlipperyGround(player, byUser);
                case 14 -> events.triggerHellIsCalling(player, byUser);
            }
        }
        return true;
    }

    private int[] loadWeightsFromConfig() {
        int[] w = new int[EVENT_KEYS_ORDER.size()];
        for (int i = 0; i < EVENT_KEYS_ORDER.size(); i++) {
            String key = EVENT_KEYS_ORDER.get(i);
            int val = Math.max(0, plugin.getConfig().getInt("events.weights." + key, 0));
            w[i] = val;
        }
        int sum = Arrays.stream(w).sum();
        if (sum <= 0) {
            plugin.getLogger().warning("Keine positiven Event-Gewichte gefunden – setze Standardgewicht 1 für alle.");
            Arrays.fill(w, 1);
        }
        return w;
    }

    private int pickWeightedIndex(int[] weights, Integer avoidIndex) {
        long total = 0L;
        final int antiDupingDelta = 1;
        for (int i = 0; i < weights.length; i++) {
            int w = weights[i];
            if (avoidIndex != null && i == avoidIndex) w = Math.max(0, w - antiDupingDelta);
            total += w;
        }
        if (total <= 0) total = Arrays.stream(weights).sum();
        long r = nextLongBounded(total);
        long acc = 0L;
        for (int i = 0; i < weights.length; i++) {
            int w = weights[i];
            if (avoidIndex != null && i == avoidIndex) w = Math.max(0, w - antiDupingDelta);
            acc += w;
            if (r < acc) return i;
        }
        return weights.length - 1;
    }

    private long nextLongBounded(long bound) {
        long r = rng.nextLong();
        long m = bound - 1;
        if ((bound & m) == 0L) return r & m;
        long u = r >>> 1;
        while (u + m - (u % bound) < 0L) {
            r = rng.nextLong();
            u = r >>> 1;
        }
        return u % bound;
    }
}