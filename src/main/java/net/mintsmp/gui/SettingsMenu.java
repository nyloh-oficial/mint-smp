package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.feature.Settings;
import net.mintsmp.util.Items;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** /settings — simple per-player toggle panel. */
public final class SettingsMenu extends Menu {

    private final MintSMP plugin;

    // slot -> {key, default, name, description}
    private record Toggle(String key, boolean def, String name, String desc) {}
    private static final java.util.Map<Integer, Toggle> SLOTS = new java.util.LinkedHashMap<>();
    static {
        SLOTS.put(11, new Toggle(Settings.NIGHT_VISION, false, "Night Vision", "See in the dark"));
        SLOTS.put(13, new Toggle(Settings.MOBS, true, "Hostile Mobs", "Let mobs target you"));
        SLOTS.put(14, new Toggle(Settings.TPA, true, "Teleport Requests", "Allow /tpa to you"));
        SLOTS.put(15, new Toggle(Settings.TEAM_CHAT, false, "Team Chat", "Send your chat to your team only"));
    }

    public SettingsMenu(MintSMP plugin, Player p) {
        super(p, 3, "<gradient:#3DDC84:#A8FF60><bold>Settings</bold></gradient>");
        this.plugin = plugin;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        Settings s = plugin.settings();
        for (var e : SLOTS.entrySet()) {
            Toggle t = e.getValue();
            boolean on = s.get(player, t.key(), t.def());
            ItemStack icon = Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    (on ? "<green>" : "<red>") + t.name(),
                    "<gray>" + t.desc(),
                    "",
                    on ? "<green>\u25cf ENABLED" : "<red>\u25cf DISABLED",
                    "<yellow>Click to toggle");
            set(e.getKey(), icon);
        }
        set(22, closeButton());
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == 22) { player.closeInventory(); return; }
        Toggle t = SLOTS.get(slot);
        if (t == null) return;
        boolean now = plugin.settings().toggle(player, t.key(), t.def());
        player.playSound(player.getLocation(), now ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.UI_BUTTON_CLICK, 1f, now ? 1.5f : 0.8f);
        build();
    }
}
