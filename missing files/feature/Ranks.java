package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rank tiers driven by permission nodes (granted by LuckPerms). No hard
 * dependency on LuckPerms — we just read permissions. Perks are deliberately
 * cosmetic/convenience only (name color, extra homes) to keep the server fair.
 *
 * Config (config.yml):
 * ranks:
 *   tiers:
 *     vip:   { permission: "mint.rank.vip",   weight: 10, name-color: "<green>",  extra-homes: 1 }
 *     mvp:   { permission: "mint.rank.mvp",   weight: 20, name-color: "<aqua>",   extra-homes: 2 }
 *     elite: { permission: "mint.rank.elite", weight: 30, name-color: "<gold>",   extra-homes: 3 }
 */
public final class Ranks {

    private final MintSMP plugin;
    public Ranks(MintSMP plugin) { this.plugin = plugin; }

    private ConfigurationSection tiers() { return plugin.getConfig().getConfigurationSection("ranks.tiers"); }

    /** Highest-weight tier the player has the permission for, or null. */
    public String rankOf(Player p) {
        ConfigurationSection t = tiers();
        if (t == null) return null;
        String best = null; int bestW = Integer.MIN_VALUE;
        for (String key : t.getKeys(false)) {
            String perm = t.getString(key + ".permission", "mint.rank." + key);
            int w = t.getInt(key + ".weight", 0);
            if (p.hasPermission(perm) && w > bestW) { bestW = w; best = key; }
        }
        return best;
    }

    public String nameColor(Player p) {
        String r = rankOf(p);
        if (r == null) return "<gray>";
        return plugin.getConfig().getString("ranks.tiers." + r + ".name-color", "<gray>");
    }

    public int extraHomes(Player p) {
        String r = rankOf(p);
        if (r == null) return 0;
        return plugin.getConfig().getInt("ranks.tiers." + r + ".extra-homes", 0);
    }

    public List<String> tierNames() {
        ConfigurationSection t = tiers();
        return t == null ? new ArrayList<>() : new ArrayList<>(t.getKeys(false));
    }

    /**
     * Store-driven rank set. Without LuckPerms we can't persist a group, so we
     * try LuckPerms via console command if installed; otherwise we tell the admin.
     * Returns true if a command was dispatched.
     */
    public boolean applyRank(CommandSender console, OfflinePlayer target, String rank) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) return false;
        // Use LuckPerms' command to set the primary group (works for online/offline).
        String name = target.getName();
        if (name == null) return false;
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "lp user " + name + " parent set " + rank);
        return true;
    }
}
