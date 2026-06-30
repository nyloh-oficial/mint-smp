package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /ah — browse listings and buy. Selling/collecting handled in the command router. */
public final class AHMenu extends Menu {

    private final MintSMP plugin;
    private final String search;
    private int page;
    private List<Database.Listing> listings = new ArrayList<>();
    private final java.util.Map<Integer, Long> slotToId = new java.util.HashMap<>();
    private static final int PER_PAGE = 45;

    public AHMenu(MintSMP plugin, Player p, String search, int page) {
        super(p, 6, "<gradient:#3DDC84:#1B5E20><bold>Auction House</bold></gradient>");
        this.plugin = plugin; this.search = search; this.page = Math.max(0, page);
    }

    @Override protected void build() {
        inventory.clear();
        slotToId.clear();
        listings = plugin.db().ahList(search);
        int total = Math.max(1, (int) Math.ceil(listings.size() / (double) PER_PAGE));
        if (page >= total) page = total - 1;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < listings.size(); i++) {
            Database.Listing l = listings.get(start + i);
            ItemStack icon = l.item().clone();
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                if (m.lore() != null) lore.addAll(m.lore());
                lore.add(Items.noItalic(Msg.mm("<gray>Seller: <white>" + l.sellerName())));
                lore.add(Items.noItalic(Msg.mm("<gray>Price: <#A8FF60>" + Amounts.money(l.price()))));
                lore.add(Items.noItalic(Msg.mm("<gray>Left-click to <green>buy</green>.")));
                m.lore(lore);
                icon.setItemMeta(m);
            }
            set(i, icon);
            slotToId.put(i, l.id());
        }
        // bottom bar
        for (int s = 45; s < 54; s++) set(s, Items.filler());
        if (page > 0) set(45, prevButton(page + 1, total));
        if (page < total - 1) set(53, nextButton(page + 1, total));
        set(49, closeButton());
        set(48, Items.of(Material.GOLD_INGOT, "<green>Sell an item",
                "<gray>Hold an item and use <white>/ah sell <price></white>."));
        set(50, Items.of(Material.CHEST, "<green>Collection",
                "<gray>Use <white>/ah collect</white> for sold/expired items."));
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && page > 0) { page--; build(); return; }
        if (slot == 53) { page++; build(); return; }
        Long id = slotToId.get(slot);
        if (id == null) return;
        Database.Listing l = plugin.db().ahGet(id);
        if (l == null) { Msg.sendRaw(player, "<red>That listing is gone."); build(); return; }
        if (l.seller() != null && l.seller().equals(player.getUniqueId())) {
            // cancel own listing -> return item to collection
            if (plugin.db().ahDelete(id)) {
                plugin.db().collectionAdd(player.getUniqueId(), l.item(), "cancelled");
                Msg.sendRaw(player, "<gray>Listing cancelled; item in <white>/ah collect</white>.");
            }
            build(); return;
        }
        double price = l.price();
        if (!plugin.economy().takeMoney(player.getUniqueId(), price)) {
            Msg.sendRaw(player, "<red>You can't afford that (" + Amounts.money(price) + ").");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!plugin.db().ahDelete(id)) { // someone bought first
            plugin.economy().addMoney(player.getUniqueId(), price);
            Msg.sendRaw(player, "<red>Too late — already sold."); build(); return;
        }
        double tax = plugin.getConfig().getDouble("auction-house.sale-tax-percent", 5.0) / 100.0;
        double payout = price * (1 - tax);
        if (l.seller() != null) plugin.economy().addMoneyAnywhere(l.seller(), payout);
        for (ItemStack rem : player.getInventory().addItem(l.item()).values())
            plugin.db().collectionAdd(player.getUniqueId(), rem, "overflow");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        Msg.sendRaw(player, "<green>Bought for <#A8FF60>" + Amounts.money(price) + "</#A8FF60>.");
        build();
    }
}
