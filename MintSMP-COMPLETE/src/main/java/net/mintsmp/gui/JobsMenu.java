package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.feature.Jobs;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/** /jobs — pick one job. */
public final class JobsMenu extends Menu {

    private final MintSMP plugin;

    public JobsMenu(MintSMP plugin, Player p) {
        super(p, 3, "<gradient:#3DDC84:#A8FF60><bold>Jobs</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        String current = plugin.jobs().job(player.getUniqueId());
        set(10, job(Material.DIAMOND_PICKAXE, "Miner", Jobs.MINER, current,
                "<gray>Earn money mining ores.", "<gray>Diamond $20, Emerald $100, Gold $10,", "<gray>Iron/Coal $5, Copper $4"));
        set(13, job(Material.WHEAT, "Farmer", Jobs.FARMER, current,
                "<gray>Earn money harvesting crops."));
        set(16, job(Material.FISHING_ROD, "Fisher", Jobs.FISHER, current,
                "<gray>Earn money fishing."));
        set(22, Items.of(Material.BARRIER, "<red>Leave Job", "<gray>Quit your current job."));
    }

    private org.bukkit.inventory.ItemStack job(Material icon, String name, String id, String current, String... lore) {
        boolean active = id.equals(current);
        String[] full = new String[lore.length + 2];
        System.arraycopy(lore, 0, full, 0, lore.length);
        full[lore.length] = "";
        full[lore.length + 1] = active ? "<green>\u2714 Current job" : "<yellow>Click to join";
        return Items.of(icon, (active ? "<green>" : "<white>") + name, full);
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        switch (slot) {
            case 10 -> { plugin.jobs().setJob(player, Jobs.MINER); build(); }
            case 13 -> { plugin.jobs().setJob(player, Jobs.FARMER); build(); }
            case 16 -> { plugin.jobs().setJob(player, Jobs.FISHER); build(); }
            case 22 -> { plugin.jobs().setJob(player, Jobs.NONE); build(); }
            default -> {}
        }
    }
}
