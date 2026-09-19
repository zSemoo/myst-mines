package me.catalysmrl.catamines.myst.pick;

import me.catalysmrl.catamines.CataMines;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pickaxe souls.
 *
 * A pick earns its own XP alongside its owner and gains one permanent trait
 * at each threshold — a Fortune tick, a self-repair, a chance at a key. The
 * levels live on the item itself, so the pick becomes the thing people care
 * about rather than the number in their profile: lose it and you lose the
 * soul, hand it down and the soul goes with it.
 *
 * Traits are deliberately small and few. A pick that does six things at once
 * can't be balanced and, more to the point, stops being legible.
 */
public class PickaxeSouls {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;
    private final NamespacedKey xpKey, levelKey, nameKey, loreKey;
    private YamlConfiguration cfg;
    private int breaksSinceRedraw;

    public PickaxeSouls(CataMines plugin) {
        this.plugin = plugin;
        this.xpKey = new NamespacedKey(plugin, "soul_xp");
        this.levelKey = new NamespacedKey(plugin, "soul_level");
        this.nameKey = new NamespacedKey(plugin, "soul_name");
        this.loreKey = new NamespacedKey(plugin, "soul_lore");
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "souls.yml");
        if (!f.exists()) plugin.saveResource("souls.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public boolean enabled() { return cfg.getBoolean("enabled", true); }

    // ------------------------------------------------------------------ the item

    public boolean isPick(ItemStack it) {
        return it != null && it.getType().name().endsWith("_PICKAXE");
    }

    public boolean hasSoul(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(levelKey, PersistentDataType.INTEGER);
    }

    public int level(ItemStack it) {
        if (!hasSoul(it)) return 0;
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    public double xp(ItemStack it) {
        if (!hasSoul(it)) return 0;
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.DOUBLE, 0d);
    }

    public long xpForNext(int level) {
        return Math.round(cfg.getDouble("curve.base", 250) * Math.pow(level, cfg.getDouble("curve.exponent", 1.5)));
    }

    public int maxLevel() { return cfg.getInt("max-level", 20); }

    /** Wakes a soul in a plain pickaxe. */
    public boolean awaken(Player p, ItemStack it) {
        if (!isPick(it)) { tell(p, cfg.getString("messages.not-a-pick", "<red>Hold a pickaxe.")); return false; }
        if (hasSoul(it)) { tell(p, cfg.getString("messages.already", "<gray>That pick already has a soul.")); return false; }
        var meta = it.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, 1);
        pdc.set(xpKey, PersistentDataType.DOUBLE, 0d);
        pdc.set(nameKey, PersistentDataType.STRING, p.getName());
        it.setItemMeta(meta);
        describe(it);
        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
        tell(p, cfg.getString("messages.awakened", "<gradient:#7de2ff:#e08cff>Something wakes in the pick.</gradient>"));
        return true;
    }

    // ------------------------------------------------------------------ earning

    /** Called on every block broken in a mine with a souled pick in hand. */
    public void onBreak(Player p, ItemStack pick, double blockXp) {
        if (!enabled() || !hasSoul(pick)) return;
        int level = level(pick);
        double gained = blockXp * cfg.getDouble("xp-share", 1.0);
        double now = xp(pick) + gained;

        int gains = 0;
        while (level < maxLevel() && now >= xpForNext(level)) {
            now -= xpForNext(level);
            level++;
            gains++;
        }

        var meta = pick.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(xpKey, PersistentDataType.DOUBLE, now);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pick.setItemMeta(meta);

        // Redraw the lore every few blocks, not only on a level: 250 blocks to
        // the first level with no visible movement reads as "not working".
        int every = Math.max(1, cfg.getInt("lore-refresh-every-blocks", 5));
        if (gains > 0 || (++breaksSinceRedraw % every) == 0) describe(pick);
        if (gains > 0) {
            String trait = traitAt(level);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
            tell(p, cfg.getString("messages.soul-level", "<gradient:#7de2ff:#e08cff>The pick sharpens.</gradient> <gray>Soul level {level}.")
                    .replace("{level}", String.valueOf(level)));
            if (trait != null)
                tell(p, cfg.getString("messages.trait", "<white>{trait}<gray> — earned, and permanent.")
                        .replace("{trait}", traitName(trait)));
        }
        applyTraits(p, pick, level);
    }

    /** The trait unlocked exactly at this level, if any. */
    public String traitAt(int level) {
        ConfigurationSection t = cfg.getConfigurationSection("traits");
        if (t == null) return null;
        for (String k : t.getKeys(false))
            if (t.getInt(k + ".level") == level) return k;
        return null;
    }

    public boolean has(ItemStack pick, String trait) {
        ConfigurationSection t = cfg.getConfigurationSection("traits." + trait);
        return t != null && level(pick) >= t.getInt("level", 999);
    }

    public String traitName(String trait) {
        return cfg.getString("traits." + trait + ".name", trait);
    }

    /** The traits that do something on each break. */
    private void applyTraits(Player p, ItemStack pick, int level) {
        // self-repair: a chance to shave damage off, so a favourite pick lasts
        if (has(pick, "mending_soul") && Math.random() < cfg.getDouble("traits.mending_soul.chance", 0.08)) {
            var meta = pick.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) {
                dmg.setDamage(Math.max(0, dmg.getDamage() - cfg.getInt("traits.mending_soul.repair", 2)));
                pick.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
            }
        }
        // a rare payout, which is what makes a high-level pick worth carrying
        if (has(pick, "lucky_soul") && Math.random() < cfg.getDouble("traits.lucky_soul.chance", 0.001))
            for (String cmd : cfg.getStringList("traits.lucky_soul.commands"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
    }

    /** Extra drops a souled pick adds, as a multiplier. */
    public double dropBonus(ItemStack pick) {
        if (!hasSoul(pick)) return 1;
        double per = cfg.getDouble("traits.rich_soul.per-level", 0.01);
        return has(pick, "rich_soul") ? 1 + level(pick) * per : 1;
    }

    // ------------------------------------------------------------------ the lore

    /**
     * The exact lines the soul last wrote, kept on the item.
     *
     * An earlier version marked its lines with a zero-width space, but a
     * resource pack can render that as a visible box — and it did. So no
     * markers: the plain text of every line we write is stored in the item's
     * PDC, and a redraw removes exactly those lines and leaves everything
     * else, wherever it sits.
     */
    private static final String SEP = "\u0001";

    private List<String> writtenLines(ItemStack pick) {
        if (!pick.hasItemMeta()) return List.of();
        String raw = pick.getItemMeta().getPersistentDataContainer()
                .getOrDefault(loreKey, PersistentDataType.STRING, "");
        return raw.isEmpty() ? List.of() : new ArrayList<>(Arrays.asList(raw.split(SEP, -1)));
    }

    /** Lines that belonged to a soul block written before this version. */
    private boolean looksLikeOldSoulLine(String plain) {
        String t = plain.strip();
        if (t.isEmpty()) return false;
        if (t.startsWith("\u200B")) return true;                 // the old marker
        if (t.startsWith("Souled Pickaxe") || t.startsWith("Woken by")) return true;
        if (t.matches("[|]+\\s+\\d+/(\\d+|max)")) return true;      // the xp bar
        ConfigurationSection traits = cfg.getConfigurationSection("traits");
        if (traits != null) for (String k : traits.getKeys(false)) {
            String name = traits.getString(k + ".name", k);
            if (t.equals("\u2714 " + name) || t.equals("\u2718 " + name)
                    || t.startsWith("\u2718 " + name + " (lv") || t.startsWith("\u2714 " + name)) return true;
        }
        return false;
    }

    /** Rewrites the soul's own lore lines, leaving every other line untouched. */
    public void describe(ItemStack pick) {
        if (!hasSoul(pick)) return;
        var meta = pick.getItemMeta();
        int level = level(pick);
        double xp = xp(pick);
        long need = xpForNext(level);
        String owner = meta.getPersistentDataContainer().getOrDefault(nameKey, PersistentDataType.STRING, "?");

        List<String> previous = writtenLines(pick);
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) for (Component line : meta.lore()) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            if (previous.contains(plain)) continue;          // ours, from last time
            if (looksLikeOldSoulLine(plain)) continue;       // ours, from an older build
            lore.add(line);
        }
        // a trailing blank left behind by the block we just removed
        while (!lore.isEmpty() && PlainTextComponentSerializer.plainText()
                .serialize(lore.get(lore.size() - 1)).trim().isEmpty())
            lore.remove(lore.size() - 1);

        // Build the new block, remembering each line exactly as it renders.
        List<String> written = new ArrayList<>();
        List<String> block = new ArrayList<>();
        block.add("<gradient:#7de2ff:#e08cff>Souled Pickaxe</gradient> <dark_gray>lv" + level);
        block.add("<gray>" + bar(level >= maxLevel() ? 1 : xp / need)
                + " <dark_gray>" + (long) xp + "/" + (level >= maxLevel() ? "max" : String.valueOf(need)));
        ConfigurationSection traits = cfg.getConfigurationSection("traits");
        if (traits != null) for (String k : traits.getKeys(false)) {
            int at = traits.getInt(k + ".level", 999);
            boolean got = level >= at;
            block.add((got ? "<green>\u2714 " : "<dark_gray>\u2718 ")
                    + (got ? "<white>" : "<dark_gray>") + traits.getString(k + ".name", k)
                    + (got ? "" : " <dark_gray>(lv" + at + ")"));
        }
        block.add("<dark_gray>Woken by " + owner);

        for (String line : block) {
            Component c = MM.deserialize("<!italic>" + line);
            lore.add(c);
            written.add(PlainTextComponentSerializer.plainText().serialize(c));
        }

        meta.getPersistentDataContainer().set(loreKey, PersistentDataType.STRING, String.join(SEP, written));
        meta.lore(lore);
        pick.setItemMeta(meta);
    }

    private String bar(double fraction) {
        int width = 16;
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
        StringBuilder sb = new StringBuilder("<gradient:#7de2ff:#e08cff>");
        for (int i = 0; i < width; i++) { if (i == filled) sb.append("<dark_gray>"); sb.append('|'); }
        return sb.toString();
    }

    private void tell(Player p, String msg) { p.sendMessage(MM.deserialize(msg)); }
}
