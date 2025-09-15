package me.exnfachjan.twitchRandomizer.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class SessionConfig {
    private final File file;
    private final YamlConfiguration yaml;

    public SessionConfig(File dataFolder) {
        this.file = new File(dataFolder, "session.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean getBoolean(String path, boolean def) {
        return yaml.getBoolean(path, def);
    }

    public boolean getBoolean(String path) {
        return yaml.getBoolean(path);
    }

    public String getString(String path, String def) {
        String v = yaml.getString(path);
        return v != null ? v : def;
    }

    public void set(String path, Object value) {
        yaml.set(path, value);
    }

    public void clear() {
        for (String key : yaml.getKeys(false)) {
            yaml.set(key, null);
        }
    }

    public void save(boolean async) {
        Runnable task = () -> {
            try {
                yaml.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        if (async) {
            new Thread(task, "SessionConfig-Save").start();
        } else {
            task.run();
        }
    }
}