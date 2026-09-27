package com.loadnpc.listener;

import com.loadnpc.manager.NPCManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NPCProtectionListener implements Listener {

    private final NPCManager npcManager;

    public NPCProtectionListener(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (npcManager.isLoadNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (npcManager.isLoadNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
