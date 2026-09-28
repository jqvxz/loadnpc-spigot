package com.loadnpc;

import com.loadnpc.command.LoadNPCCommand;
import com.loadnpc.listener.NPCProtectionListener;
import com.loadnpc.manager.NPCManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoadNPCPlugin extends JavaPlugin {

    private NPCManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        npcManager = new NPCManager(this);
        npcManager.loadNPCs();
        npcManager.startTickTask();

        LoadNPCCommand command = new LoadNPCCommand(this, npcManager);
        getCommand("loadnpc").setExecutor(command);
        getCommand("loadnpc").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new NPCProtectionListener(npcManager), this);

        getLogger().info("LoadNPC enabled — " + npcManager.getActiveNPCCount() + " NPC(s) restored.");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.saveNPCs(false);
            npcManager.releaseAllChunkTickets();
        }
        getLogger().info("LoadNPC disabled — all chunk tickets released.");
    }
}
