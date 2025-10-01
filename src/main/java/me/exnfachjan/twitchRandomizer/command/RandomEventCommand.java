package me.exnfachjan.twitchRandomizer.command;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.events.RandomEvents;
import org.bukkit.Bukkit;
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
            "fake_totem"
    );

    private int[] weights;
    private Integer lastIndex = null;

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

    public int[] getWeights() {
        return this.weights;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage("No players online – skipping event.");
            return true;
        }

        int[] weightsForPick = Arrays.copyOf(this.weights, this.weights.length);

        // Dynamische Blockaden (z.B. Boden-Events/Crafting)
        boolean anyGroundActive = false;
        boolean noCraftingActive = false;
        for (Player player : players) {
            if (events.isAnyGroundEventActive(player)) anyGroundActive = true;
            if (events.isNoCraftingActive(player)) noCraftingActive = true;
        }
        if (anyGroundActive) {
            weightsForPick[11] = 0; // floor_is_lava
            weightsForPick[13] = 0; // slippery_ground
        }
        if (noCraftingActive) {
            weightsForPick[9] = 0; // no_crafting
        }

        // --- ECHTE gewichtete Zufallsauswahl ---
        int totalWeight = 0;
        for (int w : weightsForPick) totalWeight += w;
        if (totalWeight <= 0) {
            sender.sendMessage("Keine aktiven Events verfügbar!");
            return true;
        }
        int r = rng.nextInt(totalWeight);
        int acc = 0;
        int event = -1;
        for (int i = 0; i < weightsForPick.length; i++) {
            acc += weightsForPick[i];
            if (r < acc) {
                event = i;
                break;
            }
        }
        if (event == -1) {
            sender.sendMessage("Fehler: Konnte kein Event auswählen!");
            return true;
        }
        this.lastIndex = event;

        for (Player player : players) {
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
                case 15 -> events.triggerTntRain(player, byUser);
                case 16 -> events.triggerAnvilRain(player, byUser);
                case 17 -> events.triggerSkyblock(player, byUser);
                case 18 -> events.triggerFakeTotem(player, byUser);
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
            plugin.getLogger().warning("Keine positiven Event-Gewichte gefunden – Events sind deaktiviert.");
        }
        return w;
    }
}