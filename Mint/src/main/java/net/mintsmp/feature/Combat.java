package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Combat-tag, anti-combat-log punishment, and bounties. */
public final class Combat {

    private final MintSMP plugin;
    private final Economy economy;
    private final Map<UUID, Long> tags = new HashMap<>(); // uuid -> tag-expiry epoch
    private int tagSeconds = 20;
    private boolean punishLog = true;

    public Combat(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    public void reload() {
        tagSeconds = plugin.getConfig().getInt("combat.tag-seconds", 20);
        punishLog = plugin.getConfig().getBoolean("combat.combat-log-punish", true);
    }

    public void tag(Player a, Player b) {
        long until = System.currentTimeMillis() + tagSeconds * 1000L;
        boolean wasA = isTagged(a), wasB = isTagged(b);
        tags.put(a.getUniqueId(), until);
        tags.put(b.getUniqueId(), until);
        if (!wasA) Msg.sendRaw(a, "<red>You are now in combat for " + tagSeconds + "s.");
        if (!wasB) Msg.sendRaw(b, "<red>You are now in combat for " + tagSeconds + "s.");
    }

    public boolean isTagged(Player p) {
        Long until = tags.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public void clear(Player p) { tags.remove(p.getUniqueId()); }

    /** Called on quit; if tagged and punishment on, kill + drop. */
    public void onQuit(Player p) {
        if (!punishLog || !isTagged(p)) { tags.remove(p.getUniqueId()); return; }
        tags.remove(p.getUniqueId());
        Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <red>" + p.getName() + " logged out in combat!"));
        // drop inventory and kill
        p.getInventory().forEach(it -> { if (it != null) p.getWorld().dropItemNaturally(p.getLocation(), it); });
        p.getInventory().clear();
        p.setHealth(0);
    }

    /** Action-bar countdown tick (call ~every second). */
    public void tickActionBars() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long until = tags.get(p.getUniqueId());
            if (until == null) continue;
            if (until <= now) { tags.remove(p.getUniqueId()); continue; }
            long secs = (until - now + 999) / 1000;
            p.sendActionBar(Msg.mm("<red>\u2694 Combat: <white>" + secs + "s"));
        }
    }

    // ---- bounties ---------------------------------------------------------

    public double bounty(UUID target) { return plugin.db().bounty(target); }

    public void addBounty(Player payer, UUID target, double amount) {
        if (!economy.takeMoney(payer.getUniqueId(), amount)) {
            Msg.sendRaw(payer, "<red>You can't afford that bounty.");
            return;
        }
        plugin.db().addBounty(target, amount);
        Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <yellow>A bounty of <white>" + Amounts.money(amount)
                + "</white> was placed! Total: <white>" + Amounts.money(plugin.db().bounty(target))));
    }

    /** Pay out and clear a target's bounty to the killer. */
    public void payoutBounty(Player killer, UUID target) {
        double amt = plugin.db().bounty(target);
        if (amt <= 0) return;
        plugin.db().clearBounty(target);
        economy.addMoney(killer.getUniqueId(), amt);
        Msg.sendRaw(killer, "<#A8FF60>Bounty claimed: <white>" + Amounts.money(amt));
    }
}
