package me.catalysmrl.catamines.myst.soul;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.events.CataMineBlockBreakEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Pickaxe souls.
 *
 * A pickaxe used in the mines earns XP of its own, kept on the item. At
 * thresholds it gains one permanent TRAIT, chosen from what its tier
 * offers: a Fortune tick, a self-repair tick, a chance to drop a key, a
 * bonus to the wielder's mining XP. Traits stack; the pick becomes an
 * heirloom, and losing it means something.
 *
 * Everything lives in the item's PDC, so it survives being dropped, traded
 * or put in an ender chest, and the lore is rewritten from that data so it
 * can never go stale.
 */
public class PickaxeSouls implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final NamespacedKey xpKey, levelKey, traitsKey;
    private YamlConfiguration cfg;
    private final Random random = new Random();

    public PickaxeSouls(CataMines plugin) {
        this.plugin = plugin;
        xpKey = new NamespacedKey(plugin, "soul-xp");
        levelKey = new NamespacedKey(plugin, "soul-level");
        traitsKey = new NamespacedKey(plugin, "soul-traits");
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "souls.yml");
        if (!f.exists()) plugin.saveResource("souls.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public boolean enabled() { return cfg.getBoolean("enabled", true); }

    // ------------------------------------------------------------------ reading

    private boolean isPickaxe(ItemStack it) {
        return it != null && it.getType().name().endsWith("_PICKAXE");
    }

    public int level(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m == null ? 0 : m.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    public double xp(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m == null ? 0 : m.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.DOUBLE, 0d);
    }

    public List<String> traits(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        String raw = m == null ? "" : m.getPersistentDataContainer().getOrDefault(traitsKey, PersistentDataType.STRING, "");
        return raw.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public boolean has(ItemStack it, String trait) { return traits(it).contains(trait); }

    /** XP for the pick to go from this soul level to the next. */
    public long xpForNext(int level) {
        return Math.round(cfg.getDouble("curve.base", 500) * Math.pow(Math.max(1, level + 1), cfg.getDouble("curve.exponent", 1.5)));
    }

    // ------------------------------------------------------------------ earning

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(CataMineBlockBreakEvent e) {
        if (!enabled()) return;
        Player p = e.getBlockBreakEvent().getPlayer();
        ItemStack pick = p.getInventory().getItemInMainHand();
        if (!isPickaxe(pick)) return;

        double gain = cfg.getDouble("xp-per-block", 1);
        ItemMeta m = pick.getItemMeta();
        if (m == null) return;
        var pdc = m.getPersistentDataContainer();
        double xp = pdc.getOrDefault(xpKey, PersistentDataType.DOUBLE, 0d) + gain;
        int level = pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
        boolean levelled = false;
        while (level < cfg.getInt("max-level", 10) && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
            levelled = true;
        }
        pdc.set(xpKey, PersistentDataType.DOUBLE, xp);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pick.setItemMeta(m);

        if (levelled) awaken(p, pick, level);
        else if (cfg.getBoolean("rewrite-lore", true) && random.nextInt(20) == 0) rewriteLore(pick);

        // the traits, doing their work
        applyTraits(p, pick, e);
    }

    /** A new soul level: pick a trait from the tier, tell them, rewrite the lore. */
    private void awaken(Player p, ItemStack pick, int level) {
        List<String> owned = traits(pick);
        List<String> pool = new ArrayList<>();
        ConfigurationSection tiers = cfg.getConfigurationSection("traits");
        if (tiers != null) for (String id : tiers.getKeys(false)) {
            ConfigurationSection t = tiers.getConfigurationSection(id);
            if (t == null || owned.contains(id)) continue;
            if (level >= t.getInt("from-level", 1)) pool.add(id);
        }
        String chosen = null;
        if (!pool.isEmpty()) {
            chosen = pool.get(random.nextInt(pool.size()));
            owned.add(chosen);
            ItemMeta m = pick.getItemMeta();
            m.getPersistentDataContainer().set(traitsKey, PersistentDataType.STRING, String.join(",", owned));
            pick.setItemMeta(m);
        }
        rewriteLore(pick);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.7f);
        p.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.5, 0.4, 0.5);
        String traitName = chosen == null ? null : cfg.getString("traits." + chosen + ".name", chosen);
        p.sendMessage(MM.deserialize(cfg.getString(chosen == null ? "messages.soul-level" : "messages.soul-trait",
                        "<gradient:#e08cff:#7de2ff>✦ Your pickaxe stirs.</gradient> <gray>Soul level {level}{trait}")
                .replace("{level}", String.valueOf(level))
                .replace("{trait}", traitName == null ? "" : " — it learned <white>" + traitName + "</white>.")));
    }

    /** What each trait does on a break. */
    private void applyTraits(Player p, ItemStack pick, CataMineBlockBreakEvent e) {
        for (String id : traits(pick)) {
            ConfigurationSection t = cfg.getConfigurationSection("traits." + id);
            if (t == null) continue;
            double chance = t.getDouble("chance", 0.05);
            if (random.nextDouble() > chance) continue;
            switch (t.getString("type", "").toLowerCase()) {
                case "fortune" -> {
                    // an extra drop of whatever the block gave
                    var drops = e.getBlockBreakEvent().getBlock().getDrops(pick, p);
                    for (ItemStack d : drops) p.getInventory().addItem(d).values()
                            .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                }
                case "repair" -> {
                    if (pick.getItemMeta() instanceof Damageable dmg && dmg.getDamage() > 0) {
                        dmg.setDamage(Math.max(0, dmg.getDamage() - t.getInt("amount", 25)));
                        pick.setItemMeta(dmg);
                    }
                }
                case "xp" -> plugin.getMineLevels().give(p, t.getDouble("amount", 5), false);
                case "command" -> {
                    for (String cmd : t.getStringList("commands"))
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
                    if (t.getBoolean("announce", false))
                        p.sendMessage(MM.deserialize(t.getString("message", "<gradient:#ffd166:#ff8c00>✦ {trait} triggered.</gradient>")
                                .replace("{trait}", t.getString("name", id))));
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ lore

    /** The lore is rebuilt from the data, so it can never disagree with it. */
    public void rewriteLore(ItemStack pick) {
        ItemMeta m = pick.getItemMeta();
        if (m == null) return;
        int level = level(pick);
        double xp = xp(pick);
        List<Component> lore = new ArrayList<>();
        // keep any lines that aren't ours
        List<Component> existing = m.lore();
        if (existing != null) for (Component c : existing) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            if (!plain.startsWith("Soul") && !plain.startsWith("  ") && !plain.isBlank() && !plain.startsWith("Traits")) lore.add(c);
        }
        lore.add(Component.empty());
        lore.add(MM.deserialize("<!italic><gradient:#e08cff:#7de2ff>Soul level " + level + "</gradient>"));
        int width = 15;
        int filled = (int) Math.round(width * Math.min(1, xp / (double) xpForNext(level)));
        StringBuilder bar = new StringBuilder("<#a0a0ff>");
        for (int i = 0; i < width; i++) { if (i == filled) bar.append("<dark_gray>"); bar.append('|'); }
        lore.add(MM.deserialize("<!italic>" + bar + " <gray>" + (long) xp + "<dark_gray>/" + xpForNext(level)));
        List<String> traits = traits(pick);
        if (!traits.isEmpty()) {
            lore.add(MM.deserialize("<!italic><gray>Traits:"));
            for (String id : traits)
                lore.add(MM.deserialize("<!italic>  <white>• " + cfg.getString("traits." + id + ".name", id)
                        + " <dark_gray>" + cfg.getString("traits." + id + ".blurb", "")));
        }
        m.lore(lore);
        pick.setItemMeta(m);
    }
}
