package me.exnfachjan.twitchRandomizer.events;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
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
    private final Set<UUID> skyblockLocked = new HashSet<>();
    private final Set<String> skyblockChunksCleaned = new HashSet<>();
    private final Map<UUID, BukkitTask> groundTasks = new HashMap<>();
    private final Set<UUID> slipperyActive = new HashSet<>();
    private final Set<UUID> lavaActive = new HashSet<>();
    private final Map<UUID, BossBar> eventBossbars = new HashMap<>();
    private final Map<UUID, BossBar> noCraftBossbars = new HashMap<>();
    private final Map<UUID, BukkitTask> noCraftTasks = new HashMap<>();
    private final Map<UUID, BossBar> playerSizeBossbars = new HashMap<>();
    private final Map<UUID, BukkitTask> playerSizeTasks = new HashMap<>();
    private final Map<UUID, Integer> hungerMaxCache = new HashMap<>();

    public RandomEvents(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getMessages();
    }

    public boolean isLavaActive(Player p)           { return lavaActive.contains(p.getUniqueId()); }
    public boolean isSlipperyActive(Player p)       { return slipperyActive.contains(p.getUniqueId()); }
    public boolean isAnyGroundEventActive(Player p) { return isLavaActive(p) || isSlipperyActive(p); }
    public boolean isNoCraftingActive(Player p) {
        Long until = noCraftUntil.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NoCrafting inventory handlers
    // ─────────────────────────────────────────────────────────────────────────

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
        e.setCancelled(true); e.getInventory().setResult(null);
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top == InventoryType.WORKBENCH || top == InventoryType.CRAFTING) {
            if (e.getSlotType()==InventoryType.SlotType.RESULT || e.getSlotType()==InventoryType.SlotType.CRAFTING) { e.setCancelled(true); return; }
            if (e.isShiftClick()) e.setCancelled(true);
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top != InventoryType.WORKBENCH && top != InventoryType.CRAFTING) return;
        for (int rawSlot : e.getRawSlots()) {
            InventoryType.SlotType slotType = e.getView().getSlotType(rawSlot);
            if (slotType==InventoryType.SlotType.RESULT || slotType==InventoryType.SlotType.CRAFTING) { e.setCancelled(true); break; }
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isNoCraftingActive(p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.WORKBENCH || t == InventoryType.CRAFTING) { e.setCancelled(true); p.sendMessage(i18n.tr(p, "events.no_crafting.blocked")); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PermanentHearts: beim Join wiederherstellen
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Restore permanent hearts
        org.bukkit.NamespacedKey heartKey = new org.bukkit.NamespacedKey(plugin, "perm_hearts_delta");
        try {
            Double delta = p.getPersistentDataContainer().get(heartKey, PersistentDataType.DOUBLE);
            if (delta != null && delta != 0.0) {
                org.bukkit.attribute.AttributeInstance attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) attr.setBaseValue(Math.max(2.0, 20.0 + delta));
            }
        } catch (Throwable ignored) {}
        // Restore hunger max cap
        org.bukkit.NamespacedKey hungerKey = new org.bukkit.NamespacedKey(plugin, "hunger_max_delta");
        try {
            Integer hungerDelta = p.getPersistentDataContainer().get(hungerKey, PersistentDataType.INTEGER);
            if (hungerDelta != null && hungerDelta != 0) {
                int max = Math.max(2, Math.min(20, 20 + hungerDelta));
                hungerMaxCache.put(p.getUniqueId(), max);
                if (p.getFoodLevel() > max) p.setFoodLevel(max);
            }
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync-Seed Helper
    // ─────────────────────────────────────────────────────────────────────────

    private Random seededRng(long seed) { return new Random(seed); }

    // ─── SpawnMobs ────────────────────────────────────────────────────────────
    public void triggerSpawnMobs(Player p, String byUser) { triggerSpawnMobs(p, byUser, rng.nextLong()); }
    public void triggerSpawnMobs(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        List<EntityType> allMobTypes = Arrays.asList(
            EntityType.ZOMBIE,EntityType.SKELETON,EntityType.CREEPER,EntityType.SPIDER,EntityType.CAVE_SPIDER,
            EntityType.ENDERMAN,EntityType.WITCH,EntityType.SLIME,EntityType.MAGMA_CUBE,EntityType.BLAZE,
            EntityType.GHAST,EntityType.WITHER_SKELETON,EntityType.ZOMBIFIED_PIGLIN,EntityType.ENDERMITE,
            EntityType.GUARDIAN,EntityType.ELDER_GUARDIAN,EntityType.SHULKER,EntityType.VEX,EntityType.VINDICATOR,
            EntityType.EVOKER,EntityType.RAVAGER,EntityType.PILLAGER,EntityType.PHANTOM,EntityType.DROWNED,
            EntityType.HUSK,EntityType.STRAY,EntityType.PIGLIN,EntityType.HOGLIN,EntityType.ZOGLIN,
            EntityType.BEE,EntityType.PANDA,EntityType.WOLF,EntityType.IRON_GOLEM,EntityType.LLAMA
        );
        EntityType selectedType = allMobTypes.get(r.nextInt(allMobTypes.size()));
        int amount = 1 + r.nextInt(5);
        for (int i = 0; i < amount; i++) {
            Location spawnLoc = p.getLocation().clone().add(r.nextInt(5)-2, 0, r.nextInt(5)-2);
            Entity entity = p.getWorld().spawnEntity(spawnLoc, selectedType);
            if (entity instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(p);
                if (isHostileMob(selectedType)) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20*60*60, 1, true, false, true));
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           20*60*60, 1, true, false, true));
                    new BukkitRunnable() { @Override public void run() { if(mob.isDead()||!p.isOnline()||p.isDead()){cancel();return;} if(mob.getTarget()==null||!mob.getTarget().equals(p))mob.setTarget(p); } }.runTaskTimer(plugin, 20L, 20L);
                }
            }
            if (entity instanceof Wolf wolf)     { wolf.setAngry(true); wolf.setTarget(p); }
            if (entity instanceof Bee bee)        { bee.setAnger(999999); bee.setTarget(p); }
            if (entity instanceof Panda panda)    { panda.setAggressive(true); }
            if (entity instanceof IronGolem golem){ golem.setPlayerCreated(false); golem.setTarget(p); }
            if (entity instanceof Llama llama)    { llama.setTarget(p); }
        }
        Map<String,String> ph = new HashMap<>();
        ph.put("amount", String.valueOf(amount)); ph.put("entity", pretty(selectedType.name()));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p, (byUser!=null&&!byUser.isBlank())?"events.spawn.by":"events.spawn.solo", ph));
    }

    private boolean isHostileMob(EntityType type) {
        return switch (type) {
            case ZOMBIE,SKELETON,CREEPER,SPIDER,CAVE_SPIDER,ENDERMAN,WITCH,SLIME,MAGMA_CUBE,BLAZE,GHAST,WITHER_SKELETON,
                 ZOMBIFIED_PIGLIN,ENDERMITE,GUARDIAN,ELDER_GUARDIAN,SHULKER,VEX,VINDICATOR,EVOKER,RAVAGER,PILLAGER,
                 PHANTOM,DROWNED,HUSK,STRAY,PIGLIN,PIGLIN_BRUTE,HOGLIN,ZOGLIN -> true;
            default -> false;
        };
    }

    // ─── Potion ───────────────────────────────────────────────────────────────
    public void triggerPotion(Player p, String byUser) { triggerPotion(p, byUser, rng.nextLong()); }
    public void triggerPotion(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        PotionEffectType[] effects = { PotionEffectType.REGENERATION, PotionEffectType.SATURATION, PotionEffectType.WITHER, PotionEffectType.DARKNESS, PotionEffectType.SPEED, PotionEffectType.SLOWNESS, PotionEffectType.POISON, PotionEffectType.BLINDNESS, PotionEffectType.RAID_OMEN, PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.ABSORPTION, PotionEffectType.JUMP_BOOST };
        PotionEffectType effectType = effects[r.nextInt(effects.length)];
        int durationSec = 10 + r.nextInt(111), amplifier = r.nextInt(2);
        p.addPotionEffect(new PotionEffect(effectType, durationSec*20, amplifier));
        Map<String,String> ph = new HashMap<>();
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        ph.put("effect", pretty(effectType.getName())); ph.put("seconds", String.valueOf(durationSec));
        p.sendMessage(i18n.tr(p, (byUser!=null&&!byUser.isBlank())?"events.potion.applied.by":"events.potion.applied.solo", ph));
    }

    // ─── GiveItem ─────────────────────────────────────────────────────────────
    public void triggerGiveItem(Player p, String byUser) { triggerGiveItem(p, byUser, rng.nextLong()); }
    public void triggerGiveItem(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        List<Material> mats = Arrays.stream(Material.values()).filter(Material::isItem).filter(m->m!=Material.AIR).collect(Collectors.toList());
        Material mat = mats.get(r.nextInt(mats.size())); int amount = 1 + r.nextInt(5);
        p.getInventory().addItem(new ItemStack(mat, amount));
        Map<String,String> ph = new HashMap<>();
        ph.put("item", pretty(mat.name())); ph.put("amount", String.valueOf(amount));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p, (byUser!=null&&!byUser.isBlank())?"events.give.item.by":"events.give.item.solo", ph));
    }

    // ─── ClearInventory ───────────────────────────────────────────────────────
    public void triggerClearInventory(Player p, String byUser) { triggerClearInventory(p, byUser, rng.nextLong()); }
    public void triggerClearInventory(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        PlayerInventory inv = p.getInventory();
        List<Integer> allSlots = new ArrayList<>(); for (int i=0;i<=40;i++) allSlots.add(i);
        Collections.shuffle(allSlots, r); int slotsToClear = 1 + r.nextInt(allSlots.size());
        for (int i=0;i<slotsToClear;i++) inv.setItem(allSlots.get(i), null);
        p.updateInventory();
        Map<String,String> ph = new HashMap<>(); ph.put("count", String.valueOf(slotsToClear));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p, (byUser!=null&&!byUser.isBlank())?"events.inventory.cleared.by":"events.inventory.cleared.solo", ph));
    }

    // ─── Teleport ─────────────────────────────────────────────────────────────
    public void triggerTeleport(Player p, String byUser) { triggerTeleport(p, byUser, rng.nextLong()); }
    public void triggerTeleport(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        if (r.nextInt(100_000) == 0) {
            World endWorld = Bukkit.getWorld("world_the_end");
            if (endWorld != null) {
                int x=1000+r.nextInt(500), z=1000+r.nextInt(500), y=70; setAirCube(endWorld,x,y,z); p.teleport(new Location(endWorld,x+0.5,y,z+0.5));
                Map<String,String> ph=new HashMap<>(); ph.put("x",String.valueOf(x));ph.put("y",String.valueOf(y));ph.put("z",String.valueOf(z));if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
                p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.teleport.advanced_end.by":"events.teleport.advanced_end.solo",ph)); return;
            }
        }
        World w = p.getWorld(); int x=r.nextInt(3000)-1500, z=r.nextInt(3000)-1500, y=findSafeY(w,x,z);
        setAirCube(w,x,y,z); p.teleport(new Location(w,x+0.5,y,z+0.5));
        Map<String,String> ph=new HashMap<>(); ph.put("x",String.valueOf(x));ph.put("y",String.valueOf(y));ph.put("z",String.valueOf(z));if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.teleport.random.by":"events.teleport.random.solo",ph));
    }

    // ─── DamageHalfHeart ──────────────────────────────────────────────────────
    public void triggerDamageHalfHeart(Player p, String byUser) { triggerDamageHalfHeart(p, byUser, rng.nextLong()); }
    public void triggerDamageHalfHeart(Player p, String byUser, long seed) {
        Random r = seededRng(seed); int hearts = 3+r.nextInt(6); double hp = p.getHealth();
        if (hp>1.0) p.setHealth(Math.max(1.0, hp-hearts*2.0));
        Map<String,String> ph=new HashMap<>(); ph.put("hearts",String.valueOf(hearts)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.damage.half_heart.by":"events.damage.half_heart.solo",ph));
    }

    // ─── Fire ─────────────────────────────────────────────────────────────────
    public void triggerFire(Player p, String byUser) { triggerFire(p, byUser, rng.nextLong()); }
    public void triggerFire(Player p, String byUser, long seed) {
        Random r = seededRng(seed); int seconds = 2+r.nextInt(6); p.setFireTicks(seconds*20);
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(seconds)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.fire.by":"events.fire.solo",ph));
    }

    // ─── InvShuffle ───────────────────────────────────────────────────────────
    public void triggerInvShuffle(Player p, String byUser) { triggerInvShuffle(p, byUser, rng.nextLong()); }
    public void triggerInvShuffle(Player p, String byUser, long seed) {
        Random r = seededRng(seed); PlayerInventory inv = p.getInventory();
        // Use getStorageContents() (main 36 slots only) to avoid corrupting armor/offhand slots
        ItemStack[] storage = inv.getStorageContents();
        List<ItemStack> itemList = new ArrayList<>(Arrays.asList(storage));
        Collections.shuffle(itemList, r);
        inv.setStorageContents(itemList.toArray(new ItemStack[0]));
        p.updateInventory();
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.inv_shuffle.by":"events.inv_shuffle.solo",ph));
    }

    // ─── HotPotato – 3× HP ───────────────────────────────────────────────────
    public void triggerHotPotato(Player p, String byUser) { triggerHotPotato(p, byUser, rng.nextLong()); }
    public void triggerHotPotato(Player p, String byUser, long seed) {
        FileConfiguration cfg = plugin.getConfig();
        int duration = Math.max(3, cfg.getInt("events.settings.hot_potato.duration_seconds", 10));
        float explosionPower = 4.0f;
        endHotPotato(p);
        Zombie z = p.getWorld().spawn(p.getLocation(), Zombie.class, spawned -> {
            spawned.setBaby(true); spawned.setTarget(p); spawned.setPersistent(true); spawned.setRemoveWhenFarAway(false);
            // 3× HP (Baby Zombie = 20 HP → 60 HP)
            try {
                org.bukkit.attribute.AttributeInstance attr = spawned.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) { attr.setBaseValue(60.0); spawned.setHealth(60.0); }
            } catch (Throwable ignored) {}
            spawned.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            spawned.getEquipment().setDropChance(EquipmentSlot.HEAD, 0f);
            spawned.setCustomName("Heiße Kartoffel"); spawned.setCustomNameVisible(true);
            spawned.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      20*duration+100, 9, false, false, true));
            spawned.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20*duration+100, 2, false, false, true));
        });
        hotPotatoMob.put(p.getUniqueId(), z.getUniqueId());
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(duration)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.hot_potato.start_by":"events.hot_potato.start",ph));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID zu = hotPotatoMob.remove(p.getUniqueId());
            if (zu!=null) { Entity e=Bukkit.getEntity(zu); if(e!=null&&e.isValid()){Location loc=e.getLocation();loc.getWorld().createExplosion(loc,explosionPower,false,false,p);e.remove();} }
            p.sendMessage(i18n.tr(p, "events.hot_potato.end"));
        }, duration*20L);
        hotPotatoTask.put(p.getUniqueId(), task);
    }

    public void endHotPotato(Player p) {
        UUID pu=p.getUniqueId(); BukkitTask t=hotPotatoTask.remove(pu); if(t!=null)t.cancel();
        UUID zu=hotPotatoMob.remove(pu); if(zu!=null){Entity e=Bukkit.getEntity(zu);if(e!=null)e.remove();}
    }

    // ─── NoCrafting ───────────────────────────────────────────────────────────
    public void triggerNoCrafting(Player p, String byUser) { triggerNoCrafting(p, byUser, rng.nextLong()); }
    public void triggerNoCrafting(Player p, String byUser, long seed) {
        Random r = seededRng(seed); FileConfiguration cfg = plugin.getConfig();
        int min=Math.max(5,cfg.getInt("events.settings.no_crafting.min_seconds",5));
        int max=Math.max(min,cfg.getInt("events.settings.no_crafting.max_seconds",15));
        int seconds=min+r.nextInt(max-min+1);
        noCraftUntil.put(p.getUniqueId(), System.currentTimeMillis()+(seconds*1000L));
        showNoCraftBossbar(p, seconds);
        BukkitTask oldTask=noCraftTasks.remove(p.getUniqueId()); if(oldTask!=null)oldTask.cancel();
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining=seconds;
            @Override public void run() { if(!isNoCraftingActive(p)||remaining<=0){hideNoCraftBossbar(p);BukkitTask t2=noCraftTasks.remove(p.getUniqueId());if(t2!=null)t2.cancel();return;} updateNoCraftBossbar(p,remaining,seconds);remaining--; }
        }, 0L, 20L);
        noCraftTasks.put(p.getUniqueId(), task);
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(seconds)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.no_crafting.start_by":"events.no_crafting.start",ph));
    }

    // ─── SafeCreepers ─────────────────────────────────────────────────────────
    public void triggerSafeCreepers(Player p, String byUser) { triggerSafeCreepers(p, byUser, rng.nextLong()); }
    public void triggerSafeCreepers(Player p, String byUser, long seed) {
        FileConfiguration cfg=plugin.getConfig();
        int count=Math.max(1,cfg.getInt("events.settings.safe_creepers.count",3));
        int radius=Math.max(1,cfg.getInt("events.settings.safe_creepers.radius",2));
        int lifetimeSec=Math.max(1,cfg.getInt("events.settings.safe_creepers.lifetime_seconds",8));
        boolean powered=cfg.getBoolean("events.settings.safe_creepers.powered",true);
        List<Creeper> spawnedCreepers=new ArrayList<>();
        NamespacedKey safeKey=new NamespacedKey(plugin,"safe_creeper");
        for (int i=0;i<count;i++) {
            double angle=(2*Math.PI/count)*i;
            Location spawnLoc=p.getLocation().clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            Creeper creeper=p.getWorld().spawn(spawnLoc, Creeper.class, c->{if(powered)c.setPowered(true);c.setMaxFuseTicks(0);c.setFuseTicks(0);c.getPersistentDataContainer().set(safeKey,PersistentDataType.BYTE,(byte)1);});
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,    lifetimeSec*20+40, 4,  false,false,false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,  lifetimeSec*20+40, -4, false,false,false));
            spawnedCreepers.add(creeper);
        }
        Bukkit.getScheduler().runTaskLater(plugin, ()->{for(Creeper c:spawnedCreepers){if(c.isValid()&&!c.isDead())c.explode();}p.removePotionEffect(PotionEffectType.SLOWNESS);p.removePotionEffect(PotionEffectType.JUMP_BOOST);Bukkit.getScheduler().runTask(plugin,()->p.setVelocity(new org.bukkit.util.Vector(0,0,0)));},30L);
        Bukkit.getScheduler().runTaskLater(plugin, ()->{for(Creeper c:spawnedCreepers){if(c.isValid()&&!c.isDead())c.remove();}p.removePotionEffect(PotionEffectType.SLOWNESS);p.removePotionEffect(PotionEffectType.JUMP_BOOST);},100L);
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.safe_creepers.explode_by":"events.safe_creepers.explode",ph));
    }

    @EventHandler public void onSafeCreeperExplode(EntityExplodeEvent event) {
        if(!(event.getEntity() instanceof Creeper creeper))return;
        if(!creeper.getPersistentDataContainer().has(new NamespacedKey(plugin,"safe_creeper"),PersistentDataType.BYTE))return;
        event.blockList().clear();
    }
    @EventHandler public void onSafeCreeperDamage(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Creeper creeper))return;
        if(!creeper.getPersistentDataContainer().has(new NamespacedKey(plugin,"safe_creeper"),PersistentDataType.BYTE))return;
        event.setCancelled(true);
        if(event.getEntity() instanceof Player victim) Bukkit.getScheduler().runTask(plugin,()->victim.setVelocity(new org.bukkit.util.Vector(0,victim.getVelocity().getY()>0?0:victim.getVelocity().getY(),0)));
    }

    // ─── FloorIsLava ──────────────────────────────────────────────────────────
    public void triggerFloorIsLava(Player p, String byUser) { triggerFloorIsLava(p, byUser, rng.nextLong()); }
    public void triggerFloorIsLava(Player p, String byUser, long seed) {
        if (isAnyGroundEventActive(p)) return;
        Random r=seededRng(seed); FileConfiguration cfg=plugin.getConfig();
        int min=Math.max(10,cfg.getInt("events.settings.floor_is_lava.min_seconds",10));
        int max=Math.max(min,cfg.getInt("events.settings.floor_is_lava.max_seconds",180));
        int seconds=min+r.nextInt(max-min+1);
        lavaActive.add(p.getUniqueId()); showEventBossbar(p,"floor_is_lava",seconds);
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(seconds)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.floor_is_lava.start_by":"events.floor_is_lava.start",ph));
        Map<Block,Material> replaced=new HashMap<>(); int ticksPerRun=5, totalRuns=seconds*20/ticksPerRun;
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int runsLeft=totalRuns; boolean hasEnded=false;
            @Override public void run() {
                if(runsLeft%(20/ticksPerRun)==0)updateEventBossbar(p,runsLeft/(20/ticksPerRun),seconds,"floor_is_lava");
                Block block=p.getLocation().getBlock().getRelative(0,-1,0);
                if(block.getType().isSolid()&&block.getType()!=Material.MAGMA_BLOCK&&block.getType()!=Material.AIR&&!block.isLiquid()) if(!replaced.containsKey(block)){replaced.put(block,block.getType());block.setType(Material.MAGMA_BLOCK);}
                runsLeft--;
                if(runsLeft<=0&&!hasEnded){hasEnded=true;cancelGroundEvent(p,"lava");for(Map.Entry<Block,Material> entry:replaced.entrySet())if(entry.getKey().getType()==Material.MAGMA_BLOCK)entry.getKey().setType(entry.getValue());replaced.clear();p.sendMessage(i18n.tr(p,"events.floor_is_lava.end"));Bukkit.getScheduler().runTask(plugin,()->{BukkitTask t2=groundTasks.remove(p.getUniqueId());if(t2!=null)t2.cancel();});}
            }
        }, 0L, ticksPerRun);
        groundTasks.put(p.getUniqueId(), task);
    }

    // ─── NasaCall – sauber durch Blöcke starten ──────────────────────────────
    public void triggerNasaCall(Player p, String byUser) {
        Location loc = p.getLocation();
        World world = loc.getWorld();
        int headY = loc.getBlockY() + 2; // 2 blocks above feet = above head
        int maxY = world.getMaxHeight() - 2;
        // Find first 2 consecutive non-solid blocks above the player's head
        int launchY = -1;
        for (int y = headY; y <= maxY; y++) {
            if (!world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType().isSolid()
                    && !world.getBlockAt(loc.getBlockX(), y + 1, loc.getBlockZ()).getType().isSolid()) {
                launchY = y;
                break;
            }
        }
        if (launchY > headY) {
            // Teleport player feet to launchY so head is in clear air
            Location launch = loc.clone();
            launch.setY(launchY);
            p.teleport(launch);
        }
        p.setVelocity(p.getVelocity().setY(5.5));
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.nasa_call.by":"events.nasa_call.solo",ph));
    }

    // ─── SlipperyGround ───────────────────────────────────────────────────────
    public void triggerSlipperyGround(Player p, String byUser) { triggerSlipperyGround(p, byUser, rng.nextLong()); }
    public void triggerSlipperyGround(Player p, String byUser, long seed) {
        if (isAnyGroundEventActive(p)) return;
        Random r=seededRng(seed); FileConfiguration cfg=plugin.getConfig();
        int min=Math.max(10,cfg.getInt("events.settings.slippery_ground.min_seconds",10));
        int max=Math.max(min,cfg.getInt("events.settings.slippery_ground.max_seconds",180));
        int seconds=min+r.nextInt(max-min+1);
        slipperyActive.add(p.getUniqueId()); showEventBossbar(p,"slippery_ground",seconds);
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(seconds)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.slippery_ground.start_by":"events.slippery_ground.start",ph));
        Map<Block,Material> replaced=new HashMap<>(); int ticksPerRun=5, totalRuns=seconds*20/ticksPerRun;
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int runsLeft=totalRuns; boolean hasEnded=false;
            @Override public void run() {
                if(runsLeft%(20/ticksPerRun)==0)updateEventBossbar(p,runsLeft/(20/ticksPerRun),seconds,"slippery_ground");
                Location loc=p.getLocation(); int cx=loc.getBlockX(),cy=loc.getBlockY()-1,cz=loc.getBlockZ();
                for(int dx=-1;dx<=1;dx++) for(int dz2=-1;dz2<=1;dz2++){Block b=p.getWorld().getBlockAt(cx+dx,cy,cz+dz2);if(b.getType().isSolid()&&b.getType()!=Material.PACKED_ICE&&b.getType()!=Material.AIR&&!b.isLiquid())if(!replaced.containsKey(b)){replaced.put(b,b.getType());b.setType(Material.PACKED_ICE);}}
                runsLeft--;
                if(runsLeft<=0&&!hasEnded){hasEnded=true;cancelGroundEvent(p,"ice");for(Map.Entry<Block,Material> entry:replaced.entrySet())if(entry.getKey().getType()==Material.PACKED_ICE)entry.getKey().setType(entry.getValue());replaced.clear();p.sendMessage(i18n.tr(p,"events.slippery_ground.end"));Bukkit.getScheduler().runTask(plugin,()->{BukkitTask t2=groundTasks.remove(p.getUniqueId());if(t2!=null)t2.cancel();});}
            }
        }, 0L, ticksPerRun);
        groundTasks.put(p.getUniqueId(), task);
    }

    // ─── HellIsCalling ────────────────────────────────────────────────────────
    public void triggerHellIsCalling(Player p, String byUser) { triggerHellIsCalling(p, byUser, rng.nextLong()); }
    public void triggerHellIsCalling(Player p, String byUser, long seed) {
        Random r=seededRng(seed); int count=1+r.nextInt(5); Location base=p.getLocation(); World w=p.getWorld();
        for(int i=0;i<count;i++) Bukkit.getScheduler().runTaskLater(plugin,()->{Location spawn=base.clone().add(r.nextDouble()*4-2,16+r.nextInt(12),r.nextDouble()*4-2);org.bukkit.entity.Fireball ball=(org.bukkit.entity.Fireball)w.spawnEntity(spawn,EntityType.FIREBALL);ball.setDirection(new org.bukkit.util.Vector(r.nextDouble()*0.2-0.1,-1,r.nextDouble()*0.2-0.1));ball.setYield(2.5f);ball.setIsIncendiary(true);},(long)(i*10));
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.hell_is_calling.by":"events.hell_is_calling.solo",ph));
    }

    // ─── TntRain (mit 5-Sekunden-Countdown) ───────────────────────────────────
    public void triggerTntRain(Player p, String byUser) {
        int duration=plugin.getConfig().getInt("events.settings.tnt_rain.duration_seconds",30);
        int radius=plugin.getConfig().getInt("events.settings.tnt_rain.radius",25);
        int intervalTicks=plugin.getConfig().getInt("events.settings.tnt_rain.interval_ticks",6);
        World world=p.getWorld(); int totalTicks=duration*20;
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(duration)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.tnt_rain.by":"events.tnt_rain.solo",ph));
        // 5-Sekunden-Countdown als Title-Display
        String subtitle = i18n.tr(p, "events.tnt_rain.countdown_subtitle");
        for (int i = 5; i >= 1; i--) {
            final int count = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.sendTitle("§c§l" + count, "§e" + subtitle, 3, 14, 3);
            }, (5 - i) * 20L);
        }
        // Regen startet nach 5 Sekunden
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendTitle("§c§l☠", "§e" + subtitle, 5, 15, 10);
            new BukkitRunnable(){int ticksRun=0;@Override public void run(){if(!p.isOnline()||p.isDead()){cancel();return;}int tntCount=8+rng.nextInt(5);Location playerLoc=p.getLocation();for(int i=0;i<tntCount;i++){double dx=rng.nextDouble()*radius*2-radius,dz=rng.nextDouble()*radius*2-radius;int ySpawn=Math.min(playerLoc.getWorld().getMaxHeight()-2,playerLoc.getBlockY()+3+rng.nextInt(5));world.spawnEntity(playerLoc.clone().add(dx,ySpawn-playerLoc.getY(),dz),EntityType.TNT_MINECART);}ticksRun+=intervalTicks;if(ticksRun>=totalTicks)cancel();}}.runTaskTimer(plugin,0L,intervalTicks);
        }, 5 * 20L);
    }

    // ─── AnvilRain ────────────────────────────────────────────────────────────
    public void triggerAnvilRain(Player p, String byUser) {
        int duration=plugin.getConfig().getInt("events.settings.anvil_rain.duration_seconds",30);
        int radius=plugin.getConfig().getInt("events.settings.anvil_rain.radius",25);
        int intervalTicks=plugin.getConfig().getInt("events.settings.anvil_rain.interval_ticks",6);
        World world=p.getWorld(); int totalTicks=duration*20;
        new BukkitRunnable(){int ticksRun=0;@Override public void run(){if(!p.isOnline()||p.isDead()){cancel();return;}int anvilCount=8+rng.nextInt(5);Location base=p.getLocation();for(int i=0;i<anvilCount;i++){double dx=rng.nextDouble()*radius*2-radius,dz=rng.nextDouble()*radius*2-radius;int ySpawn=Math.min(world.getMaxHeight()-2,base.getBlockY()+30+rng.nextInt(10));world.spawnFallingBlock(base.clone().add(dx,ySpawn-base.getY(),dz),Material.ANVIL.createBlockData());}ticksRun+=intervalTicks;if(ticksRun>=totalTicks)cancel();}}.runTaskTimer(plugin,0L,intervalTicks);
        Map<String,String> ph=new HashMap<>(); ph.put("seconds",String.valueOf(duration)); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.anvil_rain.by":"events.anvil_rain.solo",ph));
    }

    // ─── Skyblock ─────────────────────────────────────────────────────────────
    public void triggerSkyblock(Player p, String byUser) { triggerSkyblock(p, byUser, p.getLocation()); }
    public void triggerSkyblock(Player p, String byUser, long seed) { triggerSkyblock(p, byUser, p.getLocation()); }
    public void triggerSkyblock(Player p, String byUser, Location meetingPoint) {
        if (skyblockLocked.contains(p.getUniqueId())) return;
        skyblockLocked.add(p.getUniqueId());
        World world=meetingPoint.getWorld(); int radius=plugin.getConfig().getInt("events.settings.skyblock.radius",2); Chunk centerChunk=meetingPoint.getChunk();
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline()&&!p.isDead())p.teleport(meetingPoint);},1L);
        String chunkKey=world.getName()+":"+centerChunk.getX()+":"+centerChunk.getZ();
        if(skyblockChunksCleaned.contains(chunkKey)){sendSkyblockMessage(p,byUser);Bukkit.getScheduler().runTaskLater(plugin,()->skyblockLocked.remove(p.getUniqueId()),200L);return;}
        skyblockChunksCleaned.add(chunkKey);
        List<int[]> chunksToDelete=new ArrayList<>();
        for(int cx=centerChunk.getX()-radius;cx<=centerChunk.getX()+radius;cx++) for(int cz=centerChunk.getZ()-radius;cz<=centerChunk.getZ()+radius;cz++) if(cx!=centerChunk.getX()||cz!=centerChunk.getZ()) chunksToDelete.add(new int[]{cx,cz});
        new BukkitRunnable(){int index=0;@Override public void run(){int processed=0;while(index<chunksToDelete.size()&&processed<2){int[]coords=chunksToDelete.get(index++);Chunk targetChunk=world.getChunkAt(coords[0],coords[1]);if(!targetChunk.isLoaded())world.loadChunk(targetChunk);int minY=world.getMinHeight(),maxY=world.getMaxHeight();for(int x=0;x<16;x++)for(int z2=0;z2<16;z2++)for(int y=minY;y<maxY;y++)targetChunk.getBlock(x,y,z2).setType(Material.AIR,false);processed++;}if(index>=chunksToDelete.size()){skyblockChunksCleaned.remove(chunkKey);cancel();}}}.runTaskTimer(plugin,5L,1L);
        sendSkyblockMessage(p,byUser);
        Bukkit.getScheduler().runTaskLater(plugin,()->skyblockLocked.remove(p.getUniqueId()),200L);
    }
    private void sendSkyblockMessage(Player p, String byUser) {
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.skyblock.by":"events.skyblock.solo",ph));
    }

    // ─── FakeTotem – 50/50 ───────────────────────────────────────────────────
    public void triggerFakeTotem(Player p, String byUser) {
        boolean isReal = rng.nextBoolean(); // 50/50

        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = totem.getItemMeta();

        // Beide Totems: kursiver Name + 3 Padding-Tags → optisch identisch
        NamespacedKey keyFake = new NamespacedKey(plugin, "fake_totem");
        NamespacedKey keyPad1 = new NamespacedKey(plugin, "tr_pad_1");
        NamespacedKey keyPad2 = new NamespacedKey(plugin, "tr_pad_2");
        NamespacedKey keyPad3 = new NamespacedKey(plugin, "tr_pad_3");
        meta.getPersistentDataContainer().set(keyPad1, PersistentDataType.BYTE, (byte)0);
        meta.getPersistentDataContainer().set(keyPad2, PersistentDataType.BYTE, (byte)0);
        meta.getPersistentDataContainer().set(keyPad3, PersistentDataType.BYTE, (byte)0);
        meta.setDisplayName("§o" + i18n.tr(p, "item.minecraft.totem_of_undying"));

        if (!isReal) {
            // Fake: extra Tag → EntityResurrectEvent lässt es fehlschlagen
            meta.getPersistentDataContainer().set(keyFake, PersistentDataType.BYTE, (byte)1);
        }
        // Echtes Totem: kein fake_totem-Tag → EntityResurrectEvent läuft normal durch

        totem.setItemMeta(meta);
        p.getInventory().addItem(totem);
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"event.fake_totem.given_by":"event.fake_totem.given",ph));
    }

    @EventHandler public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        NamespacedKey key = new NamespacedKey(plugin, "fake_totem");
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (offhand!=null&&offhand.getType()==Material.TOTEM_OF_UNDYING) { ItemMeta meta=offhand.getItemMeta(); if(meta!=null&&meta.getPersistentDataContainer().has(key,PersistentDataType.BYTE)){event.setCancelled(true);p.getInventory().setItemInOffHand(null);p.sendMessage(i18n.tr(p,"event.fake_totem.fail"));return;} }
        ItemStack mainhand = p.getInventory().getItemInMainHand();
        if (mainhand!=null&&mainhand.getType()==Material.TOTEM_OF_UNDYING) { ItemMeta meta=mainhand.getItemMeta(); if(meta!=null&&meta.getPersistentDataContainer().has(key,PersistentDataType.BYTE)){event.setCancelled(true);p.getInventory().setItemInMainHand(null);p.sendMessage(i18n.tr(p,"event.fake_totem.fail"));} }
    }

    // ─── EquipmentShuffle ─────────────────────────────────────────────────────
    public void triggerEquipmentShuffle(Player p, String byUser) { triggerEquipmentShuffle(p, byUser, rng.nextLong()); }
    public void triggerEquipmentShuffle(Player p, String byUser, long seed) {
        Random r=seededRng(seed); PlayerInventory inv=p.getInventory(); ItemStack[] contents=inv.getContents();
        for(int i=0;i<contents.length;i++){ItemStack item=contents[i];if(item==null||item.getType()==Material.AIR)continue;for(Material[]tier:TOOL_TIERS){for(int j=0;j<tier.length;j++){if(tier[j]==item.getType()){int delta=r.nextBoolean()?1:-1,newJ=j+delta;if(newJ>=0&&newJ<tier.length){String oldName=pretty(item.getType().name()),newName=pretty(tier[newJ].name());ItemStack newItem=item.clone();newItem.setType(tier[newJ]);contents[i]=newItem;Map<String,String>ph=new HashMap<>();ph.put("item",oldName);ph.put("new_item",newName);p.sendMessage(i18n.tr(p,delta>0?"events.equipment_shuffle.upgrade":"events.equipment_shuffle.downgrade",ph));}break;}}}}
        inv.setContents(contents); p.updateInventory();
        Map<String,String> ph=new HashMap<>(); if(byUser!=null&&!byUser.isBlank())ph.put("user",byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.equipment_shuffle.by":"events.equipment_shuffle.solo",ph));
    }

    // ─── PermanentHearts ─────────────────────────────────────────────────────
    public void triggerPermanentHearts(Player p, String byUser) { triggerPermanentHearts(p, byUser, rng.nextLong()); }
    public void triggerPermanentHearts(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        int hearts  = 1 + r.nextInt(2);        // 1 oder 2 Herzen
        boolean gain = r.nextBoolean();          // gewinnen oder verlieren
        double delta  = (gain ? 1.0 : -1.0) * hearts * 2.0; // 1 Herz = 2 HP

        org.bukkit.attribute.AttributeInstance attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;

        double current = attr.getBaseValue();
        double newMax  = Math.max(2.0, current + delta);
        attr.setBaseValue(newMax);
        if (p.getHealth() > newMax) p.setHealth(newMax);

        // Persistenz via PersistentDataContainer
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "perm_hearts_delta");
        double stored = 0.0;
        try { Double val = p.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE); if (val!=null) stored=val; } catch (Throwable ignored) {}
        p.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, stored + delta);

        Map<String,String> ph = new HashMap<>();
        ph.put("hearts", String.valueOf(hearts));
        ph.put("total",  String.valueOf((int)(newMax / 2)));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p, (byUser!=null&&!byUser.isBlank()) ? "events.permanent_hearts.by" : "events.permanent_hearts.solo", ph));
        p.sendMessage(i18n.tr(p, gain ? "events.permanent_hearts.gain" : "events.permanent_hearts.loss", ph));
    }

    private volatile long lastStructureTeleportMs = 0L;

    // ─── StructureTeleport ────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private static final StructureType[][] WORLD_STRUCTURES = {
        // [0] Overworld
        { StructureType.VILLAGE, StructureType.DESERT_PYRAMID, StructureType.JUNGLE_PYRAMID,
          StructureType.SWAMP_HUT, StructureType.STRONGHOLD, StructureType.MINESHAFT,
          StructureType.OCEAN_MONUMENT, StructureType.WOODLAND_MANSION, StructureType.OCEAN_RUIN,
          StructureType.SHIPWRECK, StructureType.BURIED_TREASURE, StructureType.PILLAGER_OUTPOST },
        // [1] Nether
        { StructureType.NETHER_FORTRESS, StructureType.BASTION_REMNANT },
        // [2] End
        { StructureType.END_CITY }
    };
    private static final String[][] WORLD_STRUCTURE_NAMES = {
        { "Village", "Desert Pyramid", "Jungle Pyramid", "Swamp Hut", "Stronghold",
          "Mineshaft", "Ocean Monument", "Woodland Mansion", "Ocean Ruin",
          "Shipwreck", "Buried Treasure", "Pillager Outpost" },
        { "Nether Fortress", "Bastion Remnant" },
        { "End City" }
    };
    private static final String[] WORLD_NAMES = { "world", "world_nether", "world_the_end" };

    public void triggerStructureTeleport(Player p, String byUser) {
        // Dedup: called per player in the event loop, execute only once per trigger
        long now = System.currentTimeMillis();
        if (now - lastStructureTeleportMs < 3000L) return;
        lastStructureTeleportMs = now;
        Map<String,String> ph = new HashMap<>();
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.structure_teleport.by":"events.structure_teleport.solo",ph));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Build randomised list of (world, structureType, displayName) triples
            List<Object[]> options = new ArrayList<>();
            for (int i = 0; i < WORLD_NAMES.length; i++) {
                World world = Bukkit.getWorld(WORLD_NAMES[i]);
                if (world == null) continue;
                for (int j = 0; j < WORLD_STRUCTURES[i].length; j++)
                    options.add(new Object[]{world, WORLD_STRUCTURES[i][j], WORLD_STRUCTURE_NAMES[i][j]});
            }
            Collections.shuffle(options, rng);

            for (Object[] option : options) {
                World world = (World) option[0];
                @SuppressWarnings("deprecation") StructureType st = (StructureType) option[1];
                String structName = (String) option[2];
                Location origin = new Location(world, 0, 64, 0);
                @SuppressWarnings("deprecation")
                Location found = world.locateNearestStructure(origin, st, 200, false);
                if (found == null) continue;
                int safeY = findSafeY(world, found.getBlockX(), found.getBlockZ());
                Location dest = new Location(world, found.getBlockX() + 0.5, safeY, found.getBlockZ() + 0.5);
                for (Player online : Bukkit.getOnlinePlayers()) online.teleport(dest);
                Map<String,String> ph2 = new HashMap<>();
                ph2.put("structure", structName);
                ph2.put("world", pretty(world.getName()));
                ph2.put("x", String.valueOf(found.getBlockX()));
                ph2.put("z", String.valueOf(found.getBlockZ()));
                for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(i18n.tr(online,"events.structure_teleport.destination",ph2));
                return;
            }
            p.sendMessage(i18n.tr(p, "events.structure_teleport.not_found"));
        }, 1L);
    }

    // ─── HungerClubs (Hungerkeulen) ───────────────────────────────────────────
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        int max = getEffectiveHungerMax(p);
        if (max < 20 && e.getFoodLevel() > max) e.setFoodLevel(max);
    }

    private int getEffectiveHungerMax(Player p) {
        Integer cached = hungerMaxCache.get(p.getUniqueId());
        if (cached != null) return cached;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "hunger_max_delta");
        try {
            Integer delta = p.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
            int max = delta != null ? Math.max(2, Math.min(20, 20 + delta)) : 20;
            hungerMaxCache.put(p.getUniqueId(), max);
            return max;
        } catch (Throwable ignored) { return 20; }
    }

    public void triggerHungerClubs(Player p, String byUser) { triggerHungerClubs(p, byUser, rng.nextLong()); }
    public void triggerHungerClubs(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        int clubs = 1 + r.nextInt(2);           // 1 or 2 drumsticks
        boolean gain = r.nextBoolean();
        int delta = (gain ? 1 : -1) * clubs * 2; // 1 drumstick = 2 food points

        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "hunger_max_delta");
        int storedDelta = 0;
        try { Integer val = p.getPersistentDataContainer().get(key, PersistentDataType.INTEGER); if (val!=null) storedDelta=val; } catch (Throwable ignored) {}

        int newMax = Math.max(2, Math.min(20, 20 + storedDelta + delta));
        int newDelta = newMax - 20;
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, newDelta);
        hungerMaxCache.put(p.getUniqueId(), newMax);
        if (p.getFoodLevel() > newMax) p.setFoodLevel(newMax);

        Map<String,String> ph = new HashMap<>();
        ph.put("clubs", String.valueOf(clubs)); ph.put("total", String.valueOf(newMax / 2));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.hunger_clubs.by":"events.hunger_clubs.solo",ph));
        p.sendMessage(i18n.tr(p, gain?"events.hunger_clubs.gain":"events.hunger_clubs.loss", ph));
    }

    // ─── PlayerSize ───────────────────────────────────────────────────────────
    public void triggerPlayerSize(Player p, String byUser) { triggerPlayerSize(p, byUser, rng.nextLong()); }
    public void triggerPlayerSize(Player p, String byUser, long seed) {
        Random r = seededRng(seed);
        int seconds = 15 + r.nextInt(46); // 15–60 seconds
        boolean small = r.nextBoolean();
        double scale = small
            ? 0.3 + r.nextDouble() * 0.4   // 0.3 – 0.7
            : 1.8 + r.nextDouble() * 1.7;  // 1.8 – 3.5
        scale = Math.round(scale * 10.0) / 10.0;

        // Cancel any running size event for this player
        BukkitTask oldTask = playerSizeTasks.remove(p.getUniqueId());
        if (oldTask != null) oldTask.cancel();
        BossBar oldBar = playerSizeBossbars.remove(p.getUniqueId());
        if (oldBar != null) oldBar.removeAll();

        org.bukkit.attribute.AttributeInstance scaleAttr = p.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        final double originalScale = scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
        if (scaleAttr != null) scaleAttr.setBaseValue(Math.max(0.0625, Math.min(16.0, scale)));

        BarColor color = small ? BarColor.BLUE : BarColor.RED;
        String bossbarKey = small ? "bossbar.player_size_small" : "bossbar.player_size_large";
        BossBar bar = Bukkit.createBossBar(i18n.tr(p, bossbarKey) + " – " + seconds + "s", color, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0); bar.addPlayer(p);
        playerSizeBossbars.put(p.getUniqueId(), bar);

        Map<String,String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds)); ph.put("scale", String.format("%.1f", scale));
        if (byUser!=null&&!byUser.isBlank()) ph.put("user", byUser);
        p.sendMessage(i18n.tr(p,(byUser!=null&&!byUser.isBlank())?"events.player_size.by":"events.player_size.solo",ph));
        p.sendMessage(i18n.tr(p, small?"events.player_size.small":"events.player_size.large", ph));

        final int totalSec = seconds;
        BukkitTask task = new BukkitRunnable() {
            int remaining = totalSec;
            @Override public void run() {
                if (!p.isOnline() || remaining <= 0) {
                    org.bukkit.attribute.AttributeInstance a = p.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    if (a != null) a.setBaseValue(originalScale);
                    BossBar b = playerSizeBossbars.remove(p.getUniqueId());
                    if (b != null) b.removeAll();
                    playerSizeTasks.remove(p.getUniqueId());
                    if (p.isOnline()) p.sendMessage(i18n.tr(p, "events.player_size.end"));
                    cancel(); return;
                }
                BossBar b = playerSizeBossbars.get(p.getUniqueId());
                if (b != null) { b.setTitle(i18n.tr(p, bossbarKey) + " – " + remaining + "s"); b.setProgress(Math.max(0.0, Math.min(1.0, (double)remaining / totalSec))); }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        playerSizeTasks.put(p.getUniqueId(), task);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bossbar helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showEventBossbar(Player p, String eventKey, int totalSeconds) { cancelEventBossbar(p); BossBar bar=Bukkit.createBossBar(i18n.tr(p,"bossbar."+eventKey)+" – "+totalSeconds+"s",BarColor.RED,BarStyle.SEGMENTED_10); bar.setProgress(1.0);bar.addPlayer(p);eventBossbars.put(p.getUniqueId(),bar); }
    private void updateEventBossbar(Player p, int secondsLeft, int total, String eventKey) { BossBar bar=eventBossbars.get(p.getUniqueId());if(bar!=null){bar.setTitle(i18n.tr(p,"bossbar."+eventKey)+" – "+secondsLeft+"s");bar.setProgress(Math.max(0.0,Math.min(1.0,(double)secondsLeft/total)));} }
    private void cancelEventBossbar(Player p) { BossBar bar=eventBossbars.remove(p.getUniqueId());if(bar!=null)bar.removeAll(); }
    private void cancelGroundEvent(Player p, String type) { BukkitTask t=groundTasks.remove(p.getUniqueId());if(t!=null)t.cancel();if("lava".equals(type))lavaActive.remove(p.getUniqueId());else if("ice".equals(type))slipperyActive.remove(p.getUniqueId());cancelEventBossbar(p); }
    private void showNoCraftBossbar(Player p, int totalSec) { hideNoCraftBossbar(p); BossBar bar=Bukkit.createBossBar(i18n.tr(p,"bossbar.no_crafting")+" – "+totalSec+"s",BarColor.YELLOW,BarStyle.SEGMENTED_10);bar.setProgress(1.0);bar.addPlayer(p);noCraftBossbars.put(p.getUniqueId(),bar); }
    private void updateNoCraftBossbar(Player p, int secondsLeft, int total) { BossBar bar=noCraftBossbars.get(p.getUniqueId());if(bar!=null){bar.setTitle(i18n.tr(p,"bossbar.no_crafting")+" – "+secondsLeft+"s");bar.setProgress(Math.max(0.0,Math.min(1.0,(double)secondsLeft/total)));} }
    private void hideNoCraftBossbar(Player p) { BossBar bar=noCraftBossbars.remove(p.getUniqueId());if(bar!=null)bar.removeAll(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int findSafeY(World w, int x, int z) {
        int maxY=w.getMaxHeight()-1,minY=w.getMinHeight();
        for(int y=maxY;y>minY;y--) if(w.getBlockAt(x,y,z).getType().isSolid()&&!w.getBlockAt(x,y+1,z).getType().isSolid())return y+1;
        return Math.max(minY+1,64);
    }
    private void setAirCube(World w, int cx, int cy, int cz) {
        for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++){int by=cy+dy;if(by<w.getMinHeight()||by>w.getMaxHeight())continue;w.getBlockAt(cx+dx,by,cz+dz).setType(Material.AIR,false);}
    }
    private String pretty(String enumOrKey) {
        String[] parts=enumOrKey.toLowerCase(java.util.Locale.ROOT).replace('_',' ').split(" ");
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<parts.length;i++){if(parts[i].isEmpty())continue;sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));if(i+1<parts.length)sb.append(' ');}
        return sb.toString();
    }

    private static final Material[][] TOOL_TIERS = {
        {Material.WOODEN_SWORD,  Material.STONE_SWORD,  Material.IRON_SWORD,  Material.DIAMOND_SWORD,  Material.NETHERITE_SWORD},
        {Material.WOODEN_PICKAXE,Material.STONE_PICKAXE,Material.IRON_PICKAXE,Material.DIAMOND_PICKAXE,Material.NETHERITE_PICKAXE},
        {Material.WOODEN_AXE,   Material.STONE_AXE,    Material.IRON_AXE,    Material.DIAMOND_AXE,    Material.NETHERITE_AXE},
        {Material.WOODEN_SHOVEL,Material.STONE_SHOVEL, Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL},
        {Material.WOODEN_HOE,   Material.STONE_HOE,    Material.IRON_HOE,    Material.DIAMOND_HOE,    Material.NETHERITE_HOE},
        {Material.LEATHER_HELMET,    Material.CHAINMAIL_HELMET,    Material.IRON_HELMET,    Material.DIAMOND_HELMET,    Material.NETHERITE_HELMET},
        {Material.LEATHER_CHESTPLATE,Material.CHAINMAIL_CHESTPLATE,Material.IRON_CHESTPLATE,Material.DIAMOND_CHESTPLATE,Material.NETHERITE_CHESTPLATE},
        {Material.LEATHER_LEGGINGS,  Material.CHAINMAIL_LEGGINGS,  Material.IRON_LEGGINGS,  Material.DIAMOND_LEGGINGS,  Material.NETHERITE_LEGGINGS},
        {Material.LEATHER_BOOTS,     Material.CHAINMAIL_BOOTS,     Material.IRON_BOOTS,     Material.DIAMOND_BOOTS,     Material.NETHERITE_BOOTS},
    };
}