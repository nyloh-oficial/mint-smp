package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import net.mintsmp.feature.Settings;
import net.mintsmp.gui.SellWindow;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Hooks for Mint tools (instamine), the Sell GUI, and the /settings toggles. */
public final class ExtraListener implements Listener {

    private final MintSMP plugin;
    public ExtraListener(MintSMP plugin) { this.plugin = plugin; }

    /** Mint Pickaxe/Axe break the first block instantly. */
    @EventHandler
    public void onDamage(BlockDamageEvent e) {
        if (plugin.amethyst().isInstaTool(e.getItemInHand())) {
            e.setInstaBreak(true);
        }
    }

    /** Sell GUI: sell contents when the window closes. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SellWindow && e.getPlayer() instanceof Player p) {
            SellWindow.onClose(plugin, p, e.getInventory());
        }
    }

    /** Fall-damage immunity setting. */
    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p
                && plugin.settings().get(p, Settings.FALL_IMMUNE, false)) {
            e.setCancelled(true);
        }
    }

    /** "Hostile Mobs" setting off -> monsters won't target the player. */
    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && e.getEntity() instanceof Monster
                && !plugin.settings().get(p, Settings.MOBS, true)) {
            e.setCancelled(true);
        }
    }
}
