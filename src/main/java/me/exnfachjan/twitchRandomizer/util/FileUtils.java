package me.exnfachjan.twitchRandomizer.util;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.*;
import java.nio.file.Files;

public final class FileUtils {

    private FileUtils() {}

    public static void unloadWorldIfLoaded(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) {
            // Keine Spieler sollten beim Boot online sein; falls doch, teleportiere sie in eine andere Welt
            Bukkit.unloadWorld(w, false);
        }
    }

    public static void deleteWorldFolder(File folder) {
        if (!folder.exists()) return;
        // Sicherheits-Hinweis: Der Weltordner darf nicht geladen sein
        if (folder.isDirectory()) {
            File[] kids = folder.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteWorldFolder(k);
                }
            }
        }
        try {
            Files.deleteIfExists(folder.toPath());
        } catch (IOException ignored) {
            // Best effort
        }
    }

    public static void copyDirectory(File source, File target) throws IOException {
        if (!source.exists()) return;
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Could not create target dir: " + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(new File(source, child.getName()), new File(target, child.getName()));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }
}