package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Keys;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.persistence.PersistentDataType;

/**
 * Donut-style spawner stacking. The stack count lives in the spawner block's
 * own PersistentDataContainer (CreatureSpawner is a TileState), so it persists
 * with the block. Harvest yield scales with the stack (see Spawners.onRightClick).
 */
public final class SpawnerStack {

    private final MintSMP plugin;
    private int maxStack = 64;
    public SpawnerStack(MintSMP plugin) { this.plugin = plugin; this.maxStack = plugin.getConfig().getInt("spawners.max-stack", 64); }

    public int maxStack() { return maxStack; }

    public int count(Block block) {
        if (!(block.getState() instanceof CreatureSpawner cs)) return 1;
        Integer n = cs.getPersistentDataContainer().get(Keys.SPAWNER_STACK, PersistentDataType.INTEGER);
        return n == null ? 1 : Math.max(1, n);
    }

    public void setCount(Block block, int n) {
        if (!(block.getState() instanceof CreatureSpawner cs)) return;
        int v = Math.max(1, Math.min(maxStack, n));
        cs.getPersistentDataContainer().set(Keys.SPAWNER_STACK, PersistentDataType.INTEGER, v);
        cs.update(true, false);
    }

    /** Try to add to the stack. Returns the amount actually added (0 if full). */
    public int add(Block block, int amount) {
        int cur = count(block);
        int room = maxStack - cur;
        if (room <= 0) return 0;
        int add = Math.min(room, amount);
        setCount(block, cur + add);
        return add;
    }
}
