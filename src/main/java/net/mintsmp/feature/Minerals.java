package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Mint Ore generates deep underground (see MintOrePopulator). Mining it drops
 * Mint Minerals. Minerals are spent in the Mint Forge GUI to craft mythic gear,
 * which still enforces the lifetime 5-per-type cap via Mythic.mint().
 */
public final class Minerals {

    private final MintSMP plugin;
    private Material oreMaterial = Material.NETHER_QUARTZ_ORE;
    private Material mineralBase = Material.ECHO_SHARD;
    private int costPerMythic = 32;
    private int modelData = 30001;

    public Minerals(MintSMP plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        var c = plugin.getConfig();
        oreMaterial = matOr(c.getString("minerals.ore-material", "NETHER_QUARTZ_ORE"), Material.NETHER_QUARTZ_ORE);
        mineralBase = matOr(c.getString("minerals.mineral-base", "ECHO_SHARD"), Material.ECHO_SHARD);
        costPerMythic = c.getInt("minerals.cost-per-mythic", 32);
        modelData = c.getInt("minerals.mineral-model-data", 30001);
    }

    private Material matOr(String name, Material def) {
        try { return Material.valueOf(name.toUpperCase()); } catch (Exception e) { return def; }
    }

    public Material oreMaterial() { return oreMaterial; }
    public int costPerMythic() { return costPerMythic; }

    /** Build a single Mint Mineral item. */
    public ItemStack mineral(int amount) {
        ItemStack it = Items.of(mineralBase,
                "<gradient:#3DDC84:#A8FF60><bold>Mint Mineral</bold></gradient>",
                "<gray>Refine these in the <green>Mint Forge",
                "<gray>to craft mythic gear.",
                "<dark_gray>Not sellable.");
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setCustomModelData(modelData);
            it.setItemMeta(m);
        }
        Items.tagInt(it, Keys.MINERAL, 1);
        Items.tagInt(it, Keys.NO_SELL, 1);
        it.setAmount(Math.max(1, Math.min(amount, mineralBase.getMaxStackSize())));
        return it;
    }

    public boolean isMineral(ItemStack it) { return Items.hasInt(it, Keys.MINERAL); }

    public int count(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && isMineral(it)) n += it.getAmount();
        return n;
    }

    /** Remove up to 'amount' minerals from the player's inventory. */
    private void take(Player p, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !isMineral(it)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
        }
        p.updateInventory();
    }

    /** Attempt to forge a mythic of the given type using minerals. */
    public void forge(Player p, String type) {
        if (!plugin.mythic().exists(type)) { Msg.send(p, "<red>Unknown mythic type."); return; }
        if (plugin.mythic().remaining(type) <= 0) {
            Msg.send(p, "<red>" + plugin.mythic().display(type) + " <red>is sold out forever.");
            return;
        }
        int have = count(p);
        if (have < costPerMythic) {
            Msg.send(p, "<red>You need <yellow>" + costPerMythic + "<red> Mint Minerals (you have <yellow>" + have + "<red>).");
            return;
        }
        take(p, costPerMythic);
        boolean ok = plugin.mythic().mint(p, type, false);
        if (!ok) {
            // sold out between check and mint, or inventory full — refund
            p.getInventory().addItem(mineral(costPerMythic));
            Msg.send(p, "<red>Forge failed — minerals refunded.");
            return;
        }
        Msg.send(p, "<gradient:#3DDC84:#A8FF60>Forged " + plugin.mythic().display(type) + "<green>!");
    }
}
