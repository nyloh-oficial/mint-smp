package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * /sell (no args) opens this window. Player drops items in; on close, every
 * sellable item is sold and anything unsellable is returned. Unlike Menu, this
 * inventory is editable (clicks are NOT cancelled).
 */
public final class SellWindow implements InventoryHolder {

    private Inventory inv;

    @NotNull @Override public Inventory getInventory() { return inv; }

    public static void open(MintSMP plugin, Player p) {
        SellWindow holder = new SellWindow();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.mm("<gradient:#3DDC84:#A8FF60><bold>Sell</bold></gradient> <dark_gray>(close to sell)"));
        holder.inv = inv;
        p.openInventory(inv);
    }

    /** Called from the close listener: sell everything sellable, return the rest. */
    public static void onClose(MintSMP plugin, Player p, Inventory inv) {
        double total = 0;
        int sold = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (plugin.prices().canSell(it)) {
                int qty = it.getAmount();
                double paid = plugin.prices().sellStack(p, it); // sets amount to 0
                if (paid > 0) { total += paid; sold += qty; }
                inv.setItem(i, null);
            } else {
                // return unsellable
                for (ItemStack rem : p.getInventory().addItem(it).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), rem);
                inv.setItem(i, null);
            }
        }
        if (sold > 0) {
            Msg.sendRaw(p, "<green>Sold <white>" + sold + "</white> items for <gradient:#3DDC84:#A8FF60>" + Amounts.money(total));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }
}
