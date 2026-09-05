package com.infinitygear.station;

import com.infinitypickaxes.InfinityPickaxes;
import org.bukkit.Location;
import org.bukkit.World;

public final class StationParticleTask implements Runnable {
    private final StationManager stations;
    private long tick;

    public StationParticleTask(StationManager stations) {
        this.stations = stations;
    }

    @Override
    public void run() {
        tick++;
        for (var entry : stations.particleInstances().entrySet()) {
            StationManager.Definition definition = stations.definition(entry.getValue());
            if (definition == null) continue;
            StationManager.ParticleSettings particles = definition.particles();
            if (!particles.enabled() || particles.type() == null || particles.count() == 0
                    || tick % particles.intervalTicks() != 0) continue;

            Location station = entry.getKey();
            World world = station.getWorld();
            if (world == null) continue;
            Location center = station.clone().add(0.5, 0.5, 0.5);
            world.spawnParticle(particles.type(), center, particles.count(),
                    particles.offsetX(), particles.offsetY(), particles.offsetZ(), particles.speed());
        }
    }
}