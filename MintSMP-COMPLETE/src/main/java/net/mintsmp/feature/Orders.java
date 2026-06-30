package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Buy orders — the other half of the Auction House. A buyer posts "I'll buy
 * <item> at <price> each, up to <qty>" and locks the money in escrow. Sellers
 * fulfill the order; the buyer's items land in their /ah collect box.
 */
public final class Orders {

    private final MintSMP plugin;
    private final Economy economy;

    public Orders(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    public void create(Player p, double priceEach, int qty) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.sendRaw(p, "<red>Hold the item you want to buy."); return; }
        if (Items.hasInt(hand, Keys.NO_SELL) || Items.getString(hand, Keys.SPAWNER) != null
                || Items.getString(hand, Keys.AMETHYST) != null) {
            Msg.sendRaw(p, "<red>You can't place buy orders for that item."); return;
        }
        if (priceEach <= 0 || qty <= 0) { Msg.sendRaw(p, "<red>Price and quantity must be positive."); return; }
        int maxOrders = plugin.getConfig().getInt("orders.max-orders-per-player", 7);
        if (plugin.db().ordersCount(p.getUniqueId()) >= maxOrders) { Msg.sendRaw(p, "<red>Order limit reached (" + maxOrders + ")."); return; }
        double escrow = Math.round(priceEach * qty * 100.0) / 100.0;
        if (!economy.takeMoney(p.getUniqueId(), escrow)) {
            Msg.sendRaw(p, "<red>You need " + Amounts.money(escrow) + " to fund this order."); return;
        }
        long id = plugin.db().ordersCreate(p.getUniqueId(), p.getName(), hand.getType().name(), priceEach, qty, escrow);
        if (id < 0) { economy.addMoney(p.getUniqueId(), escrow); Msg.sendRaw(p, "<red>Could not create order."); return; }
        Msg.sendRaw(p, "<green>Buy order: <white>" + qty + "x " + nice(hand.getType()) + "</white> at <#A8FF60>"
                + Amounts.money(priceEach) + "</#A8FF60> each (escrow " + Amounts.money(escrow) + ").");
    }

    /** Seller fulfills as much of the order as they can from their inventory. */
    public void fulfill(Player seller, Database.Order o) {
        if (o == null) { Msg.sendRaw(seller, "<red>That order is gone."); return; }
        if (o.buyer() != null && o.buyer().equals(seller.getUniqueId())) { Msg.sendRaw(seller, "<red>You can't fulfill your own order."); return; }
        Material mat = Material.matchMaterial(o.material());
        if (mat == null) { Msg.sendRaw(seller, "<red>Invalid order item."); return; }
        int have = countOf(seller, mat);
        if (have <= 0) { Msg.sendRaw(seller, "<red>You have no " + nice(mat) + " to sell."); return; }
        int amount = Math.min(have, o.quantity());
        if (amount <= 0) { Msg.sendRaw(seller, "<red>Order already filled."); return; }
        double escrowSpent = Math.round(o.priceEach() * amount * 100.0) / 100.0;

        if (!plugin.db().ordersFulfill(o.id(), amount, escrowSpent)) {
            Msg.sendRaw(seller, "<red>Someone else just filled that order."); return;
        }
        removeOf(seller, mat, amount);
        economy.addMoney(seller.getUniqueId(), escrowSpent);
        // deliver items to the buyer's collection box
        if (o.buyer() != null) plugin.db().collectionAdd(o.buyer(), new ItemStack(mat, amount), "order");
        Msg.sendRaw(seller, "<green>Sold <white>" + amount + "x " + nice(mat) + "</white> for <#A8FF60>" + Amounts.money(escrowSpent) + "</#A8FF60>.");
    }

    public void cancel(Player p, Database.Order o) {
        if (o == null) return;
        if (o.buyer() == null || !o.buyer().equals(p.getUniqueId())) { Msg.sendRaw(p, "<red>That isn't your order."); return; }
        if (plugin.db().ordersDelete(o.id())) {
            economy.addMoney(p.getUniqueId(), o.escrow());
            Msg.sendRaw(p, "<gray>Order cancelled; <#A8FF60>" + Amounts.money(o.escrow()) + "</#A8FF60> refunded.");
        }
    }

    private int countOf(Player p, Material mat) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents())
            if (it != null && it.getType() == mat && !Items.hasInt(it, Keys.NO_SELL)) n += it.getAmount();
        return n;
    }

    private void removeOf(Player p, Material mat, int amount) {
        int remaining = amount;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (remaining <= 0) break;
            if (it == null || it.getType() != mat) continue;
            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            remaining -= take;
        }
    }

    private String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
