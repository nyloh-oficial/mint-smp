package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight 1v1 duels: request -> accept -> fight -> winner announced. */
public final class Duels {

    private final MintSMP plugin;
    private final Map<UUID, UUID> requests = new HashMap<>();   // target -> requester
    private final Map<UUID, UUID> active = new HashMap<>();     // both directions
    private final Map<UUID, Location> returnLoc = new HashMap<>();

    public Duels(MintSMP plugin) { this.plugin = plugin; }

    public boolean inDuel(Player p) { return active.containsKey(p.getUniqueId()); }
    public boolean dueling(Player a, Player b) {
        return a.getUniqueId().equals(active.get(b.getUniqueId()));
    }

    public void request(Player from, Player to) {
        if (from.equals(to)) { Msg.sendRaw(from, "<red>You can't duel yourself."); return; }
        if (inDuel(from) || inDuel(to)) { Msg.sendRaw(from, "<red>Someone is already in a duel."); return; }
        requests.put(to.getUniqueId(), from.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> requests.remove(to.getUniqueId()), 60 * 20L);
        Msg.sendRaw(to, "<green>" + from.getName() + " <gray>challenged you to a duel! <white>/duel accept " + from.getName());
        Msg.sendRaw(from, "<gray>Duel request sent to <white>" + to.getName() + "</white>.");
    }

    public void accept(Player target, String requesterName) {
        UUID reqId = requests.get(target.getUniqueId());
        if (reqId == null) { Msg.sendRaw(target, "<red>No pending duel request."); return; }
        Player req = Bukkit.getPlayer(reqId);
        if (req == null) { requests.remove(target.getUniqueId()); Msg.sendRaw(target, "<red>That player went offline."); return; }
        if (requesterName != null && !req.getName().equalsIgnoreCase(requesterName)) { Msg.sendRaw(target, "<red>No request from " + requesterName + "."); return; }
        requests.remove(target.getUniqueId());
        start(req, target);
    }

    public void deny(Player target) {
        if (requests.remove(target.getUniqueId()) != null) Msg.sendRaw(target, "<gray>Duel denied.");
        else Msg.sendRaw(target, "<red>No pending duel request.");
    }

    private void start(Player a, Player b) {
        returnLoc.put(a.getUniqueId(), a.getLocation());
        returnLoc.put(b.getUniqueId(), b.getLocation());
        active.put(a.getUniqueId(), b.getUniqueId());
        active.put(b.getUniqueId(), a.getUniqueId());
        heal(a); heal(b);
        Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <yellow>" + a.getName() + " <gray>vs <yellow>" + b.getName() + " <gray>— duel!"));
        Msg.sendRaw(a, "<green>Duel started vs <white>" + b.getName() + "</white>! <gray>Teleporting to an arena...");
        Msg.sendRaw(b, "<green>Duel started vs <white>" + a.getName() + "</white>!");
        // RTP player a to a random arena, then pull b to a once they've landed.
        plugin.teleport().rtp(a, "overworld");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (a.isOnline() && b.isOnline() && inDuel(a) && inDuel(b)) {
                b.teleport(a.getLocation());
                heal(a); heal(b);
            }
        }, 80L);
    }

    /** Called from the death listener; returns true if the death ended a duel. */
    public boolean onDeath(Player victim) {
        UUID oppId = active.get(victim.getUniqueId());
        if (oppId == null) return false;
        Player opp = Bukkit.getPlayer(oppId);
        end(victim.getUniqueId());
        end(oppId);
        if (opp != null) {
            heal(opp);
            Location back = returnLoc.remove(oppId);
            if (back != null) opp.teleport(back);
            Bukkit.broadcast(Msg.mm("<#3DDC84>[Mint] <yellow>" + opp.getName() + " <green>won the duel against <yellow>" + victim.getName() + "</yellow>!"));
        }
        returnLoc.remove(victim.getUniqueId());
        return true;
    }

    /** Cancel a duel if a participant disconnects. */
    public void onQuit(Player p) {
        UUID oppId = active.get(p.getUniqueId());
        if (oppId == null) return;
        end(p.getUniqueId());
        end(oppId);
        Player opp = Bukkit.getPlayer(oppId);
        if (opp != null) {
            Location back = returnLoc.remove(oppId);
            if (back != null) opp.teleport(back);
            Msg.sendRaw(opp, "<gray>Your opponent left — duel cancelled.");
        }
        returnLoc.remove(p.getUniqueId());
    }

    private void end(UUID id) { active.remove(id); }

    private void heal(Player p) {
        double max = p.getMaxHealth();
        p.setHealth(max);
        p.setFoodLevel(20);
        p.setFireTicks(0);
    }
}
