package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Pick-one job system. Mine as a Miner, harvest as a Farmer, fish as a Fisher —
 * and earn money for doing your job. Anyone can switch, so it's fair (no pay-to-win).
 */
public final class Jobs {

    public static final String NONE = "none";
    public static final String MINER = "miner";
    public static final String FARMER = "farmer";
    public static final String FISHER = "fisher";

    private final MintSMP plugin;
    public Jobs(MintSMP plugin) { this.plugin = plugin; }

    public String job(UUID uuid) { return plugin.db().getJob(uuid); }

    public boolean valid(String job) {
        return job.equals(MINER) || job.equals(FARMER) || job.equals(FISHER) || job.equals(NONE);
    }

    public void setJob(Player p, String job) {
        if (!valid(job)) { Msg.send(p, "<red>Unknown job. Choose miner, farmer, or fisher."); return; }
        plugin.db().setJob(p.getUniqueId(), job);
        if (job.equals(NONE)) Msg.send(p, "<gray>You left your job.");
        else Msg.send(p, "<green>You are now a <white>" + job + "</white>!");
    }

    private double cfg(String key, double def) { return plugin.getConfig().getDouble("jobs.pay." + key, def); }

    /** Mining payout (called from the block-break listener). */
    public void onMine(Player p, Material mat) {
        if (!MINER.equals(job(p.getUniqueId()))) return;
        double pay = switch (mat) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> cfg("diamond", 20);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> cfg("gold", 10);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> cfg("iron", 5);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> cfg("coal", 5);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> cfg("copper", 4);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> cfg("emerald", 100);
            default -> 0;
        };
        if (pay > 0) plugin.economy().addMoney(p.getUniqueId(), pay);
    }

    /** Farming payout for fully-grown crops. */
    public void onHarvest(Player p, Material mat) {
        if (!FARMER.equals(job(p.getUniqueId()))) return;
        double pay = switch (mat) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS -> cfg("crop", 2);
            case NETHER_WART -> cfg("crop", 2);
            default -> 0;
        };
        if (pay > 0) plugin.economy().addMoney(p.getUniqueId(), pay);
    }

    /** Fishing payout. */
    public void onFish(Player p) {
        if (!FISHER.equals(job(p.getUniqueId()))) return;
        plugin.economy().addMoney(p.getUniqueId(), cfg("fish", 5));
    }
}
