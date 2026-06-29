package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Teams (clans): shared home, team chat, optional friendly-fire off. */
public final class Teams {

    private final MintSMP plugin;
    private final Teleport teleport;
    private final Map<UUID, Long> invites = new HashMap<>(); // invitee -> teamId

    public Teams(MintSMP plugin, Teleport teleport) { this.plugin = plugin; this.teleport = teleport; }

    private int maxMembers() { return plugin.getConfig().getInt("teams.max-members", 8); }
    private boolean friendlyFire() { return plugin.getConfig().getBoolean("teams.friendly-fire", false); }
    private boolean sharedHome() { return plugin.getConfig().getBoolean("teams.shared-home", true); }

    /** True if both players are on the same team (used for friendly-fire). */
    public boolean sameTeam(Player a, Player b) {
        Long ta = plugin.db().teamOf(a.getUniqueId());
        Long tb = plugin.db().teamOf(b.getUniqueId());
        return ta != null && ta.equals(tb);
    }

    public boolean friendlyFireBlocked(Player a, Player b) {
        return !friendlyFire() && sameTeam(a, b);
    }

    // ---- commands ---------------------------------------------------------

    public void create(Player p, String name) {
        if (plugin.db().teamOf(p.getUniqueId()) != null) { Msg.sendRaw(p, "<red>Leave your current team first."); return; }
        if (name == null || !name.matches("[A-Za-z0-9_]{2,16}")) { Msg.sendRaw(p, "<red>Name must be 2-16 letters/numbers."); return; }
        if (plugin.db().teamByName(name) != null) { Msg.sendRaw(p, "<red>That team name is taken."); return; }
        long id = plugin.db().teamCreate(name, p.getUniqueId());
        if (id < 0) { Msg.sendRaw(p, "<red>Could not create team."); return; }
        plugin.db().teamAddMember(id, p.getUniqueId());
        Msg.sendRaw(p, "<green>Team <white>" + name + "</white> created. Invite with <white>/team invite <player></white>.");
    }

    public void invite(Player p, String targetName) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null || !p.getUniqueId().equals(plugin.db().teamOwner(team))) { Msg.sendRaw(p, "<red>Only the team owner can invite."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Msg.sendRaw(p, "<red>That player isn't online."); return; }
        if (plugin.db().teamMembers(team).size() >= maxMembers()) { Msg.sendRaw(p, "<red>Team is full (" + maxMembers() + ")."); return; }
        invites.put(target.getUniqueId(), team);
        Bukkit.getScheduler().runTaskLater(plugin, () -> invites.remove(target.getUniqueId()), 60 * 20L);
        Msg.sendRaw(target, "<green>" + p.getName() + " <gray>invited you to <white>" + plugin.db().teamName(team) + "</white>. <white>/team accept");
        Msg.sendRaw(p, "<gray>Invite sent to <white>" + target.getName() + "</white>.");
    }

    public void accept(Player p) {
        Long team = invites.remove(p.getUniqueId());
        if (team == null) { Msg.sendRaw(p, "<red>No pending invite."); return; }
        if (plugin.db().teamOf(p.getUniqueId()) != null) { Msg.sendRaw(p, "<red>You're already in a team."); return; }
        if (plugin.db().teamMembers(team).size() >= maxMembers()) { Msg.sendRaw(p, "<red>That team is now full."); return; }
        plugin.db().teamAddMember(team, p.getUniqueId());
        broadcast(team, "<green>" + p.getName() + " <gray>joined the team.");
    }

    public void leave(Player p) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null) { Msg.sendRaw(p, "<red>You're not in a team."); return; }
        if (p.getUniqueId().equals(plugin.db().teamOwner(team))) { Msg.sendRaw(p, "<red>Owners must use <white>/team disband</white>."); return; }
        plugin.db().teamRemoveMember(team, p.getUniqueId());
        Msg.sendRaw(p, "<gray>You left the team.");
        broadcast(team, "<gray>" + p.getName() + " left the team.");
    }

    public void kick(Player p, String targetName) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null || !p.getUniqueId().equals(plugin.db().teamOwner(team))) { Msg.sendRaw(p, "<red>Only the owner can kick."); return; }
        UUID target = plugin.db().findByName(targetName);
        if (target == null || !team.equals(plugin.db().teamOf(target))) { Msg.sendRaw(p, "<red>That player isn't on your team."); return; }
        if (target.equals(p.getUniqueId())) { Msg.sendRaw(p, "<red>You can't kick yourself."); return; }
        plugin.db().teamRemoveMember(team, target);
        broadcast(team, "<gray>" + targetName + " was kicked from the team.");
        Player online = Bukkit.getPlayer(target);
        if (online != null) Msg.sendRaw(online, "<red>You were kicked from the team.");
    }

    public void disband(Player p) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null || !p.getUniqueId().equals(plugin.db().teamOwner(team))) { Msg.sendRaw(p, "<red>Only the owner can disband."); return; }
        broadcast(team, "<red>The team has been disbanded.");
        plugin.db().teamDelete(team);
    }

    public void info(Player p) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null) { Msg.sendRaw(p, "<gray>You're not in a team. <white>/team create <name></white>."); return; }
        UUID owner = plugin.db().teamOwner(team);
        String members = plugin.db().teamMembers(team).stream().map(u -> {
            Player on = Bukkit.getPlayer(u);
            String n = on != null ? on.getName() : Bukkit.getOfflinePlayer(u).getName();
            return (u.equals(owner) ? "\u2605" : "") + n;
        }).collect(Collectors.joining(", "));
        Msg.sendRaw(p, "<gradient:#3DDC84:#1B5E20><bold>" + plugin.db().teamName(team) + "</bold></gradient>");
        Msg.sendRaw(p, "<gray>Members: <white>" + members);
    }

    public void chat(Player p, String message) {
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null) { Msg.sendRaw(p, "<red>You're not in a team."); return; }
        broadcast(team, "<dark_green>[Team] <white>" + p.getName() + "<gray>: " + message.replace("<", "\u200b<"));
    }

    public void setHome(Player p) {
        if (!sharedHome()) { Msg.sendRaw(p, "<red>Team homes are disabled."); return; }
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null || !p.getUniqueId().equals(plugin.db().teamOwner(team))) { Msg.sendRaw(p, "<red>Only the owner can set the team home."); return; }
        plugin.db().teamSetHome(team, p.getLocation());
        Msg.sendRaw(p, "<green>Team home set.");
    }

    public void home(Player p) {
        if (!sharedHome()) { Msg.sendRaw(p, "<red>Team homes are disabled."); return; }
        Long team = plugin.db().teamOf(p.getUniqueId());
        if (team == null) { Msg.sendRaw(p, "<red>You're not in a team."); return; }
        Location l = plugin.db().teamHome(team);
        if (l == null) { Msg.sendRaw(p, "<red>No team home set."); return; }
        teleport.warp(p, l, "team home");
    }

    private void broadcast(long team, String mini) {
        for (UUID u : plugin.db().teamMembers(team)) {
            Player on = Bukkit.getPlayer(u);
            if (on != null) Msg.sendRaw(on, mini);
        }
    }
}
