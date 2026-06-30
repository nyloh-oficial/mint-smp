package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Misc QoL + admin: /back, /daily, utility GUIs, heal/feed/fly/repair/god/hat/nick. */
public final class Extras {

    private final MintSMP plugin;
    private final Economy economy;
    private final Map<UUID, Location> back = new HashMap<>();
    private final Set<UUID> god = new HashSet<>();

    public Extras(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    // ---- /back ------------------------------------------------------------

    public void setBack(Player p, Location l) { if (l != null) back.put(p.getUniqueId(), l); }

    public void back(Player p) {
        Location l = back.get(p.getUniqueId());
        if (l == null) { Msg.sendRaw(p, "<red>Nowhere to go back to yet."); return; }
        plugin.teleport().warp(p, l, "previous location");
    }

    // ---- /god -------------------------------------------------------------

    public boolean isGod(Player p) { return god.contains(p.getUniqueId()); }

    public void god(Player p) {
        if (god.remove(p.getUniqueId())) Msg.sendRaw(p, "<gray>God mode <red>off<gray>.");
        else { god.add(p.getUniqueId()); Msg.sendRaw(p, "<gray>God mode <green>on<gray>."); }
    }

    public void clear(UUID uuid) { god.remove(uuid); back.remove(uuid); }

    // ---- /daily -----------------------------------------------------------

    public void daily(Player p) {
        long remaining = plugin.db().cooldownRemaining(p.getUniqueId(), "daily");
        if (remaining > 0) { Msg.sendRaw(p, "<red>Daily reward in <white>" + Kits.human(remaining)); return; }
        double money = plugin.getConfig().getDouble("daily.money", 1000);
        long shards = plugin.getConfig().getLong("daily.shards", 50);
        economy.addMoney(p.getUniqueId(), money);
        economy.addShards(p.getUniqueId(), shards);
        String crate = plugin.getConfig().getString("daily.crate", "");
        if (crate != null && !crate.isBlank() && plugin.crates().exists(crate)) {
            plugin.crates().giveKey(p.getUniqueId(), crate, plugin.getConfig().getInt("daily.crate-keys", 1));
        }
        plugin.db().setCooldown(p.getUniqueId(), "daily", System.currentTimeMillis() + 86400_000L);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Msg.sendRaw(p, "<green>Daily reward: <#A8FF60>" + Amounts.money(money) + " <gray>+ <aqua>" + Amounts.shards(shards)
                + (crate.isBlank() ? "" : " <gray>+ a " + crate + " key"));
    }

    // ---- utility containers ----------------------------------------------

    public void enderchest(Player p) { p.openInventory(p.getEnderChest()); }
    public void workbench(Player p) { p.openWorkbench(null, true); }
    public void anvil(Player p) { p.openAnvil(null, true); }
    public void grindstone(Player p) { p.openGrindstone(null, true); }
    public void cartography(Player p) { p.openCartographyTable(null, true); }
    public void loom(Player p) { p.openLoom(null, true); }
    public void smithing(Player p) { p.openSmithingTable(null, true); }
    public void stonecutter(Player p) { p.openStonecutter(null, true); }

    public void trash(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, Msg.mm("<red>Trash <gray>(closes = deleted)"));
        p.openInventory(inv);
    }

    // ---- self actions -----------------------------------------------------

    public void heal(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20); p.setSaturation(20); p.setFireTicks(0);
        Msg.sendRaw(p, "<green>Healed.");
    }

    public void feed(Player p) { p.setFoodLevel(20); p.setSaturation(20); Msg.sendRaw(p, "<green>Fed."); }

    public void fly(Player p) {
        boolean now = !p.getAllowFlight();
        p.setAllowFlight(now);
        p.setFlying(now && p.isFlying());
        Msg.sendRaw(p, now ? "<gray>Flight <green>enabled<gray>." : "<gray>Flight <red>disabled<gray>.");
    }

    public void repair(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.sendRaw(p, "<red>Hold an item to repair."); return; }
        if (hand.getItemMeta() instanceof Damageable dmg) {
            dmg.setDamage(0);
            hand.setItemMeta(dmg);
            Msg.sendRaw(p, "<green>Item repaired.");
        } else Msg.sendRaw(p, "<red>That item can't be repaired.");
    }

    public void hat(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) { Msg.sendRaw(p, "<red>Hold an item to wear."); return; }
        ItemStack helmet = p.getInventory().getHelmet();
        p.getInventory().setHelmet(hand.clone());
        p.getInventory().setItemInMainHand(helmet);
        Msg.sendRaw(p, "<green>Hat on!");
    }

    public void nick(Player p, String[] a) {
        if (a.length == 0) { Msg.sendRaw(p, "<red>/nick <name|off>"); return; }
        if (a[0].equalsIgnoreCase("off")) {
            p.displayName(net.kyori.adventure.text.Component.text(p.getName()));
            p.playerListName(net.kyori.adventure.text.Component.text(p.getName()));
            Msg.sendRaw(p, "<gray>Nickname cleared.");
            return;
        }
        var name = Msg.mm(a[0]);
        p.displayName(name);
        p.playerListName(name);
        Msg.sendRaw(p, "<green>Nickname updated.");
    }
}
