package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /orders — browse buy orders. Left-click to fulfill (or cancel your own). */
public final class OrdersMenu extends Menu {

    private final MintSMP plugin;
    private int page;
    private final java.util.Map<Integer, Long> slotToId = new java.util.HashMap<>();
    private static final int PER_PAGE = 45;

    public OrdersMenu(MintSMP plugin, Player p, int page) {
        super(p, 6, "<gradient:#3DDC84:#1B5E20><bold>Buy Orders</bold></gradient>");
        this.plugin = plugin; this.page = Math.max(0, page);
    }

    @Override protected void build() {
        inventory.clear();
        slotToId.clear();
        List<Database.Order> orders = plugin.db().ordersList(null);
        int total = Math.max(1, (int) Math.ceil(orders.size() / (double) PER_PAGE));
        if (page >= total) page = total - 1;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < orders.size(); i++) {
            Database.Order o = orders.get(start + i);
            Material mat = Material.matchMaterial(o.material());
            ItemStack icon = new ItemStack(mat == null ? Material.PAPER : mat, Math.min(64, Math.max(1, o.quantity())));
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                boolean mine = o.buyer() != null && o.buyer().equals(player.getUniqueId());
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Items.noItalic(Msg.mm("<gray>Buyer: <white>" + o.buyerName())));
                lore.add(Items.noItalic(Msg.mm("<gray>Wants: <white>" + o.quantity() + "x")));
                lore.add(Items.noItalic(Msg.mm("<gray>Pays: <#A8FF60>" + Amounts.money(o.priceEach()) + " <gray>each")));
                lore.add(Items.noItalic(Msg.mm(mine ? "<red>Left-click to cancel & refund."
                        : "<gray>Left-click to <green>sell what you have</green>.")));
                m.lore(lore);
                icon.setItemMeta(m);
            }
            set(i, icon);
            slotToId.put(i, o.id());
        }
        for (int s = 45; s < 54; s++) set(s, Items.filler());
        if (page > 0) set(45, prevButton(page + 1, total));
        if (page < total - 1) set(53, nextButton(page + 1, total));
        set(49, closeButton());
        set(48, Items.of(Material.WRITABLE_BOOK, "<green>Create a buy order",
                "<gray>Hold an item, then use", "<white>/orders create <price> <quantity></white>."));
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && page > 0) { page--; build(); return; }
        if (slot == 53) { page++; build(); return; }
        Long id = slotToId.get(slot);
        if (id == null) return;
        Database.Order o = plugin.db().ordersGet(id);
        if (o == null) { Msg.sendRaw(player, "<red>That order is gone."); build(); return; }
        if (o.buyer() != null && o.buyer().equals(player.getUniqueId())) plugin.orders().cancel(player, o);
        else plugin.orders().fulfill(player, o);
        build();
    }
}
