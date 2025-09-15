package me.exnfachjan.twitchRandomizer.reset;

import me.exnfachjan.twitchRandomizer.TwitchRandomizer;
import me.exnfachjan.twitchRandomizer.config.SessionConfig;
import me.exnfachjan.twitchRandomizer.i18n.Messages;
import me.exnfachjan.twitchRandomizer.util.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ResetManager {

    private static final String CUSTOM_PREFIX = "pregenerated_";

    private final TwitchRandomizer plugin;
    private final Messages i18n;
    private final SessionConfig session;

    public ResetManager(TwitchRandomizer plugin, SessionConfig session) {
        this.plugin = plugin;
        this.i18n = plugin.getMessages();
        this.session = session;
    }

    public void executeWorldResetIfNecessary() {
        if (!session.getBoolean("reset", false)) return;

        boolean seedReset = session.getBoolean("seed-reset", false);
        String levelName = session.getString("level-name", "world");
        String[] worlds = new String[] { levelName, levelName + "_nether", levelName + "_the_end" };

        plugin.getLogger().info("[Reset] Deleting worlds..");

        for (String name : worlds) {
            try {
                FileUtils.unloadWorldIfLoaded(name);

                File target = new File(Bukkit.getWorldContainer(), name);
                FileUtils.deleteWorldFolder(target);
                plugin.getLogger().info("[Reset] Deleted world " + name);

                if (seedReset) {
                    File source = new File(Bukkit.getWorldContainer(), CUSTOM_PREFIX + name);
                    if (source.exists() && source.isDirectory()) {
                        FileUtils.copyDirectory(source, target);
                        plugin.getLogger().info("[Reset] Copied pregenerated world to " + name);
                    } else {
                        plugin.getLogger().warning("[Reset] Pregenerated world does not exist: " + source.getName());
                    }
                } else {
                    File preg = new File(Bukkit.getWorldContainer(), CUSTOM_PREFIX + name);
                    FileUtils.deleteWorldFolder(preg);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("[Reset] Failed to reset world " + name + ": " + ex.getMessage());
            } catch (Throwable t) {
                plugin.getLogger().warning("[Reset] Unexpected error while resetting " + name + ": " + t.getMessage());
            }
        }

        session.set("reset", false);
        session.set("seed-reset", false);
        session.save(false);
        plugin.getLogger().info("[Reset] Finished.");
    }

    public void prepareWorldReset(@Nullable CommandSender requestedBy, long seed) {
        String fallbackServer = plugin.getConfig().getString("reset.fallback_server", "");
        int transferWaitTicks = Math.max(0, plugin.getConfig().getInt("reset.transfer_wait_ticks", 60));
        boolean restartOnReset = plugin.getConfig().getBoolean("restart-on-reset", false);
        int titleLeadTicks = Math.max(0, plugin.getConfig().getInt("reset.title_lead_ticks", 40)); // NEU: Zeit, um Title sichtbar zu lassen

        String levelName = !Bukkit.getWorlds().isEmpty() ? Bukkit.getWorlds().get(0).getName() : "world";
        session.set("reset", true);
        session.set("seed-reset", true);
        session.set("level-name", levelName);
        session.save(false);

        String requester = (requestedBy instanceof Player p) ? p.getName() : "Console";

        // 1) Titel (sofort)
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                String title = i18n.tr(player, "title.reset.line1");
                String sub = i18n.tr(player, "title.reset.line2");
                // längere Stay-Time ist egal – wir verzögern jetzt den Transfer separat
                player.sendTitle(title, sub, 10, 60, 10);
            } catch (Throwable ignored) {}
        }

        boolean doTransfer = (fallbackServer != null && !fallbackServer.isBlank());

        // 2) Transfer/Kick verzögert, damit der Title sichtbar wird
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (doTransfer) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendToBungeeServer(player, fallbackServer);
                }
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        String kickMessage = i18n.tr(player, "server.reset.kick", Map.of("requester", requester));
                        player.kickPlayer(kickMessage);
                    } catch (Throwable ignored) {}
                }
            }
        }, titleLeadTicks);

        // 3) Pregeneration ein paar Ticks nach Transfer
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                preGenerateSeedWorlds(seed, levelName);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Reset] Failed to pre-generate seed worlds: " + t.getMessage());
            }
        }, titleLeadTicks + 10L);

        // 4) Shutdown/Restart nach Transferwartezeit
        long shutdownDelay = titleLeadTicks + Math.max(3L, transferWaitTicks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (restartOnReset) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
            } else {
                Bukkit.shutdown();
            }
        }, shutdownDelay);
    }

    private void preGenerateSeedWorlds(long seed, String levelName) {
        String[] names = new String[] { levelName, levelName + "_nether", levelName + "_the_end" };
        for (String name : names) {
            World src = Bukkit.getWorld(name);
            if (src == null) {
                plugin.getLogger().warning("[Reset] Source world not loaded: " + name + " - skipping pregeneration");
                continue;
            }
            String newWorldName = CUSTOM_PREFIX + name;

            File target = new File(Bukkit.getWorldContainer(), newWorldName);
            FileUtils.deleteWorldFolder(target);

            try {
                WorldCreator wc = new WorldCreator(newWorldName)
                        .seed(seed)
                        .environment(src.getEnvironment());
                try { wc.generator(src.getGenerator()); } catch (Throwable ignored) {}
                try { wc.biomeProvider(src.getBiomeProvider()); } catch (Throwable ignored) {}
                try { wc.type(src.getWorldType()); } catch (Throwable ignored) {}
                try {
                    boolean genStruct = (boolean) src.getClass().getMethod("canGenerateStructures").invoke(src);
                    wc.generateStructures(genStruct);
                } catch (Throwable ignored) {}

                World created = wc.createWorld();
                if (created != null) {
                    try { created.save(); } catch (Throwable ignored) {}
                    plugin.getLogger().info("[Reset] Created pregenerated world " + newWorldName + " with seed " + seed);
                } else {
                    plugin.getLogger().warning("[Reset] WorldCreator returned null for " + newWorldName);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Reset] Failed to create pregenerated world " + newWorldName + ": " + t.getMessage());
            }
        }
    }

    private void sendToBungeeServer(Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            plugin.getLogger().info("[Reset] Sent player " + player.getName() + " to '" + server + "'");
        } catch (Throwable t) {
            try {
                String kickMessage = i18n.tr(player, "server.reset.kick", Map.of("requester", "Server"));
                player.kickPlayer(kickMessage);
            } catch (Throwable ignored) {}
            plugin.getLogger().warning("[Reset] Could not transfer " + player.getName() + " to '" + server + "': " + t.getMessage());
        }
    }
}