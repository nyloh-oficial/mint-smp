package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Amethyst items (from the Amethyst Crate). Each has a 3-day self-destruct timer
 * stored in the item's PDC; expired items are removed when used/held. All
 * amethyst items are non-sellable.
 */
public final class Amethyst {

    private final MintSMP plugin;
    private final Economy economy;
    private YamlConfiguration cfg;
    private int selfDestructDays = 3;

    // active shard boosters: player -> expiry epoch
    private final Map<UUID, Long> boosters = new HashMap<>();

    public Amethyst(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "amethyst.yml");
        if (!f.exists()) plugin.saveResource("amethyst.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        selfDestructDays = cfg.getInt("amethyst.self-destruct-days", 3);
    }

    public boolean isType(String type) {
        return cfg.getConfigurationSection("amethyst.items." + type) != null;
    }

    public ItemStack create(String type) {
        Material base = switch (type) {
            case "PICKAXE" -> Material.DIAMOND_PICKAXE;
            case "AXE" -> Material.DIAMOND_AXE;
            case "SHOVEL" -> Material.DIAMOND_SHOVEL;
            case "BUCKET" -> Material.BUCKET;
            case "SHARD_BOOSTER" -> Material.AMETHYST_SHARD;
            default -> Material.AMETHYST_SHARD;
        };
        String display = cfg.getString("amethyst.items." + type + ".display", "<aqua>Amethyst " + type);
        long expire = System.currentTimeMillis() + selfDestructDays * 24L * 3600_000L;
        ItemStack it = Items.of(base, display,
                "<gray>Self-destructs in <white>" + selfDestructDays + " days</white>.",
                "<dark_gray>Non-sellable.");
        Items.tagString(it, Keys.AMETHYST, type);
        Items.tagLong(it, Keys.AMETHYST_EXPIRE, expire);
        Items.tagInt(it, Keys.NO_SELL, 1);
        return it;
    }

    /** True if the item is an expired amethyst item (caller should remove it). */
    public boolean isExpired(ItemStack it) {
        Long exp = Items.getLong(it, Keys.AMETHYST_EXPIRE);
        return exp != null && exp < System.currentTimeMillis();
    }

    public String typeOf(ItemStack it) { return Items.getString(it, Keys.AMETHYST); }

    // ---- shard booster ----------------------------------------------------

    public boolean consumeBooster(Player p, ItemStack it) {
        if (isExpired(it)) { it.setAmount(0); Msg.sendRaw(p, "<red>That Amethyst item expired."); return true; }
        int mult = cfg.getInt("amethyst.items.SHARD_BOOSTER.multiplier", 4);
        int hours = cfg.getInt("amethyst.items.SHARD_BOOSTER.duration-hours", 24);
        boosters.put(p.getUniqueId(), System.currentTimeMillis() + hours * 3600_000L);
        it.setAmount(it.getAmount() - 1);
        Msg.sendRaw(p, "<#A8FF60>Shard booster active: <white>" + mult + "x</white> for <white>" + hours + "h</white>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }

    public int boosterMultiplier(UUID uuid) {
        Long until = boosters.get(uuid);
        if (until != null && until > System.currentTimeMillis())
            return cfg.getInt("amethyst.items.SHARD_BOOSTER.multiplier", 4);
        return 1;
    }

    // ---- tool effects -----------------------------------------------------

    /** Handle an amethyst-tool block break. Returns true if handled (event should be cancelled). */
    public boolean onBreak(Player p, Block origin, ItemStack tool) {
        String type = typeOf(tool);
        if (type == null) return false;
        if (isExpired(tool)) { tool.setAmount(0); Msg.sendRaw(p, "<red>Your Amethyst tool crumbled to dust."); return true; }
        switch (type) {
            case "PICKAXE" -> breakCube(p, origin, tool, Material.STONE);
            case "SHOVEL"  -> breakCube(p, origin, tool, Material.DIRT);
            case "AXE"     -> treeFeller(p, origin, tool);
            default        -> { return false; }
        }
        return true;
    }

    private void breakCube(Player p, Block c, ItemStack tool, Material ignoredHint) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = c.getRelative(dx, dy, dz);
                    if (b.getType().isAir() || b.getType() == Material.BEDROCK) continue;
                    if (b.getType().getHardness() < 0) continue;
                    b.breakNaturally(tool);
                    economy.addBlock(p.getUniqueId());
                }
    }

    private void treeFeller(Player p, Block start, ItemStack tool) {
        int cap = cfg.getInt("amethyst.items.AXE.max-blocks", 500);
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        int broken = 0;
        BlockFace[] faces = BlockFace.values();
        while (!queue.isEmpty() && broken < cap) {
            Block b = queue.poll();
            if (!seen.add(b)) continue;
            if (!isLog(b.getType())) continue;
            b.breakNaturally(tool);
            economy.addBlock(p.getUniqueId());
            broken++;
            for (BlockFace f : faces) {
                Block n = b.getRelative(f);
                if (!seen.contains(n) && isLog(n.getType())) queue.add(n);
            }
        }
    }

    private boolean isLog(Material m) { return m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE"); }

    /** Amethyst bucket: drain up to N water blocks near target. */
    public void drainWater(Player p, Block target, ItemStack tool) {
        if (isExpired(tool)) { tool.setAmount(0); Msg.sendRaw(p, "<red>Your Amethyst bucket dissolved."); return; }
        int cap = cfg.getInt("amethyst.items.BUCKET.max-blocks", 27);
        int drained = 0;
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty() && drained < cap) {
            Block b = queue.poll();
            if (!seen.add(b)) continue;
            if (b.getType() != Material.WATER) continue;
            b.setType(Material.AIR);
            drained++;
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block n = b.getRelative(f);
                if (!seen.contains(n) && n.getType() == Material.WATER) queue.add(n);
            }
        }
        if (drained > 0) Msg.sendRaw(p, "<aqua>Drained <white>" + drained + "</white> water blocks.");
    }
}
