package com.loadnpc.command;

import com.loadnpc.LoadNPCPlugin;
import com.loadnpc.manager.NPCManager;
import com.loadnpc.model.NPCData;
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
        for (String key : players.getKeys(false)) {
            if (key.equalsIgnoreCase(player.getName())) {
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
        return player.hasPermission("loadnpc.spawn");
    }

    private boolean canKill(Player player) {
        if (useConfigAccess()) return isAllowed(player);
        return player.hasPermission("loadnpc.kill");
    }

    private boolean canKillOthers(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return sec != null && sec.getBoolean("can-kill-others", false);
        }
        return player.hasPermission("loadnpc.killothers");
    }

    private boolean canListAll(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return sec != null && sec.getBoolean("can-list-all", false);
        }
        return player.hasPermission("loadnpc.list.all");
    }

    private boolean canSpawnMultiple(Player player) {
        if (useConfigAccess()) return getMaxNPCs(player) > 1;
        return player.hasPermission("loadnpc.spawn.multiple");
    }

    private boolean canReload(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            return (sec != null && sec.getBoolean("can-reload", false)) || player.isOp();
        }
        return player.hasPermission("loadnpc.reload");
    }

    private int getMaxNPCs(Player player) {
        if (useConfigAccess()) {
            ConfigurationSection sec = getPlayerSection(player);
            if (sec != null) return sec.getInt("max-npcs", 0);
            return plugin.getConfig().getInt("default-max-npcs", 0);
        }
        return npcManager.getMaxNPCsPerPlayer();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players (except: /" + label + " reload).");
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

        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(player, args);
            case "kill"  -> handleKill(player, args);
            case "list"  -> handleList(player, args);
            case "id"    -> handleId(player);
            default      -> sendUsage(player);
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
        sender.sendMessage(msg("config-reloaded", "&aLoadNPC configuration reloaded successfully."));
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
                player.sendMessage(ChatColor.RED + "No NPC found with ID #" + id);
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
            player.sendMessage(ChatColor.GOLD + "══ Your LoadNPCs (" + npcs.size() + "/" + getMaxNPCs(player) + ") ══");
        }

        if (npcs.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  No NPCs found.");
            return;
        }

        for (NPCData data : npcs) {
            String locStr = String.format("%s %d %d %d", data.worldName(),
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

    private String msg(String key) {
        return msg(key, "&7Unknown message.");
    }

    private String msg(String key, String defaultMessage) {
        String raw = plugin.getConfig().getString("messages." + key, defaultMessage);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "══ LoadNPC Commands ══");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc spawn [time]" + ChatColor.GRAY + " — Spawn NPC (e.g. 10m, 1h, 2h30m)");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc kill [id]" + ChatColor.GRAY + " — Kill NPC by ID or by looking at it");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc list" + ChatColor.GRAY + " — List your NPCs");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc list all" + ChatColor.GRAY + " — List all NPCs");
        sender.sendMessage(ChatColor.YELLOW + "  /loadnpc id" + ChatColor.GRAY + " — Get the ID of the NPC you're looking at");
        if (canReload(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "  /loadnpc reload" + ChatColor.GRAY + " — Reload configuration");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> subcommands = new ArrayList<>(List.of("spawn", "kill", "list", "id"));
            if (canReload(sender)) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (sub.equals("spawn")) {
                return List.of("10m", "30m", "1h", "2h", "6h", "12h", "24h").stream()
                        .filter(s -> s.startsWith(input))
                        .toList();
            }
            if (sub.equals("kill")) {
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
        }
        return Collections.emptyList();
    }
}
