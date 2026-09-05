package com.infinitygear.station;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StationInstanceStore {
    private final File file;
    private final Logger logger;
    private final Map<Key, StationType> instances = new LinkedHashMap<>();

    public StationInstanceStore(File file, Logger logger) {
        this.file = java.util.Objects.requireNonNull(file, "file");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
        load();
    }

    public Optional<StationType> find(Block block) {
        if (block == null) {
            return Optional.empty();
        }

        return find(block.getLocation());
    }

    public Optional<StationType> find(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(instances.get(Key.from(location)));
    }

    public Map<Location, StationType> snapshot() {
        Map<Location, StationType> snapshot = new LinkedHashMap<>();
        instances.forEach((key, type) -> {
            var world = Bukkit.getWorld(key.world());
            if (world != null) snapshot.put(new Location(world, key.x(), key.y(), key.z()), type);
        });
        return snapshot;
    }

    public void bind(Block block, StationType type) {
        if (block == null) {
            throw new IllegalArgumentException("A loaded block and station type are required.");
        }

        bind(block.getLocation(), type);
    }

    public void bind(Location location, StationType type) {
        if (location == null || location.getWorld() == null || type == null) {
            throw new IllegalArgumentException("A loaded location and station type are required.");
        }
        Key key = Key.from(location);
        StationType previous = instances.put(key, type);

        try { save(); }
        catch (RuntimeException failure) {
            if (previous == null) {
                instances.remove(key);
            } else {
                instances.put(key, previous);
            }

            throw failure;
        }
    }

    public Optional<StationType> unbind(Block block) {
        if (block == null) {
            return Optional.empty();
        }

        return unbind(block.getLocation());
    }

    public Optional<StationType> unbind(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        Key key = Key.from(location);
        StationType removed = instances.remove(key);
        if (removed == null) {
            return Optional.empty();
        }

        try {
            save();
        } catch (RuntimeException failure) {
            instances.put(key, removed);
            throw failure;
        }

        return Optional.ofNullable(removed);
    }

    private void load() {
        instances.clear();
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection instancesSection = yaml.getConfigurationSection("instances");
        if (instancesSection == null) {
            return;
        }

        for (String encoded : instancesSection.getKeys(false)) {
            try {
                Key key = Key.decode(encoded);
                String configuredType = instancesSection.getString(encoded);
                StationType type = StationType.valueOf(configuredType.toUpperCase(Locale.ROOT));
                instances.put(key, type);
            } catch (RuntimeException invalid) {
                logger.warning("Ignored malformed station instance '" + encoded + "' in " + file.getName());
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        instances.forEach((key, type) -> yaml.set("instances." + key.encode(), type.name()));

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create directory " + parent);
            }

            yaml.save(file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "Could not persist InfinityGear station instances", failure);
            throw new IllegalStateException("Could not persist station binding.", failure);
        }
    }

    record Key(UUID world, int x, int y, int z) {
        static Key from(Location location) {
            if (location == null || location.getWorld() == null) {
                throw new IllegalArgumentException("A location with a loaded world is required.");
            }

            return new Key(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        String encode() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        static Key decode(String encoded) {
            String[] parts = encoded.split(";", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid station key");
            }

            return new Key(
                    UUID.fromString(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        }
    }
}
