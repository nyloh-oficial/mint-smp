package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wires up all Mythic item effects. */
public final class MythicListener implements Listener {

    private final MintSMP plugin;
    private final Map<UUID, List<ItemStack>> soulbound = new HashMap<>();

    public MythicListener(MintSMP plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker && e.getEntity() instanceof LivingEntity victim) {
            plugin.mythic().onWeaponHit(attacker, victim, attacker.getInventory().getItemInMainHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // Phoenix: immune to wall-slam (kinetic) damage
        if (e.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL && plugin.mythic().wearingPhoenix(p)) {
            e.setCancelled(true); return;
        }
        // Aegis full set: no fall damage
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.mythic().aegisWorn(p) >= 4) {
            e.setCancelled(true); return;
        }
        // Cornucopia: reusable auto-totem on otherwise-lethal damage
        if (plugin.mythic().cornucopiaSave(p, e.getFinalDamage())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player shooter && e.getProjectile() instanceof Projectile arrow) {
            plugin.mythic().tagArrow(arrow, shooter);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (plugin.mythic().isMythicArrow(e.getEntity())) {
            plugin.mythic().arrowImpact(e.getEntity().getLocation());
            e.getEntity().remove();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        plugin.mythic().earthshakerPound(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand());
    }

    // ---- soulbound --------------------------------------------------------

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (!plugin.mythic().soulbound()) return;
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(it -> {
            if (plugin.mythic().isMythic(it)) { kept.add(it); return true; }
            return false;
        });
        if (!kept.isEmpty()) soulbound.put(e.getEntity().getUniqueId(), kept);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        List<ItemStack> kept = soulbound.remove(e.getPlayer().getUniqueId());
        if (kept == null) return;
        Player p = e.getPlayer();
        for (ItemStack it : kept)
            for (ItemStack rem : p.getInventory().addItem(it).values())
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
    }
}
