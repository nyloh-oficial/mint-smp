package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.storage.Database;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.entity.Player;

/** Player-created public warps. Costs money to set up; anyone can visit. */
public final class PlayerWarps {

    private final MintSMP plugin;
    private final Economy economy;
    private final Teleport teleport;

    public PlayerWarps(MintSMP plugin, Economy economy, Teleport teleport) {
        this.plugin = plugin; this.economy = economy; this.teleport = teleport;
    }

    public void create(Player p, String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{2,16}")) { Msg.sendRaw(p, "<red>Name must be 2-16 letters/numbers."); return; }
        if (plugin.db().pwarpGet(name) != null) { Msg.sendRaw(p, "<red>That warp name is taken."); return; }
        int max = plugin.getConfig().getInt("pwarp.max-per-player", 1);
        if (plugin.db().pwarpCount(p.getUniqueId()) >= max && !p.hasPermission("mint.admin")) {
            Msg.sendRaw(p, "<red>You can only have " + max + " player warp(s)."); return;
        }
        double cost = plugin.getConfig().getDouble("pwarp.create-cost", 10000);
        if (!economy.takeMoney(p.getUniqueId(), cost)) { Msg.sendRaw(p, "<red>You need " + Amounts.money(cost) + " to create a warp."); return; }
        if (!plugin.db().pwarpCreate(name, p.getUniqueId(), p.getName(), p.getLocation())) {
            economy.addMoney(p.getUniqueId(), cost);
            Msg.sendRaw(p, "<red>Could not create the warp."); return;
        }
        Msg.sendRaw(p, "<green>Player warp <white>" + name + "</white> created. Others use <white>/pwarp " + name + "</white>.");
    }

    public void delete(Player p, String name) {
        Database.PWarp w = plugin.db().pwarpGet(name);
        if (w == null) { Msg.sendRaw(p, "<red>No warp named <white>" + name + "</white>."); return; }
        if (!p.getUniqueId().equals(w.owner()) && !p.hasPermission("mint.admin")) { Msg.sendRaw(p, "<red>That isn't your warp."); return; }
        plugin.db().pwarpDelete(name);
        Msg.sendRaw(p, "<gray>Warp <white>" + name + "</white> deleted.");
    }

    public void visit(Player p, String name) {
        Database.PWarp w = plugin.db().pwarpGet(name);
        if (w == null) { Msg.sendRaw(p, "<red>No warp named <white>" + name + "</white>."); return; }
        if (w.loc() == null) { Msg.sendRaw(p, "<red>That warp's world isn't loaded."); return; }
        plugin.db().pwarpVisit(name);
        teleport.warp(p, w.loc(), "pwarp " + name);
    }
}
