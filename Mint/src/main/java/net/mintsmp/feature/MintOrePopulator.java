package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Sprinkles Mint Ore into newly generated chunks, deep underground and rare
 * (diamond-ish). Only replaces stone/deepslate so it never eats other ores.
 */
public final class MintOrePopulator extends BlockPopulator {

    private final MintSMP plugin;
    private final Material ore;
    private final int minY, maxY, chancePerChunk, maxVein;

    public MintOrePopulator(MintSMP plugin) {
        this.plugin = plugin;
        var c = plugin.getConfig();
        Material m;
        try { m = Material.valueOf(c.getString("minerals.ore-material", "NETHER_QUARTZ_ORE").toUpperCase()); }
        catch (Exception e) { m = Material.NETHER_QUARTZ_ORE; }
        this.ore = m;
        this.minY = c.getInt("minerals.ore.min-y", -60);
        this.maxY = c.getInt("minerals.ore.max-y", 16);
        this.chancePerChunk = c.getInt("minerals.ore.chance-per-chunk-percent", 28); // ~1 in 3.5 chunks
        this.maxVein = c.getInt("minerals.ore.max-vein", 3);
    }

    @Override
    public void populate(WorldInfo world, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (random.nextInt(100) >= chancePerChunk) return;
        int veins = 1 + random.nextInt(Math.max(1, maxVein));
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int v = 0; v < veins; v++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            if (!region.isInRegion(x, y, z)) continue;
            Material here = region.getType(x, y, z);
            if (here == Material.STONE || here == Material.DEEPSLATE) {
                region.setType(x, y, z, ore);
            }
        }
    }
}
