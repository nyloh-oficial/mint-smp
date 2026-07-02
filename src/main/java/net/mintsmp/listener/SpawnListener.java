package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import net.mintsmp.util.Msg;

/**
 * Spawn handling:
 *  - New players (and everyone, until an admin /setspawn) get RTP'd into the wild on join.
 *  - Death respawns at the server spawn (default 113/94/-158).
 *  - Inside the spawn radius: no block edit (non-staff), no damage, no hunger/saturation loss.
 */
public final class SpawnListener implements Listener {

    private final MintSMP plugin;
    public SpawnListener(MintSMP plugin) { this.plugin = plugin; }

    private boolean staff(Player p) { return p.isOp() || p.hasPermission("mint.admin") || p.hasPermission("mint.bypass.spawn"); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Only the very first time this player ever joins.
        if (p.hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.teleport().rtp(p, "overworld");
        }, 25L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        var loc = plugin.teleport().spawnLocation();
        if (loc != null) e.setRespawnLocation(loc);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.teleport().inSpawnRegion(p.getLocation())) {
            if (e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.teleport().inSpawnRegion(p.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (staff(e.getPlayer())) return;
        var b = e.getBlock();
        if (plugin.teleport().inSpawnRegion(b.getLocation())) {
            e.setCancelled(true);
            Msg.sendRaw(e.getPlayer(), "<red>You can't break blocks at spawn.");
            return;
        }
        // Fixed no-break cuboid (config: protected-regions.no-break)
        var c = plugin.getConfig();
        if (c.getBoolean("protected-regions.no-break.enabled", true)
                && b.getWorld().getName().equals(c.getString("protected-regions.no-break.world", "world"))) {
            int x1 = c.getInt("protected-regions.no-break.x1", 152), y1 = c.getInt("protected-regions.no-break.y1", 35), z1 = c.getInt("protected-regions.no-break.z1", -239);
            int x2 = c.getInt("protected-regions.no-break.x2", -10), y2 = c.getInt("protected-regions.no-break.y2", 142), z2 = c.getInt("protected-regions.no-break.z2", -81);
            int bx = b.getX(), by = b.getY(), bz = b.getZ();
            if (bx >= Math.min(x1, x2) && bx <= Math.max(x1, x2)
                    && by >= Math.min(y1, y2) && by <= Math.max(y1, y2)
                    && bz >= Math.min(z1, z2) && bz <= Math.max(z1, z2)) {
                e.setCancelled(true);
                Msg.sendRaw(e.getPlayer(), "<red>This area is protected.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!staff(e.getPlayer()) && plugin.teleport().inSpawnRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            Msg.sendRaw(e.getPlayer(), "<red>You can't place blocks at spawn.");
        }
    }

    /** Block using items at spawn — except consumables (food, potions, milk, etc.). */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (staff(p)) return;
        if (e.getItem() == null) return;
        if (!plugin.teleport().inSpawnRegion(p.getLocation())) return;
        Material m = e.getItem().getType();
        if (isConsumable(m)) return; // food/potions allowed
        // Deny the item's effect (pearls, bows, buckets, block placement) but still
        // allow interacting with blocks like chests/doors.
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    /** Block using items ON entities at spawn (e.g. name tags, buckets on mobs). */
    @EventHandler(ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (staff(p)) return;
        if (plugin.teleport().inSpawnRegion(p.getLocation())) e.setCancelled(true);
    }

    private boolean isConsumable(Material m) {
        if (m.isEdible()) return true; // all food
        return switch (m) {
            case POTION, HONEY_BOTTLE, MILK_BUCKET -> true;
            default -> false;
        };
    }
}
