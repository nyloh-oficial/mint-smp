package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
        boolean rtp = !p.hasPlayedBefore() || !plugin.teleport().isServerSpawnSet();
        if (rtp) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.teleport().rtp(p, "overworld");
            }, 25L);
        }
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
        if (!staff(e.getPlayer()) && plugin.teleport().inSpawnRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            Msg.sendRaw(e.getPlayer(), "<red>You can't break blocks at spawn.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!staff(e.getPlayer()) && plugin.teleport().inSpawnRegion(e.getBlock().getLocation())) {
            e.setCancelled(true);
            Msg.sendRaw(e.getPlayer(), "<red>You can't place blocks at spawn.");
        }
    }
}
