package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/** /home (no args) — click a home to teleport. */
public final class HomeMenu extends Menu {

    private final MintSMP plugin;
    private final List<String> homes = new ArrayList<>();

    public HomeMenu(MintSMP plugin, Player p) {
        super(p, 6, "<gradient:#3DDC84:#A8FF60><bold>Your Homes</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        homes.clear();
        homes.addAll(plugin.db().homeNames(player.getUniqueId()));
        int slot = 10;
        for (String name : homes) {
            if (slot >= 44) break;
            set(slot, Items.of(Material.RED_BED, "<green>" + name,
                    "<gray>Click to teleport here.",
                    "<dark_gray>/delhome " + name + " to remove"));
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        if (homes.isEmpty())
            set(22, Items.of(Material.BARRIER, "<red>No homes set",
                    "<gray>Use <white>/sethome <name></white> to make one."));
        set(49, closeButton());
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        int idx = indexForSlot(slot);
        if (idx < 0 || idx >= homes.size()) return;
        String name = homes.get(idx);
        player.closeInventory();
        plugin.teleport().home(player, name);
    }

    private int indexForSlot(int slot) {
        int idx = 0, s = 10;
        while (idx < homes.size() && s < 44) {
            if (s == slot) return idx;
            idx++; s++;
            if (s % 9 == 8) s += 2;
        }
        return -1;
    }
}
