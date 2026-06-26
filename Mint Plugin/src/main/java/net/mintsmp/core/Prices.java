package net.mintsmp.core;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/** Loads prices.yml and executes /sell, /worth. Payout = price x perItemMult x globalBooster. */
public final class Prices {

    private final MintSMP plugin;
    private final Economy economy;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private double booster = 1.0;

    public Prices(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    public void reload() {
        prices.clear();
        File f = new File(plugin.getDataFolder(), "prices.yml");
        if (!f.exists()) plugin.saveResource("prices.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = yml.getConfigurationSection("prices");
        if (sec == null) return;
        int bad = 0;
        for (String key : sec.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key);
                prices.put(mat, sec.getDouble(key));
            } catch (IllegalArgumentException ex) {
                bad++;
                plugin.getLogger().warning("prices.yml: unknown material '" + key + "' (skipped)");
            }
        }
        plugin.getLogger().info("Loaded " + prices.size() + " sell prices (" + bad + " skipped).");
        booster = plugin.getConfig().getDouble("sell.global-booster", 1.0);
    }

    public Double price(Material mat) { return prices.get(mat); }
    public boolean priced(Material mat) { return prices.containsKey(mat); }
    public double booster() { return booster; }
    public void setBooster(double b) { booster = Math.max(0, b); }

    private boolean sellable(ItemStack it) {
        if (it == null) return false;
        if (!priced(it.getType())) return false;
        if (Items.hasInt(it, Keys.NO_SELL)) return false;
        if (Items.getString(it, Keys.SPAWNER) != null) return false; // spawners: Discord market only
        if (Items.getString(it, Keys.AMETHYST) != null) return false;
        if (Items.getString(it, Keys.CRATE_KEY) != null) return false;
        return true;
    }

    /** Sell a single stack; returns money paid (0 if not sellable). */
    public double sellStack(Player p, ItemStack it) {
        if (!sellable(it)) return 0;
        Material mat = it.getType();
        int qty = it.getAmount();
        double per = prices.get(mat) * economy.multiplier(p.getUniqueId(), mat) * booster;
        double total = round2(per * qty);
        economy.addMoney(p.getUniqueId(), total);
        economy.recordVolume(p.getUniqueId(), mat, qty);
        it.setAmount(0);
        return total;
    }

    public void sellHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { fail(p, "<red>You're not holding anything."); return; }
        if (!sellable(hand)) { fail(p, "<red>That item can't be sold here."); return; }
        int qty = hand.getAmount();
        Material mat = hand.getType();
        double paid = sellStack(p, hand);
        ok(p, "<green>Sold <white>" + qty + "x " + pretty(mat) + "</white> for <#A8FF60>" + Amounts.money(paid));
    }

    public void sellAll(Player p) {
        double total = 0; int items = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (sellable(it)) { items += it.getAmount(); total += sellStack(p, it); }
        }
        if (items == 0) { fail(p, "<red>Nothing sellable in your inventory."); return; }
        ok(p, "<green>Sold <white>" + items + "</white> items for <#A8FF60>" + Amounts.money(round2(total)));
    }

    public void sellMaterial(Player p, Material mat, int max) {
        if (!priced(mat)) { fail(p, "<red>That item has no sell price."); return; }
        int remaining = max <= 0 ? Integer.MAX_VALUE : max;
        double total = 0; int sold = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType() != mat || !sellable(it)) continue;
            int take = Math.min(remaining, it.getAmount());
            if (take <= 0) break;
            double per = prices.get(mat) * economy.multiplier(p.getUniqueId(), mat) * booster;
            double paid = round2(per * take);
            economy.addMoney(p.getUniqueId(), paid);
            economy.recordVolume(p.getUniqueId(), mat, take);
            it.setAmount(it.getAmount() - take);
            total += paid; sold += take; remaining -= take;
            if (remaining <= 0) break;
        }
        if (sold == 0) { fail(p, "<red>You have none of that to sell."); return; }
        ok(p, "<green>Sold <white>" + sold + "x " + pretty(mat) + "</white> for <#A8FF60>" + Amounts.money(round2(total)));
    }

    public void worth(Player p, Material mat) {
        if (!priced(mat)) { fail(p, "<red>No sell price for " + pretty(mat) + "."); return; }
        double per = prices.get(mat) * economy.multiplier(p.getUniqueId(), mat) * booster;
        Msg.sendRaw(p, "<green>" + pretty(mat) + "<gray>: <white>" + Amounts.money(round2(per))
                + " <gray>each | <white>" + Amounts.money(round2(per * 64)) + " <gray>per stack "
                + "<dark_gray>(x" + String.format("%.2f", economy.multiplier(p.getUniqueId(), mat)) + " mult)");
    }

    public static String pretty(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private void ok(Player p, String mini)   { Msg.sendRaw(p, mini); p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f); }
    private void fail(Player p, String mini) { Msg.sendRaw(p, mini); p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f); }
}
