package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import net.mintsmp.gui.Menu;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MintListener implements Listener {

    private final MintSMP plugin;
    public MintListener(MintSMP plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.economy().load(p.getUniqueId(), p.getName());
        plugin.afk().touch(p);
        plugin.display().update(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.duels().onQuit(p);
        plugin.combat().onQuit(p);
        plugin.extras().clear(p.getUniqueId());
        plugin.economy().unload(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.economy().addDeath(victim.getUniqueId());
        plugin.extras().setBack(victim, victim.getLocation());
        plugin.duels().onDeath(victim);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            plugin.economy().addKill(killer.getUniqueId());
            long reward = plugin.getConfig().getLong("shards.earn.pvp-kill", 10)
                    * plugin.amethyst().boosterMultiplier(killer.getUniqueId());
            plugin.economy().addShards(killer.getUniqueId(), reward);
            plugin.combat().payoutBounty(killer, victim.getUniqueId());
            plugin.combat().clear(killer);
        }
        plugin.combat().clear(victim);
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Player attacker) {
            // Duels always allow damage between the two participants.
            if (plugin.duels().dueling(attacker, victim) || plugin.duels().dueling(victim, attacker)) {
                plugin.combat().tag(attacker, victim);
                return;
            }
            // Friendly fire: block damage between teammates when disabled.
            if (plugin.teams().friendlyFireBlocked(attacker, victim)) {
                e.setCancelled(true);
                return;
            }
            plugin.combat().tag(attacker, victim);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        ItemStack tool = p.getInventory().getItemInMainHand();

        // Honeypot trap: breaking a trapped container flags the player.
        if (plugin.db().isTrap(block.getLocation())) {
            plugin.staff().triggered(p, block.getLocation(), "broke");
        }

        String amethyst = plugin.amethyst().typeOf(tool);
        if (amethyst != null && (amethyst.equals("PICKAXE") || amethyst.equals("SHOVEL") || amethyst.equals("AXE"))) {
            e.setCancelled(true);
            plugin.amethyst().onBreak(p, block, tool);
            return;
        }

        if (block.getType() == Material.SPAWNER) {
            List<ItemStack> drops = new ArrayList<>();
            if (plugin.spawners().onBreak(block, p, drops)) {
                e.setDropItems(false);
                for (ItemStack d : drops) block.getWorld().dropItemNaturally(block.getLocation(), d);
                return;
            }
        }
        plugin.economy().addBlock(p.getUniqueId());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.SPAWNER) {
            plugin.spawners().onPlace(e.getBlock(), e.getItemInHand());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        String amethyst = plugin.amethyst().typeOf(tool);

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Block block = e.getClickedBlock();
            if (block.getType() == Material.SPAWNER) {
                if (plugin.spawners().onRightClick(block, p)) { e.setCancelled(true); return; }
            }
            if ("BUCKET".equals(amethyst)) {
                Block target = block.getType() == Material.WATER ? block : block.getRelative(e.getBlockFace());
                plugin.amethyst().drainWater(p, target, tool);
                e.setCancelled(true);
                return;
            }
        }

        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && "SHARD_BOOSTER".equals(amethyst)) {
            plugin.amethyst().consumeBooster(p, tool);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        org.bukkit.Location loc = e.getInventory().getLocation();
        if (loc != null && plugin.db().isTrap(loc)) {
            if (plugin.staff().triggered(p, loc, "opened")) {
                if (!plugin.staff().isStaff(p)) e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu && e.getWhoClicked() instanceof Player) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null) return;
            if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
            menu.onClick(e.getRawSlot(), e.getClick(), e);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            plugin.afk().touch(e.getPlayer());
        }
    }
}
