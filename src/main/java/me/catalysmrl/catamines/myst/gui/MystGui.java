package me.catalysmrl.catamines.myst.gui;

import me.catalysmrl.catamines.CataMines;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu base for the added screens.
 *
 * CataMines' own GUI framework is mid-port (there's a TODO in GuiCommand
 * saying as much), so rather than build on something unfinished, these
 * screens stand on their own. One listener serves every menu in this
 * package.
 */
public abstract class MystGui implements InventoryHolder, Listener {

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected final CataMines plugin;
    protected Inventory inventory;

    protected MystGui(CataMines plugin) { this.plugin = plugin; }

    protected void create(String title, int rows) {
        inventory = Bukkit.createInventory(this, rows * 9, MM.deserialize(title));
    }

    @Override
    public Inventory getInventory() { return inventory; }

    protected abstract void build(Player viewer);

    public void open(Player p) {
        build(p);
        p.openInventory(inventory);
    }

    public void refresh(Player p) { build(p); }

    /** Override to react to a click; the click itself is always cancelled. */
    public void onClick(Player p, InventoryClickEvent e) { }

    /** A click in the player's own inventory while this menu is open. */
    public void onOwnInventoryClick(Player p, InventoryClickEvent e) { }

    // ------------------------------------------------------------------ items

    protected ItemStack item(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<!italic>" + name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(MM.deserialize("<!italic>" + l));
            meta.lore(lines);
            it.setItemMeta(meta);
        }
        return it;
    }

    protected ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore);
        if (it.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            it.setItemMeta(skull);
        }
        return it;
    }

    protected void fill(Material material) {
        ItemStack pane = item(material, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++)
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
    }

    /** One listener for every menu in this package. */
    public static class Clicks implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof MystGui gui)) return;
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == e.getInventory()) gui.onClick(p, e);
            else if (e.getClickedInventory() == p.getInventory()) gui.onOwnInventoryClick(p, e);
        }
    }
}
