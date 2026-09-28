package com.loadnpc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record NPCData(
        int id,
        UUID entityUUID,
        UUID ownerUUID,
        String ownerName,
        String worldName,
        double x,
        double y,
        double z,
        long expiresAt
) {

    public boolean isTimed() {
        return expiresAt > 0;
    }

    public boolean isExpired() {
        return isTimed() && System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingMillis() {
        if (!isTimed()) return -1;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public NPCData withExpiresAt(long newExpiresAt) {
        return new NPCData(id, entityUUID, ownerUUID, ownerName, worldName, x, y, z, newExpiresAt);
    }

    public String formatTimeLeft() {
        if (!isTimed()) return "\u221E";
        long remaining = getRemainingMillis();
        if (remaining <= 0) return "0s";

        long totalSeconds = remaining / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
