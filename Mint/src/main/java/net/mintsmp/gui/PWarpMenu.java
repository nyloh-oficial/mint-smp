package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /pwarp — browse player warps. Left-click to visit; shift-click your own to delete. */
public final class PWarpMenu extends Menu {

    private final MintSMP plugin;
    private int page;
    private final java.util.Map<Integer, String> slotToName = new java.util.HashMap<>();
    private static final int PER_PAGE = 45;

    public PWarpMenu(MintSMP plugin, Player p, int page) {
        super(p, 6, "<gradient:#3DDC84:#1B5E20><bold>Player Warps</bold></gradient>");
        this.plugin = plugin; this.page = Math.max(0, page);
    }

    @Override protected void build() {
        inventory.clear();
        slotToName.clear();
        List<Database.PWarp> warps = plugin.db().pwarpList();
        int total = Math.max(1, (int) Math.ceil(warps.size() / (double) PER_PAGE));
        if (page >= total) page = total - 1;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < warps.size(); i++) {
            Database.PWarp w = warps.get(start + i);
            boolean mine = w.owner() != null && w.owner().equals(player.getUniqueId());
            ItemStack icon = Items.of(Material.LODESTONE, "<#3DDC84>" + w.name(),
                    "<gray>Owner: <white>" + w.ownerName(),
                    "<gray>Visits: <white>" + w.visits(),
                    mine ? "<gray>Left-click: <green>visit  <gray>| Shift: <red>delete"
                         : "<gray>Left-click to <green>visit</green>.");
            set(i, icon);
            slotToName.put(i, w.name());
        }
        for (int s = 45; s < 54; s++) set(s, Items.filler());
        if (page > 0) set(45, prevButton(page + 1, total));
        if (page < total - 1) set(53, nextButton(page + 1, total));
        set(49, closeButton());
        set(48, Items.of(Material.LODESTONE, "<green>Create a warp here",
                "<gray>Use <white>/pwarp set <name></white> where you stand."));
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && page > 0) { page--; build(); return; }
        if (slot == 53) { page++; build(); return; }
        String name = slotToName.get(slot);
        if (name == null) return;
        Database.PWarp w = plugin.db().pwarpGet(name);
        if (w == null) { build(); return; }
        boolean mine = w.owner() != null && w.owner().equals(player.getUniqueId());
        if (mine && click.isShiftClick()) { plugin.playerWarps().delete(player, name); build(); return; }
        player.closeInventory();
        plugin.playerWarps().visit(player, name);
    }
}
