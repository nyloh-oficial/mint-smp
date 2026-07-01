package net.mintsmp.listener;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerListPingEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

/** Chat formatting, join/quit messages, MOTD, god mode, and /back recording. */
public final class ServerListener implements Listener {

    private final MintSMP plugin;
    public ServerListener(MintSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.extras().setBack(e.getPlayer(), e.getFrom());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.extras().isGod(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;
        Player p = e.getPlayer();

        // muted players can't chat
        long muteMs = plugin.db().muteRemaining(p.getUniqueId());
        if (muteMs > 0) {
            e.setCancelled(true);
            String left = muteMs > 3_000_000_000L ? "permanently" : "for " + net.mintsmp.feature.Kits.human(muteMs);
            Msg.sendRaw(p, "<red>You are muted " + left + ".");
            return;
        }

        // hide messages from players the viewer is ignoring
        e.viewers().removeIf(a -> a instanceof Player vp
                && plugin.db().isIgnoring(vp.getUniqueId(), p.getUniqueId()));

        Long team = plugin.db().teamOf(p.getUniqueId());
        String teamTag = team != null ? "<dark_green>[" + plugin.db().teamName(team) + "]</dark_green> " : "";
        String fmt = plugin.getConfig().getString("chat.format", "%team%<gray>%player%<dark_gray>: <white>%message%");
        String base = fmt.replace("%team%", teamTag).replace("%player%", p.getName());
        String[] parts = base.split("%message%", 2);
        final String head = parts[0];
        final String tail = parts.length > 1 ? parts[1] : "";
        e.renderer((source, sourceDisplayName, message, viewer) -> {
            var out = Msg.mm(head).append(message);
            return tail.isEmpty() ? out : out.append(Msg.mm(tail));
        });
    }

    @EventHandler
    public void onJoinMessage(PlayerJoinEvent e) {
        String fmt = plugin.getConfig().getString("messages-events.join", "");
        if (fmt == null) return;
        if (fmt.isBlank()) { e.joinMessage(null); return; }
        e.joinMessage(Msg.mm(fmt.replace("%player%", e.getPlayer().getName())));
    }

    @EventHandler
    public void onQuitMessage(PlayerQuitEvent e) {
        String fmt = plugin.getConfig().getString("messages-events.quit", "");
        if (fmt == null) return;
        if (fmt.isBlank()) { e.quitMessage(null); return; }
        e.quitMessage(Msg.mm(fmt.replace("%player%", e.getPlayer().getName())));
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("motd.enabled", false)) return;
        String l1 = plugin.getConfig().getString("motd.line1", "<#3DDC84>Mint SMP");
        String l2 = plugin.getConfig().getString("motd.line2", "<gray>Economy + PvP");
        e.setMotd(LegacyComponentSerializer.legacySection().serialize(Msg.mm(l1 + "\n" + l2)));
    }
}
