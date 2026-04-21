package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.events.RandomEvents;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RandomEventCommand implements CommandExecutor {

    private final TwitchRandomizer plugin;
    private final Random rng = new Random();
    public final RandomEvents events;

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
            "hell_is_calling",
            "tnt_rain",
            "anvil_rain",
            "skyblock",
            "fake_totem",
            "equipment_shuffle",
            "permanent_hearts"
    );

    private int[] weights;
    private Integer lastIndex = null;

    private static final int RECENT_WINDOW  = 3;
    private static final double PENALTY_FACTOR = 0.2;
    private final ArrayDeque<Integer> recentEvents = new ArrayDeque<>(RECENT_WINDOW);

    public RandomEventCommand(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.events = new RandomEvents(plugin);
        plugin.getServer().getPluginManager().registerEvents(this.events, plugin);
        this.weights = loadWeightsFromConfig();
    }

    public void reloadWeights() {
        this.weights = loadWeightsFromConfig();
        plugin.getLogger().info("RandomEvent-Gewichte neu geladen.");
    }

    public int[] getWeights() { return this.weights; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String byUser = null;
        if (args.length >= 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(args[i]);
            }
            String first = sb.toString().trim();
            if (!first.isEmpty()) {
                if (first.regionMatches(true, 0, "by:", 0, 3)) first = first.substring(3).trim();
                if (!first.isEmpty()) byUser = parseAndColorize(first);
            }
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage("No players online – skipping event.");
            return true;
        }

        int[] weightsForPick = Arrays.copyOf(weights, weights.length);
        boolean anyGroundActive = false, noCraftingActive = false;
        for (Player player : players) {
            if (events.isAnyGroundEventActive(player)) anyGroundActive = true;
            if (events.isNoCraftingActive(player)) noCraftingActive = true;
        }
        if (anyGroundActive) { weightsForPick[11] = 0; weightsForPick[13] = 0; }
        if (noCraftingActive) { weightsForPick[9] = 0; }

        int penaltyPos = 0;
        for (int recentIdx : recentEvents) {
            if (weightsForPick[recentIdx] > 0) {
                double penalty = PENALTY_FACTOR * (1.0 - (penaltyPos * 0.1));
                penalty = Math.max(0.05, penalty);
                weightsForPick[recentIdx] = Math.max(1, (int)(weightsForPick[recentIdx] * penalty));
            }
            penaltyPos++;
        }

        int totalWeight = Arrays.stream(weightsForPick).sum();
        if (totalWeight <= 0) {
            sender.sendMessage("No events available (all weights 0 or blocked).");
            return true;
        }

        int r = rng.nextInt(totalWeight);
        int acc = 0, event = -1;
        for (int i = 0; i < weightsForPick.length; i++) {
            acc += weightsForPick[i];
            if (r < acc) { event = i; break; }
        }
        if (event == -1) {
            sender.sendMessage("Fehler: Konnte kein Event auswählen!");
            return true;
        }
        this.lastIndex = event;

        recentEvents.addFirst(event);
        while (recentEvents.size() > RECENT_WINDOW) recentEvents.removeLast();

        long syncSeed = rng.nextLong();
        org.bukkit.Location skyblockMeetingPoint = players.isEmpty() ? null : players.get(0).getLocation().clone();

        for (Player player : players) {
            switch (event) {
                case 0  -> events.triggerSpawnMobs(player, byUser, syncSeed);
                case 1  -> events.triggerPotion(player, byUser, syncSeed);
                case 2  -> events.triggerGiveItem(player, byUser, syncSeed);
                case 3  -> events.triggerClearInventory(player, byUser, syncSeed);
                case 4  -> events.triggerTeleport(player, byUser, syncSeed);
                case 5  -> events.triggerDamageHalfHeart(player, byUser, syncSeed);
                case 6  -> events.triggerFire(player, byUser, syncSeed);
                case 7  -> events.triggerInvShuffle(player, byUser, syncSeed);
                case 8  -> events.triggerHotPotato(player, byUser, syncSeed);
                case 9  -> events.triggerNoCrafting(player, byUser, syncSeed);
                case 10 -> events.triggerSafeCreepers(player, byUser, syncSeed);
                case 11 -> events.triggerFloorIsLava(player, byUser, syncSeed);
                case 12 -> events.triggerNasaCall(player, byUser);
                case 13 -> events.triggerSlipperyGround(player, byUser, syncSeed);
                case 14 -> events.triggerHellIsCalling(player, byUser, syncSeed);
                case 15 -> events.triggerTntRain(player, byUser);
                case 16 -> events.triggerAnvilRain(player, byUser);
                case 17 -> events.triggerSkyblock(player, byUser, skyblockMeetingPoint);
                case 18 -> events.triggerFakeTotem(player, byUser);
                case 19 -> events.triggerEquipmentShuffle(player, byUser, syncSeed);
                case 20 -> events.triggerPermanentHearts(player, byUser, syncSeed);
            }
        }
        return true;
    }

    private String parseAndColorize(String input) {
        if (input.startsWith("role:")) {
            String withoutPrefix = input.substring(5);
            int colonIdx = withoutPrefix.indexOf(':');
            if (colonIdx > 0 && colonIdx < withoutPrefix.length() - 1) {
                String role = withoutPrefix.substring(0, colonIdx).toLowerCase(java.util.Locale.ROOT);
                String name = withoutPrefix.substring(colonIdx + 1);
                String color = switch (role) {
                    case "broadcaster" -> ChatColor.RED.toString();
                    case "moderator"   -> ChatColor.GREEN.toString();
                    case "vip"         -> ChatColor.LIGHT_PURPLE.toString();
                    case "donation"    -> ChatColor.AQUA.toString();
                    default            -> ChatColor.WHITE.toString();
                };
                return color + name + ChatColor.RESET;
            }
        }
        return input;
    }

    private int[] loadWeightsFromConfig() {
        int[] w = new int[EVENT_KEYS_ORDER.size()];
        for (int i = 0; i < EVENT_KEYS_ORDER.size(); i++) {
            String key = EVENT_KEYS_ORDER.get(i);
            int val = Math.max(0, plugin.getConfig().getInt("events.weights." + key, 0));
            w[i] = val;
        }
        return w;
    }
}