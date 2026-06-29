package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom (non-vanilla) spawners. They never spawn mobs. Right-click a placed
 * spawner -> drops go straight to the player, then a per-spawner cooldown.
 * 1500 shards each (bought elsewhere/crate). Cannot be /sell'd or /ah'd.
 */
public final class Spawners {

    private final MintSMP plugin;
    private YamlConfiguration cfg;
    private int defaultCooldown = 30;
    private final Map<String, Long> cooldowns = new HashMap<>(); // worldXYZ -> next-use epoch

    public Spawners(MintSMP plugin) { this.plugin = plugin; }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "spawners.yml");
        if (!f.exists()) plugin.saveResource("spawners.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        defaultCooldown = cfg.getInt("spawners.default-cooldown-seconds", 30);
    }

    public boolean isType(String type) {
        return cfg.getConfigurationSection("spawners.types." + type) != null;
    }

    public List<String> types() {
        ConfigurationSection sec = cfg.getConfigurationSection("spawners.types");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public ItemStack createItem(String type) {
        String display = cfg.getString("spawners.types." + type + ".display", "<green>" + type + " Spawner");
        ItemStack it = Items.of(Material.SPAWNER, display,
                "<gray>Right-click placed to harvest drops.",
                "<gray>Cannot be sold or auctioned.",
                "<dark_gray>Trade via Discord Market only.");
        Items.tagString(it, Keys.SPAWNER, type);
        Items.tagInt(it, Keys.NO_SELL, 1);
        return it;
    }

    public boolean give(Player target, String type, int amount) {
        if (!isType(type)) return false;
        ItemStack it = createItem(type);
        it.setAmount(Math.max(1, amount));
        target.getInventory().addItem(it);
        return true;
    }

    public void onPlace(Block block, ItemStack inHand) {
        String type = Items.getString(inHand, Keys.SPAWNER);
        if (type != null) plugin.db().saveSpawner(block.getLocation(), type);
    }

    public boolean onBreak(Block block, Player p, List<ItemStack> dropOut) {
        String type = plugin.db().getSpawner(block.getLocation());
        if (type == null) return false;
        int stack = plugin.spawnerStack().count(block);
        plugin.db().removeSpawner(block.getLocation());
        ItemStack drop = createItem(type);
        drop.setAmount(Math.max(1, Math.min(drop.getMaxStackSize(), stack)));
        dropOut.add(drop);
        return true;
    }

    /** Preview items a spawner type can drop (for the stack GUI). */
    public List<ItemStack> dropPreview(String type) {
        List<ItemStack> out = new ArrayList<>();
        for (Map<?, ?> entry : cfg.getMapList("spawners.types." + type + ".drops")) {
            Object matName = entry.get("material");
            if (matName == null) continue;
            Material mat = Material.matchMaterial(matName.toString());
            if (mat != null) out.add(new ItemStack(mat));
        }
        return out;
    }

    /** Returns true if this block is a custom spawner and a harvest was attempted. */
    public boolean onRightClick(Block block, Player p) {
        String type = plugin.db().getSpawner(block.getLocation());
        if (type == null) return false;
        String key = keyOf(block.getLocation());
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(key);
        if (until != null && until > now) {
            long secs = (until - now + 999) / 1000;
            Msg.sendRaw(p, "<red>Spawner on cooldown: <white>" + secs + "s");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return true;
        }
        List<ItemStack> drops = rollDrops(type);
        int stack = plugin.spawnerStack().count(block);
        if (stack > 1) for (ItemStack d : drops) d.setAmount(Math.min(d.getMaxStackSize(), d.getAmount() * stack));
        for (ItemStack d : drops) p.getInventory().addItem(d).values()
                .forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
        int cd = cfg.getInt("spawners.types." + type + ".cooldown-seconds", defaultCooldown);
        cooldowns.put(key, now + cd * 1000L);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        return true;
    }

    private List<ItemStack> rollDrops(String type) {
        List<ItemStack> out = new ArrayList<>();
        List<Map<?, ?>> list = cfg.getMapList("spawners.types." + type + ".drops");
        for (Map<?, ?> m : list) {
            try {
                Material mat = Material.valueOf(String.valueOf(m.get("material")));
                int min = m.get("min") == null ? 1 : ((Number) m.get("min")).intValue();
                int max = m.get("max") == null ? min : ((Number) m.get("max")).intValue();
                int amt = max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
                if (amt > 0) out.add(new ItemStack(mat, amt));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private String keyOf(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
}
