package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /mint forge — spend Mint Minerals to craft mythic gear (5-per-type cap enforced). */
public final class ForgeMenu extends Menu {

    private final MintSMP plugin;
    private final List<String> slotType = new ArrayList<>();

    public ForgeMenu(MintSMP plugin, Player p) {
        super(p, 6, "<gradient:#3DDC84:#A8FF60><bold>Mint Forge</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        slotType.clear();
        int cost = plugin.minerals().costPerMythic();
        int have = plugin.minerals().count(player);

        // header: your minerals
        set(4, Items.of(Material.ECHO_SHARD,
                "<gradient:#3DDC84:#A8FF60><bold>Your Mint Minerals: " + have + "</bold></gradient>",
                "<gray>Each mythic costs <yellow>" + cost + "<gray> minerals.",
                "<gray>Mine <green>Mint Ore</green> deep underground to get more."));

        int slot = 10;
        for (String type : plugin.mythic().types()) {
            int remaining = plugin.mythic().remaining(type);
            int capN = plugin.mythic().cap(type);
            Material mat = plugin.mythic().material(type);
            boolean soldOut = remaining <= 0;
            boolean canAfford = have >= cost;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Stock: " + (soldOut ? "<red>SOLD OUT" : "<yellow>" + remaining + "<gray>/<white>" + capN) + " <gray>left");
            lore.add("<gray>Cost: <yellow>" + cost + " <gray>Mint Minerals");
            lore.add("");
            if (soldOut) lore.add("<red>No longer craftable.");
            else if (!canAfford) lore.add("<red>Not enough minerals.");
            else lore.add("<green>Click to forge!");
            ItemStack icon = Items.named(mat == null ? Material.NETHER_STAR : mat,
                    plugin.mythic().display(type), lore);
            set(slot, icon);
            slotType.add(type);
            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
        }
        set(49, closeButton());
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        int idx = indexForSlot(slot);
        if (idx < 0 || idx >= slotType.size()) return;
        plugin.minerals().forge(player, slotType.get(idx));
        build();
    }

    private int indexForSlot(int slot) {
        int idx = 0, s = 10;
        while (idx < slotType.size() && s < 44) {
            if (s == slot) return idx;
            idx++; s++;
            if (s % 9 == 8) s += 2;
        }
        return -1;
    }
}
