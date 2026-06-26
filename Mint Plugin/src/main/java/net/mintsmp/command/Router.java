package net.mintsmp.command;

import net.mintsmp.MintSMP;
import net.mintsmp.gui.AHMenu;
import net.mintsmp.gui.CrateMenu;
import net.mintsmp.gui.OrdersMenu;
import net.mintsmp.gui.PWarpMenu;
import net.mintsmp.gui.QuickBuyMenu;
import net.mintsmp.gui.ShopMenu;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** One executor for every Mint SMP command. Routes on command name. */
public final class Router implements CommandExecutor, TabCompleter {

    private final MintSMP plugin;
    public Router(MintSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        String name = cmd.getName().toLowerCase();
        switch (name) {
            case "mintsmp" -> core(s, a);
            case "balance" -> balance(s, a);
            case "pay" -> pay(s, a);
            case "baltop" -> top(s, "money", "Balance", true);
            case "shards" -> shards(s, a);
            case "eco" -> eco(s, a);
            case "sell" -> sell(s, a);
            case "worth" -> worth(s, a);
            case "sellmulti" -> sellmulti(s);
            case "sellboost" -> sellboost(s, a);
            case "shop" -> { if (req(s)) new ShopMenu(plugin, (Player) s, null).open(); }
            case "ah" -> ah(s, a);
            case "crates" -> { if (req(s)) new CrateMenu(plugin, (Player) s, null).open(); }
            case "key" -> key(s, a);
            case "keyall" -> keyall(s, a);
            case "spawner" -> spawner(s, a);
            case "amethyst" -> amethyst(s, a);
            case "sethome" -> { if (req(s)) plugin.teleport().setHome((Player) s, a.length > 0 ? a[0] : "home"); }
            case "home" -> { if (req(s)) plugin.teleport().home((Player) s, a.length > 0 ? a[0] : "home"); }
            case "delhome" -> { if (req(s) && need(s, a, 1, "/delhome <name>")) plugin.teleport().delHome((Player) s, a[0]); }
            case "homes" -> { if (req(s)) plugin.teleport().homesList((Player) s); }
            case "tpa" -> tpa(s, a, false);
            case "tpahere" -> tpa(s, a, true);
            case "tpaccept" -> { if (req(s)) plugin.teleport().accept((Player) s); }
            case "tpdeny" -> { if (req(s)) plugin.teleport().deny((Player) s); }
            case "rtp" -> { if (req(s)) plugin.teleport().rtp((Player) s, a.length > 0 ? a[0] : "overworld"); }
            case "spawn" -> { if (req(s)) plugin.teleport().spawn((Player) s); }
            case "setspawn" -> { if (perm(s, "mint.admin")) plugin.teleport().setSpawn((Player) s); }
            case "warp" -> warp(s, a);
            case "setwarp" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/setwarp <name>")) plugin.teleport().setWarp((Player) s, a[0]); }
            case "delwarp" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/delwarp <name>")) plugin.teleport().delWarp((Player) s, a[0]); }
            case "bounty" -> bounty(s, a);
            case "afk" -> { if (req(s)) plugin.afk().toggleAfk((Player) s); }
            case "stats" -> stats(s, a);
            case "killtop" -> top(s, "kills", "Kills", false);
            case "playtimetop" -> top(s, "playtime", "Playtime", false);
            case "blockstop" -> top(s, "blocks_mined", "Blocks", false);
            case "shardstop" -> top(s, "shards", "Shards", false);
            case "leaderboard" -> top(s, "money", "Balance", true);
            case "scoreboard" -> { if (req(s)) { boolean hidden = plugin.display().toggleSidebar((Player) s); Msg.sendRaw(s, hidden ? "<gray>Sidebar hidden." : "<gray>Sidebar shown."); } }
            case "msg", "tell" -> msg(s, a);
            case "r" -> reply(s, a);
            case "ignore" -> { if (req(s) && need(s, a, 1, "/ignore <player>")) plugin.afk().ignore((Player) s, a[0], true); }
            case "unignore" -> { if (req(s) && need(s, a, 1, "/unignore <player>")) plugin.afk().ignore((Player) s, a[0], false); }
            case "ignorelist" -> { if (req(s)) plugin.afk().ignoreList((Player) s); }
            case "msgtoggle" -> { if (req(s)) plugin.afk().toggleMsg((Player) s); }
            case "near" -> near(s, a);
            case "list" -> { if (req(s)) plugin.afk().list((Player) s); }
            case "ping" -> ping(s, a);
            case "seen" -> { if (req(s) && need(s, a, 1, "/seen <player>")) plugin.afk().seen((Player) s, a[0]); }
            case "findplayer" -> { if (req(s) && need(s, a, 1, "/findplayer <player>")) plugin.afk().findPlayer((Player) s, a[0]); }
            case "rules" -> rules(s);
            case "report" -> report(s, a);
            case "discord" -> { if (req(s)) plugin.afk().link((Player) s, "discord"); }
            case "store" -> { if (req(s)) plugin.afk().link((Player) s, "store"); }
            case "website" -> { if (req(s)) plugin.afk().link((Player) s, "website"); }
            case "settings" -> settings(s, a);
            case "team" -> team(s, a);
            case "duel" -> duel(s, a);
            case "orders" -> orders(s, a);
            case "kit" -> { if (req(s)) { if (a.length == 0) plugin.kits().list((Player) s); else plugin.kits().give((Player) s, a[0].toLowerCase()); } }
            case "kits" -> { if (req(s)) plugin.kits().list((Player) s); }
            case "pwarp" -> pwarp(s, a);
            case "back" -> { if (req(s)) plugin.extras().back((Player) s); }
            case "daily" -> { if (req(s)) plugin.extras().daily((Player) s); }
            case "ec" -> { if (req(s)) plugin.extras().enderchest((Player) s); }
            case "craft" -> { if (req(s)) plugin.extras().workbench((Player) s); }
            case "anvil" -> { if (req(s)) plugin.extras().anvil((Player) s); }
            case "grindstone" -> { if (req(s)) plugin.extras().grindstone((Player) s); }
            case "cartography" -> { if (req(s)) plugin.extras().cartography((Player) s); }
            case "loom" -> { if (req(s)) plugin.extras().loom((Player) s); }
            case "smithing" -> { if (req(s)) plugin.extras().smithing((Player) s); }
            case "stonecutter" -> { if (req(s)) plugin.extras().stonecutter((Player) s); }
            case "trash" -> { if (req(s)) plugin.extras().trash((Player) s); }
            case "hat" -> { if (req(s)) plugin.extras().hat((Player) s); }
            case "heal" -> { if (req(s) && perm(s, "mint.admin")) plugin.extras().heal((Player) s); }
            case "feed" -> { if (req(s) && perm(s, "mint.admin")) plugin.extras().feed((Player) s); }
            case "fly" -> { if (req(s) && perm(s, "mint.admin")) plugin.extras().fly((Player) s); }
            case "repair" -> { if (req(s) && perm(s, "mint.admin")) plugin.extras().repair((Player) s); }
            case "god" -> { if (req(s) && perm(s, "mint.admin")) plugin.extras().god((Player) s); }
            case "nick" -> { if (req(s) && perm(s, "mint.nick")) plugin.extras().nick((Player) s, a); }
            case "quickbuy" -> { if (req(s)) new QuickBuyMenu(plugin, (Player) s, 0).open(); }
            case "trap" -> trap(s, a);
            case "ban" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/ban <player> [reason]")) plugin.staff().ban(s, a[0], tail(a, 1)); }
            case "tempban" -> { if (perm(s, "mint.admin") && need(s, a, 2, "/tempban <player> <duration> [reason]")) plugin.staff().tempban(s, a[0], a[1], tail(a, 2)); }
            case "unban" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/unban <player>")) plugin.staff().unban(s, a[0]); }
            case "kick" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/kick <player> [reason]")) plugin.staff().kick(s, a[0], tail(a, 1)); }
            case "mute" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/mute <player> [duration|perm] [reason]")) plugin.staff().mute(s, a[0], a.length > 1 ? a[1] : null, tail(a, 2)); }
            case "unmute" -> { if (perm(s, "mint.admin") && need(s, a, 1, "/unmute <player>")) plugin.staff().unmute(s, a[0]); }
            default -> Msg.sendRaw(s, "<red>Unknown command.");
        }
        return true;
    }

    // ---- core -------------------------------------------------------------

    private void core(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();
        switch (sub) {
            case "reload" -> { if (perm(s, "mint.admin")) { plugin.reloadAll(); Msg.sendRaw(s, "<#A8FF60>Mint SMP reloaded."); } }
            case "version" -> Msg.sendRaw(s, "<#3DDC84>Mint SMP <white>" + plugin.getPluginMeta().getVersion());
            default -> Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>Mint SMP</bold></gradient> <gray>economy + PvP. <white>/shop /sell /ah /crates /rtp /home /stats");
        }
    }

    // ---- economy ----------------------------------------------------------

    private void balance(CommandSender s, String[] a) {
        UUID who; String label;
        if (a.length > 0) { who = plugin.db().findByName(a[0]); label = a[0]; if (who == null) { Msg.sendRaw(s, "<red>Unknown player."); return; } }
        else { if (!req(s)) return; who = ((Player) s).getUniqueId(); label = ((Player) s).getName(); }
        Msg.sendRaw(s, "<green>" + label + "<gray>: <#A8FF60>" + Amounts.money(plugin.economy().money(who))
                + " <gray>| <aqua>" + Amounts.shards(plugin.economy().shards(who)));
    }

    private void pay(CommandSender s, String[] a) {
        if (!req(s) || !need(s, a, 2, "/pay <player> <amount>")) return;
        Player from = (Player) s;
        Player to = Bukkit.getPlayerExact(a[0]);
        if (to == null || to.equals(from)) { Msg.sendRaw(s, "<red>Pick a valid online player."); return; }
        double amt = Amounts.parse(a[1]);
        if (amt <= 0) { Msg.sendRaw(s, "<red>Invalid amount."); return; }
        amt = Math.round(amt * 100.0) / 100.0;
        if (!plugin.economy().takeMoney(from.getUniqueId(), amt)) { Msg.sendRaw(s, "<red>Insufficient funds."); return; }
        double tax = plugin.getConfig().getDouble("economy.pay-tax-percent", 0) / 100.0;
        double received = amt * (1 - tax);
        plugin.economy().addMoney(to.getUniqueId(), received);
        Msg.sendRaw(from, "<green>Sent <#A8FF60>" + Amounts.money(amt) + "</#A8FF60> to <white>" + to.getName());
        Msg.sendRaw(to, "<green>Received <#A8FF60>" + Amounts.money(received) + "</#A8FF60> from <white>" + from.getName());
    }

    private void shards(CommandSender s, String[] a) {
        UUID who; String label;
        if (a.length > 0) { who = plugin.db().findByName(a[0]); label = a[0]; if (who == null) { Msg.sendRaw(s, "<red>Unknown player."); return; } }
        else { if (!req(s)) return; who = ((Player) s).getUniqueId(); label = ((Player) s).getName(); }
        Msg.sendRaw(s, "<green>" + label + "<gray>'s shards: <aqua>" + Amounts.shards(plugin.economy().shards(who)));
    }

    private void eco(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 4) { Msg.sendRaw(s, "<red>/eco give|take|set <player> <money|shards> <amount>"); return; }
        UUID who = plugin.db().findByName(a[1]);
        Player online = Bukkit.getPlayerExact(a[1]);
        if (who == null && online == null) { Msg.sendRaw(s, "<red>Unknown player."); return; }
        if (online != null) who = online.getUniqueId();
        boolean money = a[2].equalsIgnoreCase("money");
        double amt = Amounts.parse(a[3]);
        if (amt < 0) { Msg.sendRaw(s, "<red>Invalid amount."); return; }
        String op = a[0].toLowerCase();
        if (online == null && money) { // offline money only
            if (op.equals("give")) plugin.db().addOfflineMoney(who, amt);
            else if (op.equals("take")) plugin.db().addOfflineMoney(who, -amt);
            Msg.sendRaw(s, "<green>Updated offline player's money."); return;
        }
        switch (op) {
            case "give" -> { if (money) plugin.economy().addMoney(who, amt); else plugin.economy().addShards(who, (long) amt); }
            case "take" -> { if (money) plugin.economy().addMoney(who, -amt); else plugin.economy().addShards(who, -(long) amt); }
            case "set" -> { if (money) plugin.economy().setMoney(who, amt); else { plugin.economy().addShards(who, -plugin.economy().shards(who)); plugin.economy().addShards(who, (long) amt); } }
            default -> { Msg.sendRaw(s, "<red>give|take|set"); return; }
        }
        Msg.sendRaw(s, "<green>Done.");
    }

    // ---- sell -------------------------------------------------------------

    private void sell(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0 || a[0].equalsIgnoreCase("hand")) { plugin.prices().sellHand(p); return; }
        if (a[0].equalsIgnoreCase("all")) { plugin.prices().sellAll(p); return; }
        Material mat = Material.matchMaterial(a[0]);
        if (mat == null) { Msg.sendRaw(s, "<red>Unknown material."); return; }
        int amt = a.length > 1 ? (int) Amounts.parseLong(a[1]) : 0;
        plugin.prices().sellMaterial(p, mat, amt);
    }

    private void worth(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        Material mat;
        if (a.length == 0 || a[0].equalsIgnoreCase("hand")) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) { Msg.sendRaw(s, "<red>Hold an item or name one."); return; }
            mat = hand.getType();
        } else {
            mat = Material.matchMaterial(a[0]);
            if (mat == null) { Msg.sendRaw(s, "<red>Unknown material."); return; }
        }
        plugin.prices().worth(p, mat);
    }

    private void sellmulti(CommandSender s) {
        if (!req(s)) return;
        Player p = (Player) s;
        Map<String, Long> vols = plugin.db().topVolumes(p.getUniqueId(), 10);
        Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>Your Sell Multipliers</bold></gradient>");
        if (vols.isEmpty()) { Msg.sendRaw(s, "<gray>Sell items to raise per-item multipliers (1.0x -> 3.0x)."); return; }
        for (var en : vols.entrySet()) {
            Material mat = Material.matchMaterial(en.getKey());
            if (mat == null) continue;
            double m = plugin.economy().multiplier(p.getUniqueId(), mat);
            Msg.sendRaw(s, "<gray>" + en.getKey() + ": <#A8FF60>x" + String.format("%.2f", m)
                    + " <dark_gray>(" + en.getValue() + " sold)");
        }
    }

    private void sellboost(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 2) { Msg.sendRaw(s, "<red>/sellboost <multiplier> <minutes>"); return; }
        double mult; int mins;
        try { mult = Double.parseDouble(a[0]); mins = Integer.parseInt(a[1]); }
        catch (NumberFormatException ex) { Msg.sendRaw(s, "<red>Numbers only."); return; }
        plugin.prices().setBooster(mult);
        Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <#A8FF60>Sell booster x" + mult + " active for " + mins + " min!"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.prices().setBooster(plugin.getConfig().getDouble("sell.global-booster", 1.0));
            Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <gray>Sell booster ended."));
        }, mins * 60L * 20L);
    }

    // ---- auction house ----------------------------------------------------

    private void ah(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { new AHMenu(plugin, p, null, 0).open(); return; }
        switch (a[0].toLowerCase()) {
            case "sell" -> {
                if (a.length < 2) { Msg.sendRaw(s, "<red>/ah sell <price>"); return; }
                double price = Amounts.parse(a[1]);
                double min = plugin.getConfig().getDouble("auction-house.min-price", 1);
                double max = plugin.getConfig().getDouble("auction-house.max-price", 1e9);
                if (price < min || price > max) { Msg.sendRaw(s, "<red>Price must be " + Amounts.money(min) + " - " + Amounts.money(max)); return; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) { Msg.sendRaw(s, "<red>Hold an item to sell."); return; }
                if (Items.hasInt(hand, net.mintsmp.util.Keys.NO_SELL)) { Msg.sendRaw(s, "<red>That item can't be auctioned."); return; }
                int maxListings = plugin.getConfig().getInt("auction-house.max-listings-per-player", 7);
                if (plugin.db().ahCount(p.getUniqueId()) >= maxListings) { Msg.sendRaw(s, "<red>Listing limit reached (" + maxListings + ")."); return; }
                long id = plugin.db().ahCreate(p.getUniqueId(), p.getName(), hand.clone(), price);
                if (id < 0) { Msg.sendRaw(s, "<red>Could not list item."); return; }
                hand.setAmount(0);
                Msg.sendRaw(s, "<green>Listed for <#A8FF60>" + Amounts.money(price) + "</#A8FF60>.");
            }
            case "search" -> new AHMenu(plugin, p, a.length > 1 ? a[1] : null, 0).open();
            case "collect" -> collect(p);
            default -> new AHMenu(plugin, p, null, 0).open();
        }
    }

    private void collect(Player p) {
        var items = plugin.db().collection(p.getUniqueId());
        if (items.isEmpty()) { Msg.sendRaw(p, "<gray>Your collection box is empty."); return; }
        int given = 0;
        for (Object[] row : items) {
            long id = (long) row[0];
            ItemStack it = (ItemStack) row[1];
            var overflow = p.getInventory().addItem(it);
            if (overflow.isEmpty()) { plugin.db().collectionRemove(id); given++; }
            else break; // inventory full
        }
        Msg.sendRaw(p, "<green>Collected <white>" + given + "</white> item(s).");
    }

    // ---- crates / keys ----------------------------------------------------

    private void key(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 4 || !a[0].equalsIgnoreCase("give")) { Msg.sendRaw(s, "<red>/key give <player> <crate> <amount>"); return; }
        UUID who = plugin.db().findByName(a[1]);
        Player online = Bukkit.getPlayerExact(a[1]);
        if (online != null) who = online.getUniqueId();
        if (who == null) { Msg.sendRaw(s, "<red>Unknown player."); return; }
        if (!plugin.crates().exists(a[2])) { Msg.sendRaw(s, "<red>Unknown crate."); return; }
        int amt = (int) Amounts.parseLong(a[3]);
        plugin.crates().giveKey(who, a[2], Math.max(1, amt));
        Msg.sendRaw(s, "<green>Gave keys.");
    }

    private void keyall(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 2) { Msg.sendRaw(s, "<red>/keyall <crate> <amount>"); return; }
        if (!plugin.crates().exists(a[0])) { Msg.sendRaw(s, "<red>Unknown crate."); return; }
        plugin.crates().keyAll(a[0], Math.max(1, (int) Amounts.parseLong(a[1])));
    }

    // ---- spawner / amethyst (admin give) ----------------------------------

    private void spawner(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 3 || !a[0].equalsIgnoreCase("give")) { Msg.sendRaw(s, "<red>/spawner give <player> <type> [amt]"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.sendRaw(s, "<red>Player must be online."); return; }
        int amt = a.length > 3 ? (int) Amounts.parseLong(a[3]) : 1;
        if (!plugin.spawners().give(t, a[2].toUpperCase(), Math.max(1, amt))) { Msg.sendRaw(s, "<red>Unknown spawner type."); return; }
        Msg.sendRaw(s, "<green>Gave spawner(s).");
    }

    private void amethyst(CommandSender s, String[] a) {
        if (!perm(s, "mint.admin")) return;
        if (a.length < 3 || !a[0].equalsIgnoreCase("give")) { Msg.sendRaw(s, "<red>/amethyst give <player> <type>"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { Msg.sendRaw(s, "<red>Player must be online."); return; }
        if (!plugin.amethyst().isType(a[2].toUpperCase())) { Msg.sendRaw(s, "<red>Types: PICKAXE AXE SHOVEL BUCKET SHARD_BOOSTER"); return; }
        t.getInventory().addItem(plugin.amethyst().create(a[2].toUpperCase()));
        Msg.sendRaw(s, "<green>Gave Amethyst item.");
    }

    // ---- teleport helpers -------------------------------------------------

    private void tpa(CommandSender s, String[] a, boolean here) {
        if (!req(s) || !need(s, a, 1, here ? "/tpahere <player>" : "/tpa <player>")) return;
        Player to = Bukkit.getPlayerExact(a[0]);
        if (to == null || to.equals(s)) { Msg.sendRaw(s, "<red>Pick a valid online player."); return; }
        if (here) plugin.teleport().tpaHere((Player) s, to); else plugin.teleport().tpa((Player) s, to);
    }

    private void warp(CommandSender s, String[] a) {
        if (!req(s)) return;
        if (a.length == 0) { Msg.sendRaw(s, "<green>Warps: <white>" + String.join(", ", plugin.teleport().warpNames())); return; }
        plugin.teleport().warpTo((Player) s, a[0]);
    }

    private void bounty(CommandSender s, String[] a) {
        if (!req(s) || !need(s, a, 2, "/bounty <player> <amount>")) return;
        UUID target = plugin.db().findByName(a[0]);
        Player online = Bukkit.getPlayerExact(a[0]);
        if (online != null) target = online.getUniqueId();
        if (target == null) { Msg.sendRaw(s, "<red>Unknown player."); return; }
        double amt = Amounts.parse(a[1]);
        double min = plugin.getConfig().getDouble("combat.bounty.min-amount", 100);
        if (amt < min) { Msg.sendRaw(s, "<red>Minimum bounty is " + Amounts.money(min)); return; }
        plugin.combat().addBounty((Player) s, target, amt);
    }

    // ---- info -------------------------------------------------------------

    private void stats(CommandSender s, String[] a) {
        UUID who; String label;
        if (a.length > 0) { who = plugin.db().findByName(a[0]); label = a[0]; if (who == null) { Msg.sendRaw(s, "<red>Unknown player."); return; } }
        else { if (!req(s)) return; who = ((Player) s).getUniqueId(); label = ((Player) s).getName(); }
        Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>" + label + "</bold></gradient>");
        Msg.sendRaw(s, "<gray>Money: <#A8FF60>" + Amounts.money(plugin.economy().money(who)));
        Msg.sendRaw(s, "<gray>Shards: <aqua>" + Amounts.shards(plugin.economy().shards(who)));
        Msg.sendRaw(s, "<gray>Kills: <white>" + plugin.economy().kills(who) + " <gray>Deaths: <white>" + plugin.economy().deaths(who)
                + " <gray>K/D: <white>" + String.format("%.2f", plugin.economy().kd(who)));
        Msg.sendRaw(s, "<gray>Blocks mined: <white>" + plugin.economy().blocks(who));
    }

    private void top(CommandSender s, String column, String label, boolean money) {
        var list = plugin.economy().top(column, 10);
        Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>Top " + label + "</bold></gradient>");
        int i = 1;
        for (var t : list) {
            String val = money ? Amounts.money(t.value()) : Amounts.whole(t.value());
            Msg.sendRaw(s, "<gray>" + (i++) + ". <white>" + t.name() + " <gray>- <#A8FF60>" + val);
        }
    }

    private void near(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        int radius = a.length > 0 ? (int) Amounts.parseLong(a[0]) : 100;
        if (radius <= 0) radius = 100;
        int r2 = radius * radius;
        String names = p.getWorld().getPlayers().stream()
                .filter(o -> o != p && o.getLocation().distanceSquared(p.getLocation()) <= r2)
                .map(Player::getName).collect(Collectors.joining(", "));
        Msg.sendRaw(s, names.isEmpty() ? "<gray>No players within " + radius + " blocks." : "<green>Nearby: <white>" + names);
    }

    private void ping(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player t = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (Player) s;
        if (t == null) { Msg.sendRaw(s, "<red>Player not online."); return; }
        plugin.afk().ping((Player) s, t);
    }

    private void msg(CommandSender s, String[] a) {
        if (!req(s) || a.length < 2) { Msg.sendRaw(s, "<red>/msg <player> <message>"); return; }
        Player to = Bukkit.getPlayerExact(a[0]);
        if (to == null) { Msg.sendRaw(s, "<red>Player not online."); return; }
        plugin.afk().msg((Player) s, to, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
    }

    private void reply(CommandSender s, String[] a) {
        if (!req(s) || a.length < 1) { Msg.sendRaw(s, "<red>/r <message>"); return; }
        plugin.afk().reply((Player) s, String.join(" ", a));
    }

    private void rules(CommandSender s) {
        Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>Mint SMP Rules</bold></gradient>");
        Msg.sendRaw(s, "<gray>1. Raiding & griefing are allowed. Only ender chests are safe.");
        Msg.sendRaw(s, "<gray>2. Discord Market trades are off-server; scamming there is rules-legal.");
        Msg.sendRaw(s, "<gray>3. No cheating, hacks, or exploits.");
        Msg.sendRaw(s, "<gray>4. Be respectful in chat.");
    }

    private void report(CommandSender s, String[] a) {
        if (!req(s) || a.length < 1) { Msg.sendRaw(s, "<red>/report <message>"); return; }
        String text = String.join(" ", a);
        plugin.getLogger().warning("[REPORT] " + ((Player) s).getName() + ": " + text);
        Msg.sendRaw(s, "<green>Report submitted. Thank you.");
    }

    private void settings(CommandSender s, String[] a) {
        if (!req(s)) return;
        Msg.sendRaw(s, "<gradient:#3DDC84:#1B5E20><bold>Settings</bold></gradient>");
        Msg.sendRaw(s, "<gray>/msgtoggle <dark_gray>- toggle private messages");
        Msg.sendRaw(s, "<gray>/scoreboard <dark_gray>- toggle sidebar");
        Msg.sendRaw(s, "<gray>/afk <dark_gray>- toggle AFK");
    }

    // ---- teams / duels / orders -------------------------------------------

    private void team(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { plugin.teams().info(p); return; }
        switch (a[0].toLowerCase()) {
            case "create" -> { if (need(s, a, 2, "/team create <name>")) plugin.teams().create(p, a[1]); }
            case "invite" -> { if (need(s, a, 2, "/team invite <player>")) plugin.teams().invite(p, a[1]); }
            case "accept", "join" -> plugin.teams().accept(p);
            case "leave" -> plugin.teams().leave(p);
            case "kick" -> { if (need(s, a, 2, "/team kick <player>")) plugin.teams().kick(p, a[1]); }
            case "disband" -> plugin.teams().disband(p);
            case "info" -> plugin.teams().info(p);
            case "sethome" -> plugin.teams().setHome(p);
            case "home" -> plugin.teams().home(p);
            case "chat", "c" -> {
                if (a.length < 2) { Msg.sendRaw(s, "<red>/team chat <message>"); return; }
                plugin.teams().chat(p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            }
            default -> Msg.sendRaw(s, "<gray>/team <white>create|invite|accept|leave|kick|disband|info|chat|sethome|home");
        }
    }

    private void duel(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { Msg.sendRaw(s, "<gray>/duel <player> <dark_gray>| <gray>/duel accept [player] <dark_gray>| <gray>/duel deny"); return; }
        switch (a[0].toLowerCase()) {
            case "accept" -> plugin.duels().accept(p, a.length > 1 ? a[1] : null);
            case "deny" -> plugin.duels().deny(p);
            default -> {
                Player target = Bukkit.getPlayerExact(a[0]);
                if (target == null) { Msg.sendRaw(s, "<red>That player isn't online."); return; }
                plugin.duels().request(p, target);
            }
        }
    }

    private void orders(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { new OrdersMenu(plugin, p, 0).open(); return; }
        if (a[0].equalsIgnoreCase("create")) {
            if (a.length < 3) { Msg.sendRaw(s, "<red>/orders create <price-each> <quantity>"); return; }
            double price = Amounts.parse(a[1]);
            int qty = (int) Amounts.parseLong(a[2]);
            if (price <= 0 || qty <= 0) { Msg.sendRaw(s, "<red>Price and quantity must be positive."); return; }
            plugin.orders().create(p, price, qty);
            return;
        }
        new OrdersMenu(plugin, p, 0).open();
    }

    private void pwarp(CommandSender s, String[] a) {
        if (!req(s)) return;
        Player p = (Player) s;
        if (a.length == 0) { new PWarpMenu(plugin, p, 0).open(); return; }
        switch (a[0].toLowerCase()) {
            case "set", "create" -> { if (need(s, a, 2, "/pwarp set <name>")) plugin.playerWarps().create(p, a[1]); }
            case "delete", "del", "remove" -> { if (need(s, a, 2, "/pwarp delete <name>")) plugin.playerWarps().delete(p, a[1]); }
            case "list" -> new PWarpMenu(plugin, p, 0).open();
            default -> plugin.playerWarps().visit(p, a[0]);
        }
    }

    private void trap(CommandSender s, String[] a) {
        if (!req(s) || !perm(s, "mint.admin")) return;
        Player p = (Player) s;
        String sub = a.length == 0 ? "info" : a[0].toLowerCase();
        switch (sub) {
            case "set", "add", "create" -> plugin.staff().setTrap(p);
            case "remove", "delete", "del" -> plugin.staff().removeTrap(p);
            default -> plugin.staff().trapInfo(p);
        }
    }

    private String tail(String[] a, int from) {
        if (a.length <= from) return "";
        return String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length));
    }

    // ---- helpers ----------------------------------------------------------

    private boolean req(CommandSender s) {
        if (s instanceof Player) return true;
        Msg.sendRaw(s, "<red>Players only.");
        return false;
    }
    private boolean perm(CommandSender s, String node) {
        // Operators are always staff/admin; a permission node also grants it.
        if (s.isOp() || s.hasPermission(node)) return true;
        Msg.sendRaw(s, "<red>You don't have permission.");
        return false;
    }
    private boolean need(CommandSender s, String[] a, int n, String usage) {
        if (a.length >= n) return true;
        Msg.sendRaw(s, "<red>Usage: <white>" + usage);
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (a.length == 1) {
            String name = cmd.getName().toLowerCase();
            if (List.of("pay", "tpa", "tpahere", "msg", "tell", "ignore", "unignore", "seen",
                    "findplayer", "ping", "bounty", "stats", "balance", "shards", "duel",
                    "ban", "tempban", "unban", "kick", "mute", "unmute").contains(name)) {
                String p = a[0].toLowerCase();
                List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(p)).collect(Collectors.toList());
                if (name.equals("duel")) { names.addAll(prefix(a[0], List.of("accept", "deny"))); }
                return names;
            }
            if (name.equals("trap")) return prefix(a[0], List.of("set", "remove", "info"));
            if (name.equals("rtp")) return prefix(a[0], List.of("overworld", "nether", "end"));
            if (name.equals("mintsmp")) return prefix(a[0], List.of("help", "version", "reload"));
            if (name.equals("team")) return prefix(a[0], List.of("create", "invite", "accept", "leave", "kick", "disband", "info", "chat", "sethome", "home"));
            if (name.equals("orders")) return prefix(a[0], List.of("create", "cancel"));
            if (name.equals("kit")) return prefix(a[0], plugin.kits().kitIds());
            if (name.equals("pwarp")) {
                List<String> opts = new ArrayList<>(List.of("set", "delete", "list"));
                plugin.db().pwarpList().forEach(w -> opts.add(w.name()));
                return prefix(a[0], opts);
            }
        }
        return new ArrayList<>();
    }

    private List<String> prefix(String typed, List<String> opts) {
        String t = typed.toLowerCase();
        return opts.stream().filter(o -> o.startsWith(t)).collect(Collectors.toList());
    }
}
