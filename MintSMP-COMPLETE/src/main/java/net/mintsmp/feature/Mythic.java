package net.mintsmp.feature;

import net.mintsmp.MintSMP;
import net.mintsmp.core.Economy;
import net.mintsmp.util.Amounts;
import net.mintsmp.util.Items;
import net.mintsmp.util.Keys;
import net.mintsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Limited-edition "Mythic" gear: capped supply, serial-numbered, very OP. */
public final class Mythic {

    private final MintSMP plugin;
    private final Economy economy;
    private YamlConfiguration cfg;
    private final Map<UUID, Long> earthshakerCd = new HashMap<>();

    // ore -> smelted result for World Eater auto-smelt
    private static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);
    static {
        SMELT.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    }

    public Mythic(MintSMP plugin, Economy economy) { this.plugin = plugin; this.economy = economy; }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "mythic.yml");
        if (!f.exists()) plugin.saveResource("mythic.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    // ---- catalog ----------------------------------------------------------

    public List<String> types() {
        ConfigurationSection sec = cfg.getConfigurationSection("mythic.items");
        return sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean exists(String type) { return cfg.getConfigurationSection("mythic.items." + type) != null; }
    public String display(String type) { return cfg.getString("mythic.items." + type + ".display", "<gold>" + type); }
    public Material material(String type) {
        Material m = Material.matchMaterial(cfg.getString("mythic.items." + type + ".material", "NETHER_STAR"));
        return m == null ? Material.NETHER_STAR : m;
    }
    public double price(String type) { return cfg.getDouble("mythic.items." + type + ".price", 1_000_000_000d); }
    public int cap(String type) { return cfg.getInt("mythic.items." + type + ".cap", cfg.getInt("mythic.default-cap", 5)); }
    public int minted(String type) { return plugin.db().mythicMinted(type); }
    public int remaining(String type) { return Math.max(0, cap(type) - minted(type)); }
    public boolean soulbound() { return cfg.getBoolean("mythic.soulbound", false); }
    private double fx(String key, double def) { return cfg.getDouble("mythic.effects." + key, def); }

    public String typeOf(ItemStack it) { return Items.getString(it, Keys.MYTHIC); }
    public boolean isMythic(ItemStack it) { return typeOf(it) != null; }

    // ---- minting ----------------------------------------------------------

    /** Buy/give a mythic. charge=true takes money. Returns true on success. */
    public boolean mint(Player p, String type, boolean charge) {
        if (!exists(type)) { Msg.sendRaw(p, "<red>Unknown mythic item."); return false; }
        if (remaining(type) <= 0) { Msg.sendRaw(p, "<red>" + plain(type) + " is <bold>SOLD OUT</bold> (all " + cap(type) + " minted)."); return false; }
        double price = price(type);
        if (charge && !economy.takeMoney(p.getUniqueId(), price)) {
            Msg.sendRaw(p, "<red>You need " + Amounts.money(price) + " for that."); return false;
        }
        int serial = plugin.db().mythicTryMint(type, cap(type));
        if (serial < 0) {
            if (charge) economy.addMoney(p.getUniqueId(), price);
            Msg.sendRaw(p, "<red>Just sold out — try another."); return false;
        }
        for (ItemStack it : createItems(type, serial))
            for (ItemStack rem : p.getInventory().addItem(it).values())
                p.getWorld().dropItemNaturally(p.getLocation(), rem);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
        Bukkit.broadcast(Msg.mm("<gradient:#3DDC84:#1B5E20>[Mythic]</gradient> <white>" + p.getName()
                + " <gray>obtained " + display(type) + " <gray>#" + serial + "/" + cap(type) + "<gray>!"));
        return true;
    }

    // ---- item construction -----------------------------------------------

    public List<ItemStack> createItems(String type, int serial) {
        List<ItemStack> out = new ArrayList<>();
        int cap = cap(type);
        switch (type) {
            case "AEGIS" -> {
                out.add(piece(Material.NETHERITE_HELMET, type, serial, cap, "protection:8"));
                out.add(piece(Material.NETHERITE_CHESTPLATE, type, serial, cap, "protection:8"));
                out.add(piece(Material.NETHERITE_LEGGINGS, type, serial, cap, "protection:8"));
                out.add(piece(Material.NETHERITE_BOOTS, type, serial, cap, "protection:8"));
            }
            case "EXCALIBUR" -> out.add(piece(material(type), type, serial, cap, "sharpness:10,fire_aspect:5,knockback:3"));
            case "WORLD_EATER" -> out.add(piece(material(type), type, serial, cap, "efficiency:10,fortune:5,unbreaking:5"));
            case "STORMCALLER" -> out.add(piece(material(type), type, serial, cap, "power:8,infinity:1,unbreaking:5,flame:1"));
            case "EARTHSHAKER" -> out.add(piece(material(type), type, serial, cap, "density:5,unbreaking:5"));
            default -> out.add(piece(material(type), type, serial, cap, ""));
        }
        return out;
    }

    private ItemStack piece(Material mat, String type, int serial, int cap, String enchants) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Mythic \u2022 #" + serial + "/" + cap);
        for (String line : summary(type)) lore.add("<gray>" + line);
        if (soulbound()) lore.add("<aqua>Soulbound");
        ItemStack it = Items.named(mat, display(type) + " <gray>#" + serial, lore);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setUnbreakable(true); it.setItemMeta(m); }
        if (!enchants.isBlank()) Items.applyEnchants(it, enchants);
        Items.tagString(it, Keys.MYTHIC, type);
        Items.tagInt(it, Keys.MYTHIC_SERIAL, serial);
        return it;
    }

    private List<String> summary(String type) {
        return switch (type) {
            case "EXCALIBUR" -> List.of("Lifesteal on hit + 25% lightning strike");
            case "WORLD_EATER" -> List.of("5x5x5 vein break, auto-smelt, Fortune 5");
            case "AEGIS" -> List.of("Full set: +10 hearts, Resist II,", "Fire Resist, no fall damage");
            case "STORMCALLER" -> List.of("Arrows strike lightning & explode");
            case "PHOENIX" -> List.of("No wall-slam dmg, fireproof, slow-fall");
            case "HEART_OF_VOID" -> List.of("Hold for Str II, Speed II, Haste III,", "Night Vision, Regen, +6 hearts");
            case "EARTHSHAKER" -> List.of("Right-click: shockwave knockback");
            case "CORNUCOPIA" -> List.of("Reusable auto-totem (5-min cooldown)");
            default -> List.of();
        };
    }

    private String plain(String type) { return type.charAt(0) + type.substring(1).toLowerCase().replace('_', ' '); }

    // ---- effect predicates ------------------------------------------------

    private boolean isType(ItemStack it, String type) { return type.equals(typeOf(it)); }

    public int aegisWorn(Player p) {
        int n = 0;
        var inv = p.getInventory();
        if (isType(inv.getHelmet(), "AEGIS")) n++;
        if (isType(inv.getChestplate(), "AEGIS")) n++;
        if (isType(inv.getLeggings(), "AEGIS")) n++;
        if (isType(inv.getBoots(), "AEGIS")) n++;
        return n;
    }

    public boolean hasAmulet(Player p) {
        for (ItemStack it : p.getInventory().getStorageContents()) if (isType(it, "HEART_OF_VOID")) return true;
        return false;
    }

    public boolean wearingPhoenix(Player p) { return isType(p.getInventory().getChestplate(), "PHOENIX"); }

    public boolean hasCornucopia(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isType(it, "CORNUCOPIA")) return true;
        return false;
    }

    // ---- effects ----------------------------------------------------------

    public void onWeaponHit(Player attacker, LivingEntity victim, ItemStack weapon) {
        if (!isType(weapon, "EXCALIBUR")) return;
        double heal = fx("excalibur-lifesteal-hearts", 1.0) * 2.0;
        attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
        if (ThreadLocalRandom.current().nextDouble() < fx("excalibur-lightning-chance", 0.25)) {
            victim.getWorld().strikeLightning(victim.getLocation());
        }
    }

    /** World Eater AoE break (called from break listener; event already cancelled). */
    public boolean worldEaterBreak(Player p, Block origin, ItemStack tool) {
        if (!isType(tool, "WORLD_EATER")) return false;
        int r = (int) fx("world-eater-radius", 2);
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    Block b = origin.getRelative(dx, dy, dz);
                    Material t = b.getType();
                    if (t.isAir() || t == Material.BEDROCK || t.getHardness() < 0) continue;
                    for (ItemStack drop : b.getDrops(tool)) {
                        Material smelted = SMELT.get(b.getType());
                        if (smelted != null) drop = new ItemStack(smelted, drop.getAmount());
                        for (ItemStack rem : p.getInventory().addItem(drop).values())
                            b.getWorld().dropItemNaturally(b.getLocation(), rem);
                    }
                    b.setType(Material.AIR);
                    plugin.economy().addBlock(p.getUniqueId());
                }
        return true;
    }

    public void earthshakerPound(Player p, ItemStack item) {
        if (!isType(item, "EARTHSHAKER")) return;
        int cd = (int) fx("earthshaker-cooldown-seconds", 3);
        long now = System.currentTimeMillis();
        Long until = earthshakerCd.get(p.getUniqueId());
        if (until != null && until > now) return;
        earthshakerCd.put(p.getUniqueId(), now + cd * 1000L);

        double radius = fx("earthshaker-radius", 5.0);
        double dmg = fx("earthshaker-damage", 8.0);
        Location c = p.getLocation();
        p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, c, 3);
        p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(c, radius, radius, radius)) {
            if (e == p || !(e instanceof LivingEntity le)) continue;
            le.damage(dmg, p);
            Vector push = le.getLocation().toVector().subtract(c.toVector()).normalize().multiply(1.6).setY(0.7);
            le.setVelocity(push);
        }
    }

    public void tagArrow(org.bukkit.entity.Entity arrow, Player shooter) {
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (!isType(bow, "STORMCALLER")) {
            bow = shooter.getInventory().getItemInOffHand();
            if (!isType(bow, "STORMCALLER")) return;
        }
        arrow.getPersistentDataContainer().set(Keys.MYTHIC_ARROW, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
    }

    public boolean isMythicArrow(org.bukkit.entity.Entity arrow) {
        return arrow.getPersistentDataContainer().has(Keys.MYTHIC_ARROW, org.bukkit.persistence.PersistentDataType.INTEGER);
    }

    public void arrowImpact(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.strikeLightning(loc);
        w.createExplosion(loc, (float) fx("stormcaller-explosion-power", 2.0), false, false);
    }

    /** Returns true if Cornucopia saved the player (caller should cancel the lethal hit). */
    public boolean cornucopiaSave(Player p, double finalDamage) {
        if (p.getHealth() - finalDamage > 0) return false;
        if (!hasCornucopia(p)) return false;
        if (plugin.db().cooldownRemaining(p.getUniqueId(), "cornucopia") > 0) return false;
        int cd = (int) fx("cornucopia-cooldown-seconds", 300);
        plugin.db().setCooldown(p.getUniqueId(), "cornucopia", System.currentTimeMillis() + cd * 1000L);
        p.setHealth(p.getMaxHealth());
        apply(p, PotionEffectType.REGENERATION, 200, 2);
        apply(p, PotionEffectType.ABSORPTION, 200, 1);
        apply(p, PotionEffectType.FIRE_RESISTANCE, 200, 0);
        p.getWorld().strikeLightningEffect(p.getLocation());
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1f, 1f);
        Msg.sendRaw(p, "<gradient:#55ff55:#ffff55>Cornucopia revived you!</gradient> <gray>(cooldown " + Kits.human(cd * 1000L) + ")");
        return true;
    }

    // ---- passive tick (armor set + amulet + phoenix) ----------------------

    public void tickEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int healthAmp = -1;
            if (aegisWorn(p) >= 4) {
                apply(p, PotionEffectType.RESISTANCE, 40, 1);
                apply(p, PotionEffectType.FIRE_RESISTANCE, 40, 0);
                healthAmp = Math.max(healthAmp, 4); // +10 hearts
            }
            if (hasAmulet(p)) {
                apply(p, PotionEffectType.STRENGTH, 40, 1);
                apply(p, PotionEffectType.SPEED, 40, 1);
                apply(p, PotionEffectType.HASTE, 40, 2);
                apply(p, PotionEffectType.NIGHT_VISION, 220, 0);
                apply(p, PotionEffectType.REGENERATION, 40, 0);
                healthAmp = Math.max(healthAmp, 2); // +6 hearts
            }
            if (wearingPhoenix(p)) {
                apply(p, PotionEffectType.FIRE_RESISTANCE, 40, 0);
                if (!p.isGliding()) apply(p, PotionEffectType.SLOW_FALLING, 40, 0);
            }
            if (healthAmp >= 0) apply(p, PotionEffectType.HEALTH_BOOST, 60, healthAmp);
        }
    }

    private void apply(Player p, PotionEffectType type, int ticks, int amp) {
        if (type == null) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amp, true, false, false));
    }
}
