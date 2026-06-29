package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /leaderboard — GUI with category buttons and a top-10 list. */
public final class LeaderboardMenu extends Menu {

    private final MintSMP plugin;
    private String column = "money";

    private record Cat(String column, String label, Material icon) {}
    private static final java.util.Map<Integer, Cat> CATS = new java.util.LinkedHashMap<>();
    static {
        CATS.put(2, new Cat("money", "Money", Material.GOLD_INGOT));
        CATS.put(3, new Cat("shards", "Shards", Material.AMETHYST_SHARD));
        CATS.put(4, new Cat("kills", "Kills", Material.DIAMOND_SWORD));
        CATS.put(5, new Cat("blocks_mined", "Blocks Mined", Material.DIAMOND_PICKAXE));
        CATS.put(6, new Cat("playtime", "Playtime", Material.CLOCK));
    }

    public LeaderboardMenu(MintSMP plugin, Player p) {
        super(p, 6, "<gradient:#3DDC84:#A8FF60><bold>Leaderboards</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        for (var e : CATS.entrySet()) {
            Cat c = e.getValue();
            boolean sel = c.column().equals(column);
            set(e.getKey(), Items.of(c.icon(),
                    (sel ? "<green>\u25b6 " : "<gray>") + c.label(),
                    sel ? "<green>Showing this board" : "<yellow>Click to view"));
        }
        List<Database.Top> top = plugin.economy().top(column, 10);
        int slot = 19;
        int rank = 1;
        for (Database.Top t : top) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rank <white>#" + rank);
            lore.add("<gray>" + label() + ": <gradient:#3DDC84:#A8FF60>" + format(t.value()));
            Material medal = rank == 1 ? Material.GOLD_BLOCK : rank == 2 ? Material.IRON_BLOCK : rank == 3 ? Material.COPPER_BLOCK : Material.PAPER;
            set(slot, Items.named(medal, "<yellow>#" + rank + " <white>" + (t.name() == null ? "?" : t.name()), lore));
            slot++;
            if (slot % 9 == 8) slot += 2;
            rank++;
            if (slot >= 44) break;
        }
        if (top.isEmpty()) set(22, Items.of(Material.BARRIER, "<red>No data yet"));
        set(49, closeButton());
    }

    private String label() {
        for (Cat c : CATS.values()) if (c.column().equals(column)) return c.label();
        return column;
    }

    private String format(double v) {
        return switch (column) {
            case "money" -> Amounts.money(v);
            case "shards" -> Amounts.shards((long) v);
            case "playtime" -> (long) (v / 3600) + "h " + (long) ((v % 3600) / 60) + "m";
            default -> Amounts.whole(v);
        };
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 49) { player.closeInventory(); return; }
        Cat c = CATS.get(slot);
        if (c != null) { column = c.column(); build(); }
    }
}
