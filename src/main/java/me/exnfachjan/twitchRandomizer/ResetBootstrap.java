package me.exnfachjan.twitchRandomizer;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ResetBootstrap {

    private static final String PREGEN_PREFIX = "pregenerated_";

    private final TwitchRandomizer plugin;
    private final Logger log;

    public ResetBootstrap(TwitchRandomizer plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // In der Plugin-Hauptklasse in onLoad() aufrufen
    public void executeIfNecessary() {
        boolean pending = plugin.getConfig().getBoolean("reset.pending", false);
        if (!pending) return;

        String levelName = plugin.getConfig().getString("reset.levelName", "world");
        boolean seedReset = plugin.getConfig().getBoolean("reset.seedReset", true);

        File worldContainer = Bukkit.getWorldContainer();
        String[] worlds = new String[] {
                levelName,
                levelName + "_nether",
                levelName + "_the_end"
        };

        log.info("[ResetBootstrap] Reset ist ausstehend – ersetze Welten vor dem Laden.");

        for (String name : worlds) {
            File target = new File(worldContainer, name);
            File source = new File(worldContainer, PREGEN_PREFIX + name);

            // Zielwelt entfernen
            deleteQuietly(target);

            // Falls Seed-Reset angefordert und pregen vorhanden, kopieren
            if (seedReset && source.exists() && source.isDirectory()) {
                log.info("[ResetBootstrap] Kopiere " + source.getName() + " -> " + target.getName());
                try {
                    copyRecursive(source.toPath(), target.toPath());
                } catch (IOException e) {
                    log.warning("[ResetBootstrap] Konnte vorerzeugte Welt nicht kopieren: " + e.getMessage());
                }
            } else if (seedReset) {
                log.warning("[ResetBootstrap] Keine vorerzeugte Welt gefunden: " + source.getName());
            }

            // Pregen-Ordner nach Verwendung entfernen
            deleteQuietly(source);
        }

        // Flags zurücksetzen
        plugin.getConfig().set("reset.pending", false);
        plugin.getConfig().set("reset.seedReset", false);
        plugin.saveConfig();

        log.info("[ResetBootstrap] Welt-Reset abgeschlossen, Flags entfernt.");
    }

    private void deleteQuietly(File path) {
        if (path == null || !path.exists()) return;
        try (Stream<Path> walk = Files.walk(path.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}

        if (path.exists()) {
            try {
                File target = new File(path.getParentFile(), path.getName() + ".old-" + System.currentTimeMillis());
                boolean renamed = path.renameTo(target);
                if (renamed) {
                    log.warning("[ResetBootstrap] Ordner konnte nicht gelöscht werden, wurde umbenannt: "
                            + path.getName() + " -> " + target.getName());
                } else {
                    log.warning("[ResetBootstrap] Ordner konnte nicht gelöscht/umbenannt werden: " + path.getAbsolutePath());
                }
            } catch (Throwable ignored) {}
        }
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                Path rel = source.relativize(src);
                Path dst = target.resolve(rel);
                try {
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}