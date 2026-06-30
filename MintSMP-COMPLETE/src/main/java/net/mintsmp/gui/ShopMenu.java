package net.mintsmp.gui;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** /shop — Shard Shop. Landing of 5 categories; each opens a buy page (shards). */
public final class ShopMenu extends Menu {

    private final MintSMP plugin;
    private final YamlConfiguration shop;
    private String category; // null = landing

    private final List<String> catKeys = new ArrayList<>();
    private final List<Integer> slotToItem = new ArrayList<>();
    private final List<Material> pageMats = new ArrayList<>();
    private final List<Integer> pageCosts = new ArrayList<>();
    private final List<String> pageEnchants = new ArrayList<>();
    private final List<String> pageKeys = new ArrayList<>();

    public ShopMenu(MintSMP plugin, Player p, String category) {
        super(p, 6, plugin.shopTitle());
        this.plugin = plugin;
        this.category = category;
        this.shop = plugin.shopConfig();
    }

    @Override protected void build() {
        inventory.clear();
        fillBorder();
        catKeys.clear();
        if (category == null) buildLanding(); else buildCategory();
        set(rowsTimes9() - 1, closeButton());
        if (category != null) set(rowsTimes9() - 9, backButton());
    }

    private int rowsTimes9() { return inventory.getSize(); }

    private void buildLanding() {
        ConfigurationSection cats = shop.getConfigurationSection("shop.categories");
        if (cats == null) return;
        int slot = 20;
        for (String key : cats.getKeys(false)) {
            String disp = cats.getString(key + ".display", "<green>" + key);
            Material icon = Material.matchMaterial(cats.getString(key + ".icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            set(slot, Items.of(icon, disp, "<gray>Click to browse."));
            catKeys.add(key);
            slotToItemPut(slot, catKeys.size() - 1);
            slot += 2;
            if (slot % 9 == 8) slot += 2;
        }
    }

    private final java.util.Map<Integer, Integer> landingSlots = new java.util.HashMap<>();
    private void slotToItemPut(int slot, int idx) { landingSlots.put(slot, idx); }

    private void buildCategory() {
        ConfigurationSection items = shop.getConfigurationSection("shop.categories." + category + ".items");
        pageMats.clear(); pageCosts.clear(); pageEnchants.clear(); pageKeys.clear();
        if (items == null) return;
        int slot = 10;
        for (String key : items.getKeys(false)) {
            Material mat = resolveMaterial(key);
            int cost = items.getInt(key + ".buy", 0);
            String ench = items.getString(key + ".enchant", null);
            ItemStack icon = mat == null ? Items.of(Material.PAPER, "<green>" + key) : new ItemStack(mat);
            var meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Items.noItalic(Msg.mm("<green>" + niceName(key))));
                meta.lore(List.of(
                        Items.noItalic(Msg.mm("<gray>Cost: <red>" + Amounts.money(cost))),
                        Items.noItalic(Msg.mm("<gray>Left: <green>buy 1  <gray>| Shift: <green>buy 64"))));
                icon.setItemMeta(meta);
            }
            set(slot, icon);
            pageMats.add(mat); pageCosts.add(cost); pageEnchants.add(ench); pageKeys.add(key);
            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= rowsTimes9() - 9) break;
        }
    }

    @Override public void onClick(int slot, ClickType click, InventoryClickEvent e) {
        if (slot == rowsTimes9() - 1) { player.closeInventory(); return; }
        if (category == null) {
            Integer idx = landingSlots.get(slot);
            if (idx != null && idx < catKeys.size()) { category = catKeys.get(idx); refreshOpen(); }
            return;
        }
        if (slot == rowsTimes9() - 9) { category = null; refreshOpen(); return; }
        int first = 10;
        // map slot back to page index by rebuilding the same layout
        int idx = pageIndexForSlot(slot);
        if (idx < 0 || idx >= pageMats.size()) return;
        Material mat = pageMats.get(idx);
        if (mat == null) return;
        int amount = click.isShiftClick() ? 64 : 1;
        long cost = (long) pageCosts.get(idx) * amount;
        if (!plugin.economy().takeMoney(player.getUniqueId(), cost)) {
            Msg.sendRaw(player, "<red>Not enough money (need " + Amounts.money(cost) + ").");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        ItemStack give = new ItemStack(mat, amount);
        if (pageEnchants.get(idx) != null) Items.applyEnchants(give, pageEnchants.get(idx));
        // Potions: set the actual brew type so it isn't a blank water bottle.
        String key = pageKeys.get(idx);
        if (mat == Material.POTION && key.startsWith("POTION_")) {
            applyPotion(give, key.substring("POTION_".length()));
        }
        for (ItemStack rem : player.getInventory().addItem(give).values())
            player.getWorld().dropItemNaturally(player.getLocation(), rem);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
        Msg.sendRaw(player, "<green>Bought <white>" + amount + "x " + niceName(key) + "</white> for <red>" + Amounts.money(cost));
    }

    private void applyPotion(ItemStack item, String suffix) {
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm)) return;
        org.bukkit.potion.PotionType type = switch (suffix.toUpperCase()) {
            case "SPEED", "SWIFTNESS" -> org.bukkit.potion.PotionType.SWIFTNESS;
            case "STRENGTH" -> org.bukkit.potion.PotionType.STRENGTH;
            case "FIRE_RESISTANCE" -> org.bukkit.potion.PotionType.FIRE_RESISTANCE;
            case "HEALING" -> org.bukkit.potion.PotionType.HEALING;
            case "REGEN", "REGENERATION" -> org.bukkit.potion.PotionType.REGENERATION;
            case "NIGHT_VISION" -> org.bukkit.potion.PotionType.NIGHT_VISION;
            case "INVISIBILITY" -> org.bukkit.potion.PotionType.INVISIBILITY;
            case "LEAPING", "JUMP" -> org.bukkit.potion.PotionType.LEAPING;
            case "WATER_BREATHING" -> org.bukkit.potion.PotionType.WATER_BREATHING;
            default -> {
                try { yield org.bukkit.potion.PotionType.valueOf(suffix.toUpperCase()); }
                catch (Exception ex) { yield org.bukkit.potion.PotionType.WATER; }
            }
        };
        pm.setBasePotionType(type);
        item.setItemMeta(pm);
    }

    private int pageIndexForSlot(int slot) {
        int idx = 0, s = 10;
        while (idx < pageMats.size() && s < rowsTimes9() - 9) {
            if (s == slot) return idx;
            idx++; s++;
            if (s % 9 == 8) s += 2;
        }
        return -1;
    }

    private void refreshOpen() { build(); }

    private Material resolveMaterial(String key) {
        if (key.startsWith("POTION_")) return Material.POTION;
        return Material.matchMaterial(key);
    }

    private String niceName(String key) {
        String s = key.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
