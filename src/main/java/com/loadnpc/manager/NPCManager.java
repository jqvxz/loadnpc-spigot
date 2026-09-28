package com.loadnpc.manager;

import com.loadnpc.LoadNPCPlugin;
import com.loadnpc.model.NPCData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NPCManager {

    private static final String NPC_METADATA_KEY = "LoadNPC";
    private static final String DATA_FILE = "npcs.yml";

    private final LoadNPCPlugin plugin;
    private int chunkRadius;
    private int maxNPCsPerPlayer;

    private final Map<UUID, NPCData> activeNPCs = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> idToEntity = new ConcurrentHashMap<>();
    private int nextId = 1;

    public NPCManager(LoadNPCPlugin plugin) {
        this.plugin = plugin;
        this.chunkRadius = plugin.getConfig().getInt("chunk-radius", 2);
        this.maxNPCsPerPlayer = plugin.getConfig().getInt("max-npcs-per-player", 3);
    }

    public void reloadConfig() {
        int oldRadius = this.chunkRadius;
        this.chunkRadius = plugin.getConfig().getInt("chunk-radius", 2);
        this.maxNPCsPerPlayer = plugin.getConfig().getInt("max-npcs-per-player", 3);

        if (oldRadius != this.chunkRadius) {
            releaseAllChunkTickets();
            for (NPCData data : activeNPCs.values()) {
                applyChunkTickets(data);
            }
        }
    }

    public int getChunkRadius() {
        return chunkRadius;
    }

    public void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> expired = new ArrayList<>();

                for (NPCData data : activeNPCs.values()) {
                    if (data.isExpired()) {
                        expired.add(data.entityUUID());
                        continue;
                    }
                    if (data.isTimed()) {
                        updateNametag(data);
                    }
                }

                for (UUID uuid : expired) {
                    NPCData d = activeNPCs.get(uuid);
                    if (d != null) {
                        plugin.getLogger().info("NPC #" + d.id() + " expired, removing.");
                    }
                    killNPC(uuid);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void updateNametag(NPCData data) {
        Entity entity = Bukkit.getEntity(data.entityUUID());
        if (entity instanceof ArmorStand stand && stand.isValid()) {
            String name = ChatColor.GREEN + "LoadNPC " + ChatColor.GRAY + "by "
                    + ChatColor.WHITE + data.ownerName() + " "
                    + ChatColor.YELLOW + data.formatTimeLeft();
            stand.setCustomName(name);
            return;
        }

        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) return;
        int chunkX = Location.locToBlock(loc.getX()) >> 4;
        int chunkZ = Location.locToBlock(loc.getZ()) >> 4;
        if (!loc.getWorld().isChunkLoaded(chunkX, chunkZ)) return;

        for (Entity e : loc.getWorld().getChunkAt(chunkX, chunkZ).getEntities()) {
            if (e.getUniqueId().equals(data.entityUUID()) && e instanceof ArmorStand stand) {
                String name = ChatColor.GREEN + "LoadNPC " + ChatColor.GRAY + "by "
                        + ChatColor.WHITE + data.ownerName() + " "
                        + ChatColor.YELLOW + data.formatTimeLeft();
                stand.setCustomName(name);
                break;
            }
        }
    }

    public void dressAsPlayer(ArmorStand as, UUID ownerUUID) {
        as.setCustomNameVisible(true);
        as.setGravity(false);
        as.setInvulnerable(true);
        as.setVisible(true);
        as.setSmall(false);
        as.setBasePlate(false);
        as.setArms(true);
        as.setCanPickupItems(false);
        as.setPersistent(true);
        as.setCollidable(false);
        as.setSilent(true);

        boolean glowing = plugin.getConfig().getBoolean("visuals.glowing", false);
        as.setGlowing(glowing);

        as.setRightArmPose(new EulerAngle(Math.toRadians(340), 0, Math.toRadians(5)));
        as.setLeftArmPose(new EulerAngle(Math.toRadians(10), 0, Math.toRadians(350)));
        as.setRightLegPose(new EulerAngle(Math.toRadians(5), 0, 0));
        as.setLeftLegPose(new EulerAngle(Math.toRadians(355), 0, 0));
        as.setHeadPose(new EulerAngle(0, 0, 0));

        EntityEquipment eq = as.getEquipment();
        if (eq == null) return;

        boolean useOwnerSkin = plugin.getConfig().getBoolean("visuals.use-owner-skin", true);
        if (useOwnerSkin) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerUUID));
                skull.setItemMeta(skullMeta);
            }
            eq.setHelmet(skull);
        }

        Color shirtColor = parseColor(plugin.getConfig().getString("visuals.shirt-color"), Color.fromRGB(0, 160, 180));
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(shirtColor);
            chest.setItemMeta(chestMeta);
        }
        eq.setChestplate(chest);

        Color pantsColor = parseColor(plugin.getConfig().getString("visuals.pants-color"), Color.fromRGB(43, 59, 137));
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta legsMeta = (LeatherArmorMeta) legs.getItemMeta();
        if (legsMeta != null) {
            legsMeta.setColor(pantsColor);
            legs.setItemMeta(legsMeta);
        }
        eq.setLeggings(legs);

        Color bootsColor = parseColor(plugin.getConfig().getString("visuals.boots-color"), Color.fromRGB(60, 60, 60));
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setColor(bootsColor);
            boots.setItemMeta(bootsMeta);
        }
        eq.setBoots(boots);

        String itemStr = plugin.getConfig().getString("visuals.held-item", "CLOCK");
        Material heldMat = parseMaterial(itemStr, Material.CLOCK);
        if (heldMat != Material.AIR) {
            eq.setItemInMainHand(new ItemStack(heldMat));
        } else {
            eq.setItemInMainHand(null);
        }
    }

    public ArmorStand spawnNPC(UUID ownerUUID, String ownerName, Location location, long durationMs) {
        int id = nextId++;
        long expiresAt = (durationMs > 0) ? System.currentTimeMillis() + durationMs : -1;
        String timeDisplay = (durationMs > 0) ? formatDuration(durationMs) : "\u221E";

        String initialName = ChatColor.GREEN + "LoadNPC " + ChatColor.GRAY + "by "
                + ChatColor.WHITE + ownerName + " "
                + ChatColor.YELLOW + timeDisplay;

        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setCustomName(initialName);
            dressAsPlayer(as, ownerUUID);
            as.addScoreboardTag(NPC_METADATA_KEY);
            as.addScoreboardTag("owner:" + ownerUUID);
            as.addScoreboardTag("npcid:" + id);
        });

        NPCData data = new NPCData(id, stand.getUniqueId(), ownerUUID, ownerName,
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), expiresAt);
        activeNPCs.put(stand.getUniqueId(), data);
        idToEntity.put(id, stand.getUniqueId());
        applyChunkTickets(data);
        saveNPCs();

        return stand;
    }

    public void killNPC(UUID entityUUID) {
        NPCData data = activeNPCs.remove(entityUUID);
        if (data == null) return;

        idToEntity.remove(data.id());

        Entity entity = Bukkit.getEntity(entityUUID);
        if (entity != null) {
            entity.remove();
        } else {
            Location loc = data.toLocation();
            if (loc != null && loc.getWorld() != null) {
                int chunkX = Location.locToBlock(loc.getX()) >> 4;
                int chunkZ = Location.locToBlock(loc.getZ()) >> 4;
                if (loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    for (Entity e : loc.getWorld().getChunkAt(chunkX, chunkZ).getEntities()) {
                        if (e.getUniqueId().equals(entityUUID)) {
                            e.remove();
                            break;
                        }
                    }
                }
            }
        }

        releaseChunkTickets(data);
        saveNPCs();
    }

    public NPCData killNPCById(int id) {
        UUID entityUUID = idToEntity.get(id);
        if (entityUUID == null) return null;
        NPCData data = activeNPCs.get(entityUUID);
        killNPC(entityUUID);
        return data;
    }

    public boolean extendNPCDuration(int id, long additionalMs) {
        UUID entityUUID = idToEntity.get(id);
        if (entityUUID == null) return false;
        NPCData data = activeNPCs.get(entityUUID);
        if (data == null || !data.isTimed()) return false;

        long base = Math.max(System.currentTimeMillis(), data.expiresAt());
        long newExpires = base + additionalMs;
        NPCData updated = data.withExpiresAt(newExpires);

        activeNPCs.put(entityUUID, updated);
        updateNametag(updated);
        saveNPCs();
        return true;
    }

    public int clearPlayerNPCs(UUID ownerUUID) {
        List<UUID> toKill = activeNPCs.values().stream()
                .filter(d -> d.ownerUUID().equals(ownerUUID))
                .map(NPCData::entityUUID)
                .toList();

        for (UUID uuid : toKill) {
            killNPC(uuid);
        }
        return toKill.size();
    }

    public int clearAllNPCs() {
        List<UUID> toKill = new ArrayList<>(activeNPCs.keySet());
        for (UUID uuid : toKill) {
            killNPC(uuid);
        }
        return toKill.size();
    }

    public List<NPCData> findNPCsByOwnerName(String name) {
        return activeNPCs.values().stream()
                .filter(d -> d.ownerName().equalsIgnoreCase(name))
                .toList();
    }

    public boolean isLoadNPC(UUID entityUUID) {
        return activeNPCs.containsKey(entityUUID);
    }

    public boolean isLoadNPC(Entity entity) {
        return entity.getScoreboardTags().contains(NPC_METADATA_KEY);
    }

    public UUID getOwner(UUID entityUUID) {
        NPCData data = activeNPCs.get(entityUUID);
        return data != null ? data.ownerUUID() : null;
    }

    public NPCData getData(UUID entityUUID) {
        return activeNPCs.get(entityUUID);
    }

    public NPCData getDataById(int id) {
        UUID entityUUID = idToEntity.get(id);
        return entityUUID != null ? activeNPCs.get(entityUUID) : null;
    }

    public UUID getOwnerFromEntity(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("owner:")) {
                try {
                    return UUID.fromString(tag.substring(6));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    public int getIdFromEntity(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith("npcid:")) {
                try {
                    return Integer.parseInt(tag.substring(6));
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    public int getActiveNPCCount() {
        return activeNPCs.size();
    }

    public int getPlayerNPCCount(UUID ownerUUID) {
        return (int) activeNPCs.values().stream()
                .filter(d -> d.ownerUUID().equals(ownerUUID))
                .count();
    }

    public int getMaxNPCsPerPlayer() {
        return maxNPCsPerPlayer;
    }

    public Collection<NPCData> getAllNPCs() {
        return Collections.unmodifiableCollection(activeNPCs.values());
    }

    public List<NPCData> getPlayerNPCs(UUID ownerUUID) {
        return activeNPCs.values().stream()
                .filter(d -> d.ownerUUID().equals(ownerUUID))
                .toList();
    }

    private void applyChunkTickets(NPCData data) {
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();
        int centerX = Location.locToBlock(loc.getX()) >> 4;
        int centerZ = Location.locToBlock(loc.getZ()) >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.addPluginChunkTicket(centerX + dx, centerZ + dz, plugin);
            }
        }
    }

    private void releaseChunkTickets(NPCData data) {
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();
        int centerX = Location.locToBlock(loc.getX()) >> 4;
        int centerZ = Location.locToBlock(loc.getZ()) >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.removePluginChunkTicket(centerX + dx, centerZ + dz, plugin);
            }
        }
    }

    public void releaseAllChunkTickets() {
        for (World world : plugin.getServer().getWorlds()) {
            world.removePluginChunkTickets(plugin);
        }
    }

    public void saveNPCs() {
        saveNPCs(true);
    }

    public void saveNPCs(boolean async) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, DATA_FILE);
        File tempFile = new File(dataFolder, DATA_FILE + ".tmp");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("next-id", nextId);

        int index = 0;
        for (NPCData data : activeNPCs.values()) {
            String path = "npcs." + index;
            yaml.set(path + ".id", data.id());
            yaml.set(path + ".entity-uuid", data.entityUUID().toString());
            yaml.set(path + ".owner-uuid", data.ownerUUID().toString());
            yaml.set(path + ".owner-name", data.ownerName());
            yaml.set(path + ".world", data.worldName());
            yaml.set(path + ".x", data.x());
            yaml.set(path + ".y", data.y());
            yaml.set(path + ".z", data.z());
            yaml.set(path + ".expires-at", data.expiresAt());
            index++;
        }

        Runnable writeTask = () -> {
            synchronized (this) {
                try {
                    yaml.save(tempFile);
                    try {
                        Files.move(tempFile.toPath(), file.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save NPCs", e);
                }
            }
        };

        if (async && plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, writeTask);
        } else {
            writeTask.run();
        }
    }

    public void loadNPCs() {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId = yaml.getInt("next-id", 1);

        ConfigurationSection section = yaml.getConfigurationSection("npcs");
        if (section == null) return;

        List<String> toRemove = new ArrayList<>();

        for (String key : section.getKeys(false)) {
            String path = "npcs." + key;
            try {
                int id = yaml.getInt(path + ".id");
                UUID entityUUID = UUID.fromString(yaml.getString(path + ".entity-uuid"));
                UUID ownerUUID = UUID.fromString(yaml.getString(path + ".owner-uuid"));
                String ownerName = yaml.getString(path + ".owner-name", "Unknown");
                String worldName = yaml.getString(path + ".world");
                double x = yaml.getDouble(path + ".x");
                double y = yaml.getDouble(path + ".y");
                double z = yaml.getDouble(path + ".z");
                long expiresAt = yaml.getLong(path + ".expires-at", -1);

                int chunkX = Location.locToBlock(x) >> 4;
                int chunkZ = Location.locToBlock(z) >> 4;

                if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                    plugin.getLogger().info("NPC #" + id + " expired while offline, cleaning up.");
                    World w = plugin.getServer().getWorld(worldName);
                    if (w != null) {
                        Chunk chunk = w.getChunkAt(chunkX, chunkZ);
                        chunk.load();
                        for (Entity entity : chunk.getEntities()) {
                            if (entity.getUniqueId().equals(entityUUID)) {
                                entity.remove();
                                break;
                            }
                        }
                    }
                    toRemove.add(key);
                    continue;
                }

                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found, skipping NPC #" + id);
                    continue;
                }

                boolean found = false;
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                chunk.load();

                for (Entity entity : chunk.getEntities()) {
                    if (entity.getUniqueId().equals(entityUUID) && entity instanceof ArmorStand stand) {
                        found = true;
                        if (stand.getEquipment() == null || stand.getEquipment().getHelmet() == null
                                || stand.getEquipment().getHelmet().getType() != Material.PLAYER_HEAD) {
                            dressAsPlayer(stand, ownerUUID);
                        }
                        break;
                    }
                }

                if (!found) {
                    plugin.getLogger().warning("NPC #" + id + " entity no longer exists, cleaning up.");
                    toRemove.add(key);
                    continue;
                }

                NPCData data = new NPCData(id, entityUUID, ownerUUID, ownerName, worldName, x, y, z, expiresAt);
                activeNPCs.put(entityUUID, data);
                idToEntity.put(id, entityUUID);
                applyChunkTickets(data);

                if (id >= nextId) nextId = id + 1;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load NPC entry: " + key, e);
                toRemove.add(key);
            }
        }

        if (!toRemove.isEmpty()) {
            saveNPCs(false);
        }
    }

    private Color parseColor(String hex, Color def) {
        if (hex == null || hex.trim().isEmpty()) return def;
        try {
            String clean = hex.trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            int rgb = Integer.parseInt(clean, 16);
            return Color.fromRGB(rgb);
        } catch (Exception e) {
            return def;
        }
    }

    private Material parseMaterial(String name, Material def) {
        if (name == null || name.trim().isEmpty()) return def;
        Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return mat != null ? mat : def;
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
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
