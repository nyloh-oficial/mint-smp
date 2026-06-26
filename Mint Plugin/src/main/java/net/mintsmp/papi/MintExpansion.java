package net.mintsmp.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.mintsmp.MintSMP;
import net.mintsmp.util.Amounts;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * PlaceholderAPI expansion. Register only when PAPI is installed.
 * Placeholders: %mintsmp_money% %mintsmp_shards% %mintsmp_kills% %mintsmp_deaths%
 *               %mintsmp_kd% %mintsmp_blocks% %mintsmp_bounty% %mintsmp_team%
 */
public final class MintExpansion extends PlaceholderExpansion {

    private final MintSMP plugin;
    public MintExpansion(MintSMP plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "mintsmp"; }
    @Override public @NotNull String getAuthor() { return "nyloh-oficial"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        UUID id = player.getUniqueId();
        return switch (params.toLowerCase()) {
            case "money" -> Amounts.money(plugin.economy().money(id));
            case "money_raw" -> String.valueOf(plugin.economy().money(id));
            case "shards" -> String.valueOf(plugin.economy().shards(id));
            case "kills" -> String.valueOf(plugin.economy().kills(id));
            case "deaths" -> String.valueOf(plugin.economy().deaths(id));
            case "kd" -> String.format("%.2f", plugin.economy().kd(id));
            case "blocks" -> String.valueOf(plugin.economy().blocks(id));
            case "bounty" -> Amounts.money(plugin.db().bounty(id));
            case "team" -> {
                Long t = plugin.db().teamOf(id);
                yield t == null ? "" : plugin.db().teamName(t);
            }
            default -> null;
        };
    }
}
