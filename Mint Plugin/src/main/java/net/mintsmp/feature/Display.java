package net.mintsmp.feature;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/** Green "Mint SMP" sidebar, refreshed periodically. */
public final class Display {

    private final MintSMP plugin;
    private final Economy economy;
    private final Afk afk;
    private final java.util.Set<java.util.UUID> hidden = new java.util.HashSet<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public Display(MintSMP plugin, Economy economy, Afk afk) { this.plugin = plugin; this.economy = economy; this.afk = afk; }

    /** Toggle the sidebar for a player. Returns true if it is now hidden. */
    public boolean toggleSidebar(Player p) {
        if (hidden.add(p.getUniqueId())) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            return true;
        }
        hidden.remove(p.getUniqueId());
        update(p);
        return false;
    }

    public void update(Player p) {
        if (hidden.contains(p.getUniqueId())) return;
        if (!p.getScoreboard().getObjectives().stream().anyMatch(o -> o.getName().equals("mint"))) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("mint", Criteria.DUMMY,
                    Msg.mm("<gradient:#3DDC84:#1B5E20><bold>Mint SMP</bold></gradient>"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            p.setScoreboard(board);
        }
        Scoreboard board = p.getScoreboard();
        Objective obj = board.getObjective("mint");
        if (obj == null) return;
        // clear old entries
        for (String e : new ArrayList<>(board.getEntries())) board.resetScores(e);

        List<String> lines = new ArrayList<>();
        lines.add(legacy("<dark_gray>\u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"));
        lines.add(legacy("<gray>Money: <#A8FF60>" + Amounts.money(economy.money(p.getUniqueId()))));
        lines.add(legacy("<gray>Shards: <aqua>" + Amounts.shards(economy.shards(p.getUniqueId()))));
        lines.add(legacy("<gray>K/D: <white>" + economy.kills(p.getUniqueId()) + "/" + economy.deaths(p.getUniqueId())));
        if (afk.isAfk(p)) lines.add(legacy("<yellow>[AFK]"));
        lines.add(legacy("<dark_gray>\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"));
        lines.add(legacy("<#3DDC84>" + plugin.getConfig().getString("general.server-ip", "play.mintsmp.net")));

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            // ensure uniqueness with trailing reset codes
            String entry = lines.get(i) + "\u00a7r".repeat(i % 10) + (i >= 10 ? "\u00a7" + (char) ('a' + (i - 10)) : "");
            if (entry.length() > 40) entry = entry.substring(0, 40);
            obj.getScore(entry).setScore(score--);
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) { update(p); updateTablist(p); }
    }

    public void updateTablist(Player p) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) return;
        String ip = plugin.getConfig().getString("general.server-ip", "play.mintsmp.net");
        var header = Msg.mm("<gradient:#3DDC84:#1B5E20><bold>Mint SMP</bold></gradient>\n"
                + "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size());
        var footer = Msg.mm("<gray>Money: <#A8FF60>" + Amounts.money(economy.money(p.getUniqueId()))
                + " <dark_gray>| <gray>Shards: <aqua>" + Amounts.shards(economy.shards(p.getUniqueId())) + "\n"
                + "<#3DDC84>" + ip);
        p.sendPlayerListHeaderAndFooter(header, footer);
    }

    private String legacy(String mini) { return LEGACY.serialize(Msg.mm(mini)); }
}
