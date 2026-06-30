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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /quickbuy — shop-style grid backed by the Auction House. Each distinct item
 * currently listed shows at its CHEAPEST price; a click buys that cheapest
 * listing with money.
 */
public final class QuickBuyMenu extends Menu {

    private final MintSMP plugin;
    private int page;
    private final java.util.Map<Integer, Material> slotToMat = new java.util.HashMap<>();
    private static final int PER_PAGE = 45;

    public QuickBuyMenu(MintSMP plugin, Player p, int page) {
        super(p, 6, "<gradient:#3DDC84:#1B5E20><bold>Quick Buy</bold></gradient>");
        this.plugin = plugin; this.page = Math.max(0, page);
    }

    /** material -> cheapest listing, plus a count of listings for that material. */
    private Map<Material, Database.Listing> cheapest() {
        Map<Material, Database.Listing> best = new LinkedHashMap<>();
        for (Database.Listing l : plugin.db().ahList(null)) {
            Material m = l.item().getType();
            Database.Listing cur = best.get(m);
            if (cur == null || l.price() < cur.price()) best.put(m, l);
        }
        return best;
    }

    @Override protected void build() {
        inventory.clear();
        slotToMat.clear();
        List<Map.Entry<Material, Database.Listing>> entries = new ArrayList<>(cheapest().entrySet());
        int total = Math.max(1, (int) Math.ceil(entries.size() / (double) PER_PAGE));
        if (page >= total) page = total - 1;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            Map.Entry<Material, Database.Listing> en = entries.get(start + i);
            Database.Listing l = en.getValue();
            ItemStack icon = l.item().clone();
            ItemMeta m = icon.getItemMeta();
            if (m != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                if (m.lore() != null) lore.addAll(m.lore());
                lore.add(Items.noItalic(Msg.mm("<gray>Cheapest: <#A8FF60>" + Amounts.money(l.price())
                        + " <gray>(x" + l.item().getAmount() + ")")));
                lore.add(Items.noItalic(Msg.mm("<gray>Seller: <white>" + l.sellerName())));
                lore.add(Items.noItalic(Msg.mm("<gray>Click to <green>buy the cheapest</green>.")));
                m.lore(lore);
                icon.setItemMeta(m);
            }
            set(i, icon);
            slotToMat.put(i, en.getKey());
        }
        for (int s = 45; s < 54; s++) set(s, Items.filler());
        if (page > 0) set(45, prevButton(page + 1, total));
        if (page < total - 1) set(53, nextButton(page + 1, total));
        set(49, closeButton());
        set(48, Items.of(Material.OAK_HANGING_SIGN, "<green>Quick Buy",
                "<gray>Buys the cheapest Auction House", "<gray>listing for each item, instantly."));
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && page > 0) { page--; build(); return; }
        if (slot == 53) { page++; build(); return; }
        Material mat = slotToMat.get(slot);
        if (mat == null) return;

        // re-resolve the current cheapest listing for this material
        Database.Listing best = null;
        for (Database.Listing l : plugin.db().ahList(null)) {
            if (l.item().getType() != mat) continue;
            if (best == null || l.price() < best.price()) best = l;
        }
        if (best == null) { Msg.sendRaw(player, "<red>No listings left for that item."); build(); return; }
        if (best.seller() != null && best.seller().equals(player.getUniqueId())) {
            Msg.sendRaw(player, "<red>That's your own listing — use /ah to manage it."); return;
        }
        double price = best.price();
        if (!plugin.economy().takeMoney(player.getUniqueId(), price)) {
            Msg.sendRaw(player, "<red>You can't afford that (" + Amounts.money(price) + ").");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!plugin.db().ahDelete(best.id())) {
            plugin.economy().addMoney(player.getUniqueId(), price);
            Msg.sendRaw(player, "<red>Too late — already sold."); build(); return;
        }
        double tax = plugin.getConfig().getDouble("auction-house.sale-tax-percent", 5.0) / 100.0;
        if (best.seller() != null) plugin.economy().addMoneyAnywhere(best.seller(), price * (1 - tax));
        for (ItemStack rem : player.getInventory().addItem(best.item()).values())
            plugin.db().collectionAdd(player.getUniqueId(), rem, "quickbuy-overflow");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        Msg.sendRaw(player, "<green>Bought <white>" + best.item().getAmount() + "x " + nice(mat)
                + "</white> for <#A8FF60>" + Amounts.money(price) + "</#A8FF60>.");
        build();
    }

    private String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
