package net.mintsmp.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Routes chat + command events through AntiSpam. */
public final class ChatListener implements Listener {

    private final MintSMP plugin;
    public ChatListener(MintSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        String reason = plugin.antiSpam().checkChat(p, msg);
        if (reason != null) {
            e.setCancelled(true);
            Msg.sendRaw(p, reason);
            return;
        }
        // Team-chat toggle: route to team only (must run on the main thread).
        if (plugin.settings().get(p, net.mintsmp.feature.Settings.TEAM_CHAT, false)) {
            e.setCancelled(true);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.teams().chat(p, msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.antiSpam().checkCommand(e.getPlayer())) {
            e.setCancelled(true);
            Msg.sendRaw(e.getPlayer(), "<red>Slow down — you're sending commands too fast.");
        }
    }
}
