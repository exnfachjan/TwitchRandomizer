package me.exnfachjan.twitchRandomizer.events;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class RandomEvents implements Listener {

    private final TwitchRandomizer plugin;
    private final Messages i18n;
    private final Map<UUID, Long> noCraftUntil = new HashMap<>();
    private final Map<UUID, UUID> hotPotatoMob = new HashMap<>();
    private final Map<UUID, BukkitTask> hotPotatoTask = new HashMap<>();
    private final Random rng = new Random();

    // Für Boden-Events
    private final Map<UUID, BukkitTask> groundTasks = new HashMap<>();
    private final Set<UUID> slipperyActive = new HashSet<>();
    private final Set<UUID> lavaActive = new HashSet<>();
    private final Map<UUID, BossBar> eventBossbars = new HashMap<>();

    // Bossbar für NoCrafting
    private final Map<UUID, BossBar> noCraftBossbars = new HashMap<>();
    private final Map<UUID, BukkitTask> noCraftTasks = new HashMap<>();

    public RandomEvents(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getMessages();
    }

    public boolean isLavaActive(Player p) {
        return lavaActive.contains(p.getUniqueId());
    }
    public boolean isSlipperyActive(Player p) {
        return slipperyActive.contains(p.getUniqueId());
    }
    public boolean isAnyGroundEventActive(Player p) {
        return isLavaActive(p) || isSlipperyActive(p);
    }
    public boolean isNoCraftingActive(Player p) {
        Long until = noCraftUntil.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    // ===== No Crafting Events (mit Bossbar) =====

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        e.getInventory().setResult(null);
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        e.setCancelled(true);
        e.getInventory().setResult(null);
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top == InventoryType.WORKBENCH || top == InventoryType.CRAFTING) {
            if (e.getSlotType() == InventoryType.SlotType.RESULT ||
                    e.getSlotType() == InventoryType.SlotType.CRAFTING) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top != InventoryType.WORKBENCH && top != InventoryType.CRAFTING) return;
        for (int rawSlot : e.getRawSlots()) {
            org.bukkit.inventory.InventoryView view = e.getView();
            InventoryType.SlotType slotType = view.getSlotType(rawSlot);
            if (slotType == InventoryType.SlotType.RESULT ||
                    slotType == InventoryType.SlotType.CRAFTING) {
                e.setCancelled(true);
                break;
            }
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.WORKBENCH || t == InventoryType.CRAFTING) {
            e.setCancelled(true);
            p.sendMessage(i18n.tr(p, "events.no_crafting.blocked"));
        }
    }

    public void triggerNoCrafting(Player p, String byUser) {
        int seconds = 30 + rng.nextInt(91);
        noCraftUntil.put(p.getUniqueId(), System.currentTimeMillis() + (seconds * 1000));

        // Bossbar anzeigen
        showNoCraftBossbar(p, seconds);

        // Eventuell alten Task abbrechen
        BukkitTask oldTask = noCraftTasks.remove(p.getUniqueId());
        if (oldTask != null) oldTask.cancel();

        // Task für Bossbar-Update und Ablauf
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!isNoCraftingActive(p) || remaining <= 0) {
                    hideNoCraftBossbar(p);
                    BukkitTask t = noCraftTasks.remove(p.getUniqueId());
                    if (t != null) t.cancel();
                    return;
                }
                updateNoCraftBossbar(p, remaining, seconds);
                remaining--;
            }
        }, 0L, 20L);

        noCraftTasks.put(p.getUniqueId(), task);

        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);

        String key = (byUser != null && !byUser.isBlank()) ? "events.no_crafting.start_by" : "events.no_crafting.start";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    private void showNoCraftBossbar(Player p, int totalSeconds) {
        hideNoCraftBossbar(p);
        String title = i18n.tr(p, "bossbar.no_crafting") + " \u2013 " + totalSeconds + "s";
        BossBar bar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.addPlayer(p);
        noCraftBossbars.put(p.getUniqueId(), bar);
    }

    private void updateNoCraftBossbar(Player p, int secondsLeft, int total) {
        BossBar bar = noCraftBossbars.get(p.getUniqueId());
        if (bar != null) {
            String title = i18n.tr(p, "bossbar.no_crafting") + " \u2013 " + secondsLeft + "s";
            bar.setTitle(title);
            bar.setProgress(Math.max(0.0, Math.min(1.0, (double) secondsLeft / (double) total)));
        }
    }

    private void hideNoCraftBossbar(Player p) {
        BossBar bar = noCraftBossbars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    // ====== Events ======

    public void triggerSpawnMobs(Player p, String byUser) {
        List<EntityType> allMobTypes = Arrays.asList(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
                EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME,
                EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.ENDERMITE, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN,
                EntityType.SHULKER, EntityType.VEX, EntityType.VINDICATOR, EntityType.EVOKER,
                EntityType.RAVAGER, EntityType.PILLAGER, EntityType.PHANTOM,
                EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN,
                EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE,
                EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA,
                EntityType.WOLF, EntityType.CAT, EntityType.OCELOT, EntityType.RABBIT, EntityType.VILLAGER,
                EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.SQUID, EntityType.GLOW_SQUID,
                EntityType.BAT, EntityType.MOOSHROOM, EntityType.POLAR_BEAR, EntityType.PARROT,
                EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD, EntityType.SALMON,
                EntityType.PUFFERFISH, EntityType.TROPICAL_FISH, EntityType.PANDA, EntityType.FOX,
                EntityType.BEE, EntityType.STRIDER, EntityType.AXOLOTL, EntityType.GOAT,
                EntityType.ALLAY, EntityType.FROG
        );
        List<EntityType> availableMobs = allMobTypes.stream()
                .filter(type -> type != null && type.isSpawnable())
                .collect(Collectors.toList());
        if (availableMobs.isEmpty()) {
            availableMobs = Arrays.asList(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.COW, EntityType.PIG);
        }
        EntityType selectedType = availableMobs.get(rng.nextInt(availableMobs.size()));
        int amount = 1 + rng.nextInt(5);
        for (int i = 0; i < amount; i++) {
            double offsetX = rng.nextDouble() * 4 - 2;
            double offsetZ = rng.nextDouble() * 4 - 2;
            p.getWorld().spawnEntity(p.getLocation().add(offsetX, 0, offsetZ), selectedType);
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount));
        ph.put("entity", pretty(selectedType.name()));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.spawn.by" : "events.spawn.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerGiveItem(Player p, String byUser) {
        List<Material> mats = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(mat -> mat != Material.AIR)
                .collect(Collectors.toList());

        Material mat = mats.get(rng.nextInt(mats.size()));

        int amount = 1 + rng.nextInt(5);
        p.getInventory().addItem(new ItemStack(mat, amount));

        Map<String, String> ph = new HashMap<>();
        ph.put("item", pretty(mat.name()));
        ph.put("amount", String.valueOf(amount));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);

        String key = (byUser != null && !byUser.isBlank()) ? "events.give.item.by" : "events.give.item.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerClearInventory(Player p, String byUser) {
        PlayerInventory inv = p.getInventory();
        List<Integer> allSlots = new ArrayList<>();
        // Main inventory: 0-35, Armor: 36-39, Offhand: 40
        for (int i = 0; i <= 40; i++) allSlots.add(i);
        Collections.shuffle(allSlots, rng);

        // Mindestens 1, maximal alle Slots
        int slotsToClear = 1 + rng.nextInt(allSlots.size());
        for (int i = 0; i < slotsToClear; i++) {
            int slot = allSlots.get(i);
            inv.setItem(slot, null);
        }
        p.updateInventory();

        Map<String, String> ph = new HashMap<>();
        ph.put("count", String.valueOf(slotsToClear));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.inventory.cleared.by" : "events.inventory.cleared.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerTeleport(Player p, String byUser) {
        Random rng = new Random();
        // 0,001% Wahrscheinlichkeit für Advanced End
        if (rng.nextInt(100_000) == 0) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null) {
                int x = 1000 + rng.nextInt(500);
                int z = 1000 + rng.nextInt(500);
                int y = 70; // Sichere Y-Koordinate fürs End (kannst du anpassen)
                setAirCube(endWorld, x, y, z);
                Location tpLoc = new Location(endWorld, x + 0.5, y, z + 0.5);
                p.teleport(tpLoc);
                Map<String, String> ph = new HashMap<>();
                ph.put("x", String.valueOf(x));
                ph.put("y", String.valueOf(y));
                ph.put("z", String.valueOf(z));
                if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
                String key = (byUser != null && !byUser.isBlank()) ? "events.teleport.advanced_end.by" : "events.teleport.advanced_end.solo";
                p.sendMessage(i18n.tr(p, key, ph));
                return;
            }
            // Falls kein End, normaler Teleport unten
        }

        World w = p.getWorld();
        int x = rng.nextInt(3000) - 1500;
        int z = rng.nextInt(3000) - 1500;
        int y = p.getLocation().getBlockY(); // Immer exakt das aktuelle Y-Level!

        // 3x3x3 Air Cube erstellen
        setAirCube(w, x, y, z);
        Location tpLoc = new Location(w, x + 0.5, y, z + 0.5);
        p.teleport(tpLoc);
        Map<String, String> ph = new HashMap<>();
        ph.put("x", String.valueOf(x));
        ph.put("y", String.valueOf(y));
        ph.put("z", String.valueOf(z));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.teleport.random.by" : "events.teleport.random.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    // Hilfsfunktion: Macht einen 3x3x3 Air Cube um die Zielkoordinate
    private void setAirCube(World w, int centerX, int centerY, int centerZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int bx = centerX + dx;
                    int by = centerY + dy;
                    int bz = centerZ + dz;
                    if (by < w.getMinHeight() || by > w.getMaxHeight()) continue;
                    w.getBlockAt(bx, by, bz).setType(org.bukkit.Material.AIR, false);
                }
            }
        }
    }

    public void triggerDamageHalfHeart(Player p, String byUser) {
        double hp = p.getHealth();
        if (hp > 1.0) p.setHealth(Math.max(1.0, hp - 1.0));
        Map<String, String> ph = new HashMap<>();
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.damage.half_heart.by" : "events.damage.half_heart.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerFire(Player p, String byUser) {
        int seconds = 2 + rng.nextInt(6);
        p.setFireTicks(seconds * 20);
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.fire.by" : "events.fire.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerInvShuffle(Player p, String byUser) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] items = inv.getContents();
        List<ItemStack> itemList = new ArrayList<>(Arrays.asList(items));
        Collections.shuffle(itemList, rng);
        inv.setContents(itemList.toArray(new ItemStack[0]));
        p.updateInventory(); // <-- Inventar-Update für Client!
        Map<String, String> ph = new HashMap<>();
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.inv_shuffle.by" : "events.inv_shuffle.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerSafeCreepers(Player p, String byUser) {
        int seconds = 60 + rng.nextInt(61);
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.safe_creepers.explode_by" : "events.safe_creepers.explode";
        p.sendMessage(i18n.tr(p, key, ph));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as @a at @s run particle minecraft:explosion_emitter ~1 ~ ~");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as @a at @s run playsound minecraft:entity.generic.explode master @s ~1 ~ ~ 1 1 0");
    }

    // ==== Floor is Lava (mit Boden-Restore) ====
    public void triggerFloorIsLava(Player p, String byUser) {
        if (isAnyGroundEventActive(p)) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        int min = Math.max(10, cfg.getInt("events.settings.floor_is_lava.min_seconds", 10));
        int max = Math.max(min, cfg.getInt("events.settings.floor_is_lava.max_seconds", 180));
        int seconds = randomBetween(min, max);
        lavaActive.add(p.getUniqueId());
        showEventBossbar(p, "floor_is_lava", seconds);
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.floor_is_lava.start_by" : "events.floor_is_lava.start";
        p.sendMessage(i18n.tr(p, key, ph));
        Map<Block, Material> replaced = new HashMap<>();
        int ticksPerRun = 5;
        int totalRuns = seconds * 20 / ticksPerRun;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int runsLeft = totalRuns;
            boolean hasEnded = false;
            @Override
            public void run() {
                if (runsLeft % (20 / ticksPerRun) == 0) {
                    int sekundenRest = runsLeft / (20 / ticksPerRun);
                    updateEventBossbar(p, sekundenRest, seconds, "floor_is_lava");
                }
                Location loc = p.getLocation();
                Block block = loc.getBlock().getRelative(0, -1, 0);
                if (block.getType().isSolid() && block.getType() != Material.MAGMA_BLOCK
                        && block.getType() != Material.AIR && !block.isLiquid()) {
                    if (!replaced.containsKey(block)) {
                        replaced.put(block, block.getType());
                        block.setType(Material.MAGMA_BLOCK);
                    }
                }
                runsLeft--;
                if (runsLeft <= 0 && !hasEnded) {
                    hasEnded = true;
                    cancelGroundEvent(p, "lava");
                    for (Map.Entry<Block, Material> entry : replaced.entrySet()) {
                        Block b = entry.getKey();
                        if (b.getType() == Material.MAGMA_BLOCK) {
                            b.setType(entry.getValue());
                        }
                    }
                    replaced.clear();
                    p.sendMessage(i18n.tr(p, "events.floor_is_lava.end"));
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BukkitTask t = groundTasks.remove(p.getUniqueId());
                        if (t != null) t.cancel();
                    });
                }
            }
        }, 0L, ticksPerRun);
        groundTasks.put(p.getUniqueId(), task);
    }

    // ==== Slippery Ground ====
    public void triggerSlipperyGround(Player p, String byUser) {
        if (isAnyGroundEventActive(p)) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        int min = Math.max(10, cfg.getInt("events.settings.slippery_ground.min_seconds", 10));
        int max = Math.max(min, cfg.getInt("events.settings.slippery_ground.max_seconds", 180));
        int seconds = randomBetween(min, max);
        slipperyActive.add(p.getUniqueId());
        showEventBossbar(p, "slippery_ground", seconds);
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.slippery_ground.start_by" : "events.slippery_ground.start";
        p.sendMessage(i18n.tr(p, key, ph));
        Map<Block, Material> replaced = new HashMap<>();
        int ticksPerRun = 5;
        int totalRuns = seconds * 20 / ticksPerRun;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int runsLeft = totalRuns;
            boolean hasEnded = false;
            @Override
            public void run() {
                if (runsLeft % (20 / ticksPerRun) == 0) {
                    int sekundenRest = runsLeft / (20 / ticksPerRun);
                    updateEventBossbar(p, sekundenRest, seconds, "slippery_ground");
                }
                Location loc = p.getLocation();
                int cx = loc.getBlockX();
                int cy = loc.getBlockY() - 1;
                int cz = loc.getBlockZ();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = p.getWorld().getBlockAt(cx + dx, cy, cz + dz);
                        if (b.getType().isSolid() && b.getType() != Material.PACKED_ICE
                                && b.getType() != Material.AIR && !b.isLiquid()) {
                            if (!replaced.containsKey(b)) {
                                replaced.put(b, b.getType());
                                b.setType(Material.PACKED_ICE);
                            }
                        }
                    }
                }
                runsLeft--;
                if (runsLeft <= 0 && !hasEnded) {
                    hasEnded = true;
                    cancelGroundEvent(p, "ice");
                    for (Map.Entry<Block, Material> entry : replaced.entrySet()) {
                        Block b = entry.getKey();
                        if (b.getType() == Material.PACKED_ICE) {
                            b.setType(entry.getValue());
                        }
                    }
                    replaced.clear();
                    p.sendMessage(i18n.tr(p, "events.slippery_ground.end"));
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BukkitTask t = groundTasks.remove(p.getUniqueId());
                        if (t != null) t.cancel();
                    });
                }
            }
        }, 0L, ticksPerRun);
        groundTasks.put(p.getUniqueId(), task);
    }

    public void triggerHellIsCalling(Player p, String byUser) {
        int count = 1 + rng.nextInt(5);
        Location base = p.getLocation();
        World w = p.getWorld();
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = base.clone().add(rng.nextDouble() * 4 - 2, 16 + rng.nextInt(12), rng.nextDouble() * 4 - 2);
                org.bukkit.entity.Fireball ball = (org.bukkit.entity.Fireball) w.spawnEntity(spawn, EntityType.FIREBALL);
                ball.setDirection(new org.bukkit.util.Vector(0, -1, 0));
                ball.setYield(2.5F);
                ball.setIsIncendiary(true);
            }, i * 10L);
        }
        Map<String, String> ph = new HashMap<>();
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.hell_is_calling.by" : "events.hell_is_calling.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerNasaCall(Player p, String byUser) {
        p.setVelocity(p.getVelocity().setY(5.5));
        Map<String, String> ph = new HashMap<>();
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.nasa_call.by" : "events.nasa_call.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    public void triggerHotPotato(Player p, String byUser) {
        FileConfiguration cfg = plugin.getConfig();
        int duration = Math.max(3, cfg.getInt("events.settings.hot_potato.duration_seconds", 10));
        float explosionPower = 4.0f;
        endHotPotato(p);
        Zombie z = p.getWorld().spawn(p.getLocation(), Zombie.class, spawned -> {
            spawned.setBaby(true);
            spawned.setTarget(p);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            spawned.getEquipment().setDropChance(EquipmentSlot.HEAD, 0f);
            spawned.setCustomName("Heiße Kartoffel");
            spawned.setCustomNameVisible(true);
            spawned.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * duration + 100, 9, false, false, true));
        });
        hotPotatoMob.put(p.getUniqueId(), z.getUniqueId());
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(duration));
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        String key = (byUser != null && !byUser.isBlank()) ? "events.hot_potato.start_by" : "events.hot_potato.start";
        p.sendMessage(i18n.tr(p, key, ph));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID zu = hotPotatoMob.remove(p.getUniqueId());
            if (zu != null) {
                Entity e = Bukkit.getEntity(zu);
                if (e != null && e.isValid()) {
                    Location loc = e.getLocation();
                    loc.getWorld().createExplosion(loc, explosionPower, false, false, p);
                    e.remove();
                }
            }
            p.sendMessage(i18n.tr(p, "events.hot_potato.end"));
        }, duration * 20L);
        hotPotatoTask.put(p.getUniqueId(), task);
    }
    public void endHotPotato(Player p) {
        UUID pu = p.getUniqueId();
        BukkitTask t = hotPotatoTask.remove(pu);
        if (t != null) t.cancel();
        UUID zu = hotPotatoMob.remove(pu);
        if (zu != null) {
            Entity e = Bukkit.getEntity(zu);
            if (e != null) e.remove();
        }
    }

    public void triggerPotion(Player p, String byUser) {
        PotionEffectType[] effects = {
                PotionEffectType.REGENERATION,
                PotionEffectType.SATURATION,
                PotionEffectType.WITHER,
                PotionEffectType.DARKNESS,
                PotionEffectType.SPEED,
                PotionEffectType.SLOWNESS,
                PotionEffectType.POISON,
                PotionEffectType.BLINDNESS,
                PotionEffectType.RAID_OMEN,
                PotionEffectType.HERO_OF_THE_VILLAGE,
                PotionEffectType.ABSORPTION,
                PotionEffectType.JUMP_BOOST
        };
        PotionEffectType effectType = effects[rng.nextInt(effects.length)];
        int minSeconds = 10;
        int maxSeconds = 120;
        int durationSec = minSeconds + rng.nextInt(maxSeconds - minSeconds + 1);
        int durationTicks = durationSec * 20;
        int amplifier = rng.nextInt(2);
        p.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier));
        Map<String, String> ph = new HashMap<>();
        if (byUser != null && !byUser.isBlank()) ph.put("user", byUser);
        ph.put("effect", pretty(effectType.getName()));
        ph.put("seconds", String.valueOf(durationSec));
        String key = (byUser != null && !byUser.isBlank()) ? "events.potion.applied.by" : "events.potion.applied.solo";
        p.sendMessage(i18n.tr(p, key, ph));
    }

    // === Bossbar & Boden-Event-Helpers ===
    private void showEventBossbar(Player p, String eventKey, int totalSeconds) {
        cancelEventBossbar(p);
        String title = i18n.tr(p, "bossbar." + eventKey) + " \u2013 " + totalSeconds + "s";
        BossBar bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.addPlayer(p);
        eventBossbars.put(p.getUniqueId(), bar);
    }
    private void updateEventBossbar(Player p, int secondsLeft, int total, String eventKey) {
        BossBar bar = eventBossbars.get(p.getUniqueId());
        if (bar != null) {
            String title = i18n.tr(p, "bossbar." + eventKey) + " \u2013 " + secondsLeft + "s";
            bar.setTitle(title);
            bar.setProgress(Math.max(0.0, Math.min(1.0, (double) secondsLeft / (double) total)));
        }
    }
    private void cancelEventBossbar(Player p) {
        BossBar bar = eventBossbars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
    private void cancelGroundEvent(Player p, String type) {
        BukkitTask t = groundTasks.remove(p.getUniqueId());
        if (t != null) t.cancel();
        if (type.equals("lava")) {
            lavaActive.remove(p.getUniqueId());
        } else if (type.equals("ice")) {
            slipperyActive.remove(p.getUniqueId());
        }
        cancelEventBossbar(p);
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return min;
        return min + rng.nextInt(max - min + 1);
    }
    private String pretty(String enumOrKey) {
        String s = enumOrKey.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            if (i + 1 < parts.length) sb.append(' ');
        }
        return sb.toString();
    }
}