package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Shows a stacked spawner: total count at top + the drops it produces. */
public final class SpawnerStackMenu extends Menu {

    private final MintSMP plugin;
    private final String type;
    private final int count;

    public SpawnerStackMenu(MintSMP plugin, Player p, String type, int count) {
        super(p, 3, "<gradient:#3DDC84:#A8FF60><bold>Spawner Stack \u00d7" + count + "</bold></gradient>");
        this.plugin = plugin;
        this.type = type;
        this.count = count;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        set(4, Items.of(Material.SPAWNER,
                "<gradient:#3DDC84:#A8FF60><bold>" + nice(type) + " Spawner</bold></gradient>",
                "<gray>Stacked: <yellow>\u00d7" + count,
                "<gray>Right-click to harvest <white>(yield \u00d7" + count + ")",
                "<gray>Hold a matching spawner + right-click to add.",
                "<gray>Break to retrieve <white>" + count + "</white> spawners."));
        List<ItemStack> preview = plugin.spawners().dropPreview(type);
        int slot = 11;
        for (ItemStack it : preview) {
            if (slot > 15) break;
            set(slot++, it);
        }
        set(22, closeButton());
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 22) player.closeInventory();
    }

    private String nice(String s) {
        s = s.toLowerCase().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
