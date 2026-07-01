package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

/** /team (no args) — quick team actions. Create/invite still use chat commands. */
public final class TeamMenu extends Menu {

    private final MintSMP plugin;

    public TeamMenu(MintSMP plugin, Player p) {
        super(p, 3, "<gradient:#3DDC84:#A8FF60><bold>Team</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        set(10, Items.of(Material.ENDER_PEARL, "<green>Team Home", "<gray>Teleport to your team home."));
        set(11, Items.of(Material.RESPAWN_ANCHOR, "<green>Set Team Home", "<gray>Set the team home here."));
        set(13, Items.of(Material.PAPER, "<white>Team Info", "<gray>View your team + members."));
        set(15, Items.of(Material.OAK_DOOR, "<yellow>Leave Team", "<gray>Leave your current team."));
        set(16, Items.of(Material.TNT, "<red>Disband Team", "<gray>Owner only: delete the team."));
        set(22, closeButton());
        // Note: create / invite / kick use /team create|invite|kick <name> (need typing).
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        switch (slot) {
            case 10 -> { player.closeInventory(); plugin.teams().home(player); }
            case 11 -> { plugin.teams().setHome(player); }
            case 13 -> { player.closeInventory(); plugin.teams().info(player); }
            case 15 -> { player.closeInventory(); plugin.teams().leave(player); }
            case 16 -> { player.closeInventory(); plugin.teams().disband(player); }
            case 22 -> player.closeInventory();
            default -> {}
        }
    }
}
