package net.mintsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.mintsmp.MintSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Central text helper. ALL player-facing strings live in messages.yml (MiniMessage).
 * Nothing user-facing should be hardcoded elsewhere.
 *
 * Usage: Msg.send(sender, "command.reloaded");
 *        Msg.send(sender, "economy.paid", "amount", "$10.00", "target", "Steve");
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static FileConfiguration messages;
    private static String prefix = "<#3DDC84>[Mint]</#3DDC84> <gray>";

    private Msg() {}

    /** (Re)load messages.yml from the plugin data folder, copying defaults on first run. */
    public static void load(MintSMP plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = messages.getString("prefix", prefix);
    }

    /** Raw lookup with MiniMessage parse, no prefix. Returns key itself if missing. */
    public static Component raw(String key, String... pairs) {
        String template = messages != null ? messages.getString(key, key) : key;
        return MM.deserialize(template, resolvers(pairs));
    }

    /** Lookup + prefix, then send. */
    public static void send(CommandSender to, String key, String... pairs) {
        String template = messages != null ? messages.getString(key, key) : key;
        to.sendMessage(MM.deserialize(prefix + template, resolvers(pairs)));
    }

    /** Send an already-written MiniMessage string (prefixed). */
    public static void sendRaw(CommandSender to, String miniMessage, String... pairs) {
        to.sendMessage(MM.deserialize(prefix + miniMessage, resolvers(pairs)));
    }

    /** Parse a MiniMessage string into a Component (no prefix, no lookup). */
    public static Component mm(String miniMessage, String... pairs) {
        return MM.deserialize(miniMessage, resolvers(pairs));
    }

    private static TagResolver[] resolvers(String... pairs) {
        List<TagResolver> list = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            list.add(Placeholder.parsed(pairs[i], pairs[i + 1]));
        }
        return list.toArray(new TagResolver[0]);
    }
}
