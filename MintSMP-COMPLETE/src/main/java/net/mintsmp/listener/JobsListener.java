package net.mintsmp.listener;

import net.mintsmp.MintSMP;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;

/** Pays players for mining / harvesting / fishing based on their chosen job. */
public final class JobsListener implements Listener {

    private final MintSMP plugin;
    public JobsListener(MintSMP plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        // mining
        plugin.jobs().onMine(p, b.getType());
        // farming: only pay for fully-grown crops
        if (b.getBlockData() instanceof Ageable age && age.getAge() == age.getMaximumAge()) {
            plugin.jobs().onHarvest(p, b.getType());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            plugin.jobs().onFish(e.getPlayer());
        }
    }
}
