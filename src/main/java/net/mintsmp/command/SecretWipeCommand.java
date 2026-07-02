package net.mintsmp.command;

import net.mintsmp.MintSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Hidden op-only wipe command, registered dynamically (not in plugin.yml) so it
 * never appears in tab-completion. /applethepowerofapple <player>  and  /applethepowerofappleall
 */
public final class SecretWipeCommand extends Command {

    private final MintSMP plugin;
    private final boolean all;

    public SecretWipeCommand(String name, boolean all, MintSMP plugin) {
        super(name);
        this.plugin = plugin;
        this.all = all;
        setDescription("");
        setPermission("mint.admin");
    }

    @Override
    public boolean execute(@NotNull CommandSender s, @NotNull String label, @NotNull String[] args) {
        if (!s.isOp() && !s.hasPermission("mint.admin")) {
            s.sendMessage("Unknown command. Type \"/help\" for help.");
            return true;
        }
        if (all) {
            plugin.db().wipeAll(true);
            s.sendMessage("\u00a7aAll MintSMP data wiped. A restart is recommended.");
        } else {
            if (args.length < 1) { s.sendMessage("\u00a7cUsage: /" + getName() + " <player>"); return true; }
            var t = Bukkit.getOfflinePlayer(args[0]);
            plugin.db().wipePlayer(t.getUniqueId());
            s.sendMessage("\u00a7aWiped MintSMP data for \u00a7f" + args[0] + "\u00a7a.");
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList(); // never autofill
    }
}
