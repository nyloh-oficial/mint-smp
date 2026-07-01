package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.feature.Crates;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /crates — list crates; opening one shows 7 rewards to pick from (no RNG). */
public final class CrateMenu extends Menu {

    private final MintSMP plugin;
    private final Crates crates;
    private String crateId; // null = list

    private final List<String> listSlots = new ArrayList<>(); // index = slot offset
    private final java.util.Map<Integer, String> crateAtSlot = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> rewardAtSlot = new java.util.HashMap<>();

    public CrateMenu(MintSMP plugin, Player p, String crateId) {
        super(p, crateId == null ? 3 : 6, "<gradient:#3DDC84:#1B5E20><bold>Crates</bold></gradient>");
        this.plugin = plugin; this.crates = plugin.crates(); this.crateId = crateId;
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        crateAtSlot.clear(); rewardAtSlot.clear();
        if (crateId == null) buildList(); else buildPick();
        set(inventory.getSize() - 1, closeButton());
        if (crateId != null) set(inventory.getSize() - 9, backButton());
    }

    private void buildList() {
        int slot = 10;
        for (String id : crates.crateIds()) {
            int keys = crates.keys(player, id);
            ItemStack icon = Items.of(Material.CHEST, crates.display(id),
                    "<gray>Your keys: <white>" + keys,
                    "<gray>Click to open (pick 1 of " + crates.pickCount() + ").");
            set(slot, icon);
            crateAtSlot.put(slot, id);
            slot += 1;
            if (slot % 9 == 8) slot += 2;
        }
    }

    private void buildPick() {
        List<Crates.Map> rewards = crates.rewards(crateId);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21}; // up to ~10 slots; show pickCount
        int show = Math.min(rewards.size(), crates.pickCount());
        for (int i = 0; i < show; i++) {
            ItemStack preview = crates.preview(rewards.get(i));
            ItemMeta m = preview.getItemMeta();
            if (m != null) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Items.noItalic(Msg.mm("<gray>Click to <green>choose this reward</green>.")));
                lore.add(Items.noItalic(Msg.mm("<dark_gray>Consumes 1 key.")));
                m.lore(lore);
                preview.setItemMeta(m);
            }
            int slot = slots[Math.min(i, slots.length - 1)];
            set(slot, preview);
            rewardAtSlot.put(slot, i);
        }
        set(4, Items.of(Material.TRIPWIRE_HOOK, crates.display(crateId),
                "<gray>Keys: <white>" + crates.keys(player, crateId)));
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == inventory.getSize() - 1) { player.closeInventory(); return; }
        if (crateId == null) {
            String id = crateAtSlot.get(slot);
            if (id == null) return;
            if (crates.keys(player, id) <= 0) {
                Msg.sendRaw(player, "<red>You have no <white>" + id + "</white> keys.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            crateId = id; build();
            return;
        }
        if (slot == inventory.getSize() - 9) { crateId = null; refreshToList(); return; }
        Integer idx = rewardAtSlot.get(slot);
        if (idx == null) return;
        if (crates.redeem(player, crateId, idx)) {
            player.closeInventory();
        }
    }

    private void refreshToList() {
        // reopen as list (size differs) -> open a fresh list menu
        new CrateMenu(plugin, player, null).open();
    }
}
