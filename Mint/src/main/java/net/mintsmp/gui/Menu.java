package net.mintsmp.gui;

import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base chest menu. Implements InventoryHolder so the click listener can detect
 * plugin GUIs and route clicks here. All clicks in a Menu are cancelled.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player player;
    protected Inventory inventory;
    private final int rows;
    private final String titleMini;

    protected Menu(Player player, int rows, String titleMini) {
        this.player = player;
        this.rows = Math.max(1, Math.min(6, rows));
        this.titleMini = titleMini;
    }

    @NotNull @Override public Inventory getInventory() { return inventory; }

    public void open() {
        inventory = Bukkit.createInventory(this, rows * 9, Msg.mm(titleMini));
        build();
        player.openInventory(inventory);
    }

    protected void refresh() { build(); }

    /** Populate inventory contents. */
    protected abstract void build();

    /** Handle a cancelled click. */
    public abstract void onClick(int slot, ClickType click, InventoryClickEvent event);

    // ---- helpers ----------------------------------------------------------

    protected void fillBorder() {
        ItemStack pane = Items.filler();
        for (int s : Items.border(rows)) inventory.setItem(s, pane);
    }

    protected void set(int slot, ItemStack item) { inventory.setItem(slot, item); }

    protected ItemStack closeButton() {
        return Items.of(Material.BARRIER, "<red>Close");
    }
    protected ItemStack backButton() {
        return Items.of(Material.ARROW, "<yellow>Back");
    }
    protected ItemStack nextButton(int page, int total) {
        return Items.of(Material.ARROW, "<green>Next \u00bb",
                "<gray>Page <white>" + page + "</white>/<white>" + total + "</white>");
    }
    protected ItemStack prevButton(int page, int total) {
        return Items.of(Material.ARROW, "<green>\u00ab Prev",
                "<gray>Page <white>" + page + "</white>/<white>" + total + "</white>");
    }
}
