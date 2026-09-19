package me.catalysmrl.catamines.myst.pick;

import me.catalysmrl.catamines.CataMines;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /pick — wake a soul, or look at the one you have.
 */
public class PickCommand implements TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;

    public PickCommand(CataMines plugin) { this.plugin = plugin; }

    private void tell(CommandSender to, String msg) { to.sendMessage(MM.deserialize(msg)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { tell(sender, "<red>Players only."); return true; }
        PickaxeSouls souls = plugin.getPickaxeSouls();
        var held = p.getInventory().getItemInMainHand();

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!souls.hasSoul(held)) {
                tell(sender, "<gray>That pick has no soul. <white>/pick awaken <gray>wakes one.");
                return true;
            }
            int lv = souls.level(held);
            tell(sender, "<gradient:#7de2ff:#e08cff>Soul level " + lv + "</gradient> <dark_gray>"
                    + (long) souls.xp(held) + "/" + souls.xpForNext(lv));
            souls.describe(held);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "awaken", "wake" -> {
                if (!p.hasPermission("mystmines.souls.awaken")) { tell(sender, "<red>No."); return true; }
                if (souls.awaken(p, held)) p.getInventory().setItemInMainHand(held);
            }
            case "reload" -> {
                if (!p.hasPermission("mystmines.souls.admin")) { tell(sender, "<red>No."); return true; }
                souls.reload();
                tell(sender, "<green>souls.yml reloaded.");
            }
            case "give" -> {
                if (!p.hasPermission("mystmines.souls.admin")) { tell(sender, "<red>No."); return true; }
                if (args.length < 2) { tell(sender, "<red>/pick give <xp>"); return true; }
                try {
                    souls.onBreak(p, held, Double.parseDouble(args[1]));
                    p.getInventory().setItemInMainHand(held);
                    tell(sender, "<green>Done.");
                } catch (NumberFormatException e) { tell(sender, "<red>That's not a number."); }
            }
            default -> tell(sender, "<gray>/pick <dark_gray>| <gray>/pick awaken");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1)
            for (String s : List.of("info", "awaken"))
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
        return out;
    }
}
