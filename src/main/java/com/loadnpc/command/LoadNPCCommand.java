package com.loadnpc.command;

import com.loadnpc.LoadNPCPlugin;
import com.loadnpc.manager.NPCManager;
import com.loadnpc.model.NPCData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadNPCCommand implements CommandExecutor, TabCompleter {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE
    );

    private final LoadNPCPlugin plugin;
    private final NPCManager npcManager;

    public LoadNPCCommand(LoadNPCPlugin plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    private boolean useConfigAccess() {
        return plugin.getConfig().getBoolean("use-config-access", false);
    }

    private ConfigurationSection getPlayerSection(Player player) {
        ConfigurationSection players = plugin.getConfig().getConfigurationSection("players");
        if (players == null) return null;

        String uuidStr = player.getUniqueId().toString();
        String name = player.getName();

        for (String key : players.getKeys(false)) {
            if (key.equalsIgnoreCase(uuidStr)) {
                return players.getConfigurationSection(key);
            }
        }

        for (String key : players.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) {
                return players.getConfigurationSection(key);
            }
        }
        return null;
    }

    private boolean isAllowed(Player player) {
        if (!useConfigAccess()) return true;
        ConfigurationSection sec = getPlayerSection(player);
        if (sec != null) return sec.getInt("max-npcs", 0) > 0;
        return plugin.getConfig().getInt("default-max-npcs", 0) > 0;
    }

    private boolean canSpawn(Player player) {
        if (useConfigAccess()) return getMaxNPCs(player) > 0;
        return player.hasPermission("loadnpc.spawn") || player.hasPermission("loadnpc.admin");
    }

    private boolean canKill(Player player) {
        if (useConfigAccess()) return isAllowed(player);
        return player.hasPermission("loadnpc.kill") || player.hasPermission("loadnpc.admin");
    }

    private boolean canKillOthers(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return (sec != null && sec.getBoolean("can-kill-others", false)) || player.isOp();
        }
        return player.hasPermission("loadnpc.killothers") || player.hasPermission("loadnpc.admin");
    }

    private boolean canListAll(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return (sec != null && sec.getBoolean("can-list-all", false)) || player.isOp();
        }
        return player.hasPermission("loadnpc.list.all") || player.hasPermission("loadnpc.admin");
    }

    private boolean canSpawnMultiple(Player player) {
        if (useConfigAccess()) return getMaxNPCs(player) > 1;
        return player.hasPermission("loadnpc.spawn.multiple")
                || player.hasPermission("loadnpc.max.*")
                || player.hasPermission("loadnpc.admin")
                || getMaxNPCs(player) > 1;
    }

    private boolean canTp(Player player, NPCData data) {
        if (player.hasPermission("loadnpc.admin")) return true;
        boolean isOwner = data.ownerUUID().equals(player.getUniqueId());
        if (isOwner) {
            return player.hasPermission("loadnpc.tp") || isAllowed(player);
        }
        if (useConfigAccess()) {
            return canKillOthers(player);
        }
        return player.hasPermission("loadnpc.tp.others");
    }

    private boolean canClear(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (player.hasPermission("loadnpc.admin") || player.hasPermission("loadnpc.clear")) return true;
        if (useConfigAccess()) return canKillOthers(player) || player.isOp();
        return false;
    }

    private boolean canExtend(Player player, NPCData data) {
        if (player.hasPermission("loadnpc.admin")) return true;
        boolean isOwner = data.ownerUUID().equals(player.getUniqueId());
        if (isOwner) {
            return player.hasPermission("loadnpc.extend") || player.hasPermission("loadnpc.spawn") || isAllowed(player);
        }
        return canKillOthers(player);
    }

    private boolean canViewInfo(Player player, NPCData data) {
        if (player.hasPermission("loadnpc.admin") || player.hasPermission("loadnpc.info")) return true;
        if (data.ownerUUID().equals(player.getUniqueId())) return true;
        return canListAll(player);
    }

    private boolean canReload(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return (sec != null && sec.getBoolean("can-reload", false)) || player.isOp();
        }
        return player.hasPermission("loadnpc.reload") || player.hasPermission("loadnpc.admin");
    }

    private int getMaxNPCs(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            if (sec != null) return sec.getInt("max-npcs", 0);
            return plugin.getConfig().getInt("default-max-npcs", 0);
        }

        if (player.hasPermission("loadnpc.max.*") || player.hasPermission("loadnpc.admin")) {
            return Integer.MAX_VALUE;
        }

        int maxFromPerms = -1;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("loadnpc.max.") && pai.getValue()) {
                try {
                    int val = Integer.parseInt(perm.substring("loadnpc.max.".length()));
                    if (val > maxFromPerms) {
                        maxFromPerms = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (maxFromPerms > 0) {
            return maxFromPerms;
        }

        if (player.hasPermission("loadnpc.spawn.multiple")) {
            return npcManager.getMaxNPCsPerPlayer();
        }

        if (player.hasPermission("loadnpc.spawn")) {
            return 1;
        }

        return 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            handleClear(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players (except: /" + label + " reload, /" + label + " clear).");
            return true;
        }

        if (useConfigAccess() && !isAllowed(player)) {
            player.sendMessage(msg("not-allowed"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn"  -> handleSpawn(player, args);
            case "kill"   -> handleKill(player, args);
            case "list"   -> handleList(player, args);
            case "id"     -> handleId(player);
            case "tp"     -> handleTp(player, args);
            case "info"   -> handleInfo(player, args);
            case "extend" -> handleExtend(player, args);
            default       -> sendUsage(player);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!canReload(sender)) {
            sender.sendMessage(msg("no-permission"));
            return;
        }

        plugin.reloadConfig();
        npcManager.reloadConfig();
        sender.sendMessage(msg("config-reloaded", "&aConfiguration reloaded successfully."));
    }

    private void handleSpawn(Player player, String[] args) {
        if (!canSpawn(player)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        int current = npcManager.getPlayerNPCCount(player.getUniqueId());
        int max = getMaxNPCs(player);

        if (current >= 1 && !canSpawnMultiple(player)) {
            player.sendMessage(msg("need-multiple-permission"));
            return;
        }
        if (current >= max) {
            player.sendMessage(msg("max-npcs-reached").replace("%max%", String.valueOf(max)));
            return;
        }

        long durationMs = -1;
        if (args.length >= 2) {
            durationMs = parseDuration(args[1]);
            if (durationMs <= 0) {
                player.sendMessage(msg("invalid-duration"));
                return;
            }
        }

        Location loc = player.getLocation().clone();
        ArmorStand npc = npcManager.spawnNPC(player.getUniqueId(), player.getName(), loc, durationMs);
        if (npc != null) {
            NPCData data = npcManager.getData(npc.getUniqueId());
            String message = msg("npc-spawned")
                    .replace("%id%", String.valueOf(data.id()))
                    .replace("%time%", data.formatTimeLeft());
            player.sendMessage(message);
        } else {
            player.sendMessage(msg("max-npcs-reached").replace("%max%", String.valueOf(max)));
        }
    }

    private void handleKill(Player player, String[] args) {
        if (!canKill(player)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        if (args.length >= 2) {
            int id;
            try {
                id = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid ID. Usage: /loadnpc kill <id>");
                return;
            }

            NPCData data = npcManager.getDataById(id);
            if (data == null) {
                player.sendMessage(msg("no-npc-found").replace("%id%", String.valueOf(id)));
                return;
            }

            if (!data.ownerUUID().equals(player.getUniqueId()) && !canKillOthers(player)) {
                player.sendMessage(msg("no-permission-kill-others"));
                return;
            }

            npcManager.killNPCById(id);
            player.sendMessage(msg("npc-killed").replace("%id%", String.valueOf(id)));
            return;
        }

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                10.0, 0.2,
                e -> e instanceof ArmorStand && npcManager.isLoadNPC(e)
        );

        if (result == null || result.getHitEntity() == null) {
            player.sendMessage(msg("not-looking-at-npc"));
            return;
        }

        Entity target = result.getHitEntity();
        UUID ownerUUID = npcManager.getOwnerFromEntity(target);

        if (ownerUUID != null && !ownerUUID.equals(player.getUniqueId())) {
            if (!canKillOthers(player)) {
                player.sendMessage(msg("no-permission-kill-others"));
                return;
            }
        }

        NPCData data = npcManager.getData(target.getUniqueId());
        int id = data != null ? data.id() : npcManager.getIdFromEntity(target);
        npcManager.killNPC(target.getUniqueId());
        player.sendMessage(msg("npc-killed").replace("%id%", String.valueOf(id)));
    }

    private void handleTp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /loadnpc tp <id>");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid ID. Usage: /loadnpc tp <id>");
            return;
        }

        NPCData data = npcManager.getDataById(id);
        if (data == null) {
            player.sendMessage(msg("no-npc-found").replace("%id%", String.valueOf(id)));
            return;
        }

        if (!canTp(player, data)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "World '" + data.worldName() + "' is not loaded.");
            return;
        }

        Location tpLoc = loc.clone().add(0.5, 0, 0.5);
        tpLoc.setYaw(player.getLocation().getYaw());
        tpLoc.setPitch(player.getLocation().getPitch());
        player.teleport(tpLoc);
        player.sendMessage(msg("teleported").replace("%id%", String.valueOf(id)));
    }

    private void handleInfo(Player player, String[] args) {
        NPCData data = null;
        if (args.length >= 2) {
            try {
                int id = Integer.parseInt(args[1]);
                data = npcManager.getDataById(id);
                if (data == null) {
                    player.sendMessage(msg("no-npc-found").replace("%id%", String.valueOf(id)));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid ID. Usage: /loadnpc info [id]");
                return;
            }
        } else {
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    10.0, 0.2,
                    e -> e instanceof ArmorStand && npcManager.isLoadNPC(e)
            );
            if (result != null && result.getHitEntity() != null) {
                data = npcManager.getData(result.getHitEntity().getUniqueId());
            }
        }

        if (data == null) {
            player.sendMessage(msg("not-looking-at-npc"));
            return;
        }

        if (!canViewInfo(player, data)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        int chunkX = Location.locToBlock(data.x()) >> 4;
        int chunkZ = Location.locToBlock(data.z()) >> 4;
        int radius = npcManager.getChunkRadius();
        int area = (radius * 2 + 1) * (radius * 2 + 1);

        player.sendMessage(ChatColor.GOLD + "══ LoadNPC #" + data.id() + " Information ══");
        player.sendMessage(ChatColor.YELLOW + "  Owner: " + ChatColor.WHITE + data.ownerName() + ChatColor.GRAY + " (" + data.ownerUUID() + ")");
        player.sendMessage(ChatColor.YELLOW + "  World: " + ChatColor.WHITE + data.worldName());
        player.sendMessage(ChatColor.YELLOW + "  Coordinates: " + ChatColor.AQUA + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", data.x(), data.y(), data.z()));
        player.sendMessage(ChatColor.YELLOW + "  Chunk: " + ChatColor.WHITE + "[" + chunkX + ", " + chunkZ + "]" + ChatColor.GRAY + " (Radius: " + radius + ", " + area + " chunks)");
        player.sendMessage(ChatColor.YELLOW + "  Time Left: " + ChatColor.GREEN + data.formatTimeLeft());
        player.sendMessage(ChatColor.YELLOW + "  Status: " + ChatColor.GREEN + "Active & Loaded");
    }

    private void handleExtend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /loadnpc extend <id> <duration> (e.g. 1h, 30m)");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid ID. Usage: /loadnpc extend <id> <duration>");
            return;
        }

        NPCData data = npcManager.getDataById(id);
        if (data == null) {
            player.sendMessage(msg("no-npc-found").replace("%id%", String.valueOf(id)));
            return;
        }

        if (!canExtend(player, data)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        if (!data.isTimed()) {
            player.sendMessage(msg("npc-is-permanent").replace("%id%", String.valueOf(id)));
            return;
        }

        long durationMs = parseDuration(args[2]);
        if (durationMs <= 0) {
            player.sendMessage(msg("invalid-duration"));
            return;
        }

        boolean ok = npcManager.extendNPCDuration(id, durationMs);
        if (ok) {
            NPCData updated = npcManager.getDataById(id);
            String message = msg("npc-extended")
                    .replace("%id%", String.valueOf(id))
                    .replace("%time%", formatDuration(durationMs))
                    .replace("%left%", updated != null ? updated.formatTimeLeft() : "");
            player.sendMessage(message);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to extend NPC.");
        }
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!canClear(sender)) {
            sender.sendMessage(msg("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /loadnpc clear <player|all>");
            return;
        }

        String target = args[1];
        if (target.equalsIgnoreCase("all")) {
            int count = npcManager.clearAllNPCs();
            sender.sendMessage(msg("cleared-npcs").replace("%count%", String.valueOf(count)));
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(target);
        int count = 0;
        if (targetPlayer != null) {
            count = npcManager.clearPlayerNPCs(targetPlayer.getUniqueId());
        } else {
            List<NPCData> list = npcManager.findNPCsByOwnerName(target);
            for (NPCData d : list) {
                npcManager.killNPC(d.entityUUID());
                count++;
            }
        }

        sender.sendMessage(msg("cleared-npcs").replace("%count%", String.valueOf(count)));
    }

    private void handleList(Player player, String[] args) {
        boolean showAll = args.length >= 2 && args[1].equalsIgnoreCase("all");

        if (showAll && !canListAll(player)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        Collection<NPCData> npcs;
        if (showAll) {
            npcs = npcManager.getAllNPCs();
            player.sendMessage(ChatColor.GOLD + "══ All LoadNPCs (" + npcs.size() + ") ══");
        } else {
            npcs = npcManager.getPlayerNPCs(player.getUniqueId());
            int max = getMaxNPCs(player);
            String maxStr = max == Integer.MAX_VALUE ? "∞" : String.valueOf(max);
            player.sendMessage(ChatColor.GOLD + "══ Your LoadNPCs (" + npcs.size() + "/" + maxStr + ") ══");
        }

        if (npcs.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  No NPCs found.");
            return;
        }

        for (NPCData data : npcs) {
            String locStr = String.format(Locale.ROOT, "%s %d %d %d", data.worldName(),
                    (int) data.x(), (int) data.y(), (int) data.z());
            String line = ChatColor.YELLOW + "  #" + data.id()
                    + ChatColor.GRAY + " | "
                    + ChatColor.WHITE + data.ownerName()
                    + ChatColor.GRAY + " | "
                    + ChatColor.AQUA + locStr
                    + ChatColor.GRAY + " | "
                    + ChatColor.GREEN + data.formatTimeLeft();
            player.sendMessage(line);
        }
    }

    private void handleId(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                10.0, 0.2,
                e -> e instanceof ArmorStand && npcManager.isLoadNPC(e)
        );

        if (result == null || result.getHitEntity() == null) {
            player.sendMessage(msg("not-looking-at-npc"));
            return;
        }

        NPCData data = npcManager.getData(result.getHitEntity().getUniqueId());
        if (data == null) {
            player.sendMessage(msg("not-looking-at-npc"));
            return;
        }

        player.sendMessage(ChatColor.GREEN + "NPC ID: " + ChatColor.WHITE + "#" + data.id()
                + ChatColor.GRAY + " (owned by " + data.ownerName() + ", " + data.formatTimeLeft() + " left)");
    }

    private long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        if (!matcher.matches()) return -1;

        String hStr = matcher.group(1);
        String mStr = matcher.group(2);
        String sStr = matcher.group(3);

        if (hStr == null && mStr == null && sStr == null) return -1;

        long hours   = hStr != null ? Long.parseLong(hStr) : 0;
        long minutes = mStr != null ? Long.parseLong(mStr) : 0;
        long seconds = sStr != null ? Long.parseLong(sStr) : 0;

        long totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000;
        return totalMs > 0 ? totalMs : -1;
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

    private String msg(String key) {
        return msg(key, "&7Unknown message.");
    }

    private String msg(String key, String defaultMessage) {
        String raw = plugin.getConfig().getString("messages." + key, defaultMessage);
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&aLoadNPC&8] ");
        raw = raw.replace("%prefix%", prefix);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "══ LoadNPC Commands ══");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc spawn [time]" + ChatColor.GRAY + " — Spawn player NPC (e.g. 10m, 1h, 2h30m)");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc kill [id]" + ChatColor.GRAY + " — Kill NPC by ID or by looking at it");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc list [all]" + ChatColor.GRAY + " — List your NPCs or all NPCs");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc id" + ChatColor.GRAY + " — Get the ID of the NPC you're looking at");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc info [id]" + ChatColor.GRAY + " — View detailed chunk & NPC info");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc tp <id>" + ChatColor.GRAY + " — Teleport to an NPC");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc extend <id> <time>" + ChatColor.GRAY + " — Extend an active NPC's timer");
        if (canClear(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "  /loadnpc clear <player|all>" + ChatColor.GRAY + " — Bulk remove NPCs");
        }
        if (canReload(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "  /loadnpc reload" + ChatColor.GRAY + " — Reload configuration");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> subcommands = new ArrayList<>(List.of("spawn", "kill", "list", "id", "tp", "info", "extend"));
            if (canClear(sender)) {
                subcommands.add("clear");
            }
            if (canReload(sender)) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String input = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("spawn")) {
                return List.of("10m", "30m", "1h", "2h", "6h", "12h", "24h").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
            if (sub.equals("kill") || sub.equals("tp") || sub.equals("info") || sub.equals("extend")) {
                Player player = sender instanceof Player p ? p : null;
                if (player != null) {
                    return npcManager.getAllNPCs().stream()
                            .filter(d -> d.ownerUUID().equals(player.getUniqueId()) || canKillOthers(player))
                            .map(d -> String.valueOf(d.id()))
                            .filter(s -> s.startsWith(input))
                            .toList();
                }
            }
            if (sub.equals("list")) {
                return List.of("all").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
            if (sub.equals("clear") && canClear(sender)) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
                for (NPCData d : npcManager.getAllNPCs()) {
                    if (!suggestions.contains(d.ownerName())) {
                        suggestions.add(d.ownerName());
                    }
                }
                return suggestions.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(input))
                        .toList();
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String input = args[2].toLowerCase(Locale.ROOT);
            if (sub.equals("extend")) {
                return List.of("10m", "30m", "1h", "2h", "6h", "12h", "24h").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
        }
        return Collections.emptyList();
    }
}
