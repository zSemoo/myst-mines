package me.catalysmrl.catamines.myst.level;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.myst.gui.LevelGui;
import me.catalysmrl.catamines.myst.gui.LevelTopGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /level — your mining career.
 *
 * Bare, it opens the GUI. `/level top` opens the leaderboard, and staff can
 * set or inspect someone else's.
 */
public class LevelCommand implements TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;

    public LevelCommand(CataMines plugin) { this.plugin = plugin; }

    private void tell(CommandSender to, String msg) { to.sendMessage(MM.deserialize(msg)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MineLevels levels = plugin.getMineLevels();

        if (args.length == 0) {
            if (!(sender instanceof Player p)) { tell(sender, "<red>Players only. Try <white>/level top</white>."); return true; }
            new LevelGui(plugin, p.getUniqueId()).open(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "top", "leaderboard", "lb" -> {
                if (sender instanceof Player p) { new LevelTopGui(plugin).open(p); return true; }
                // console gets it as text
                int place = 1;
                for (MineLevels.Profile prof : levels.top(10))
                    tell(sender, "<gray>#" + place++ + " <white>" + prof.name + " <dark_gray>level " + prof.level);
            }
            case "check", "info" -> {
                if (args.length < 2) { tell(sender, "<red>/level check <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "<red>" + args[1] + " isn't online."); return true; }
                MineLevels.Profile prof = levels.profile(target);
                tell(sender, "<gray>" + target.getName() + ": <white>level " + prof.level
                        + " <dark_gray>(" + (long) prof.xp + "/" + levels.xpForNext(prof.level) + " xp, "
                        + prof.blocks + " blocks, #" + levels.placeOf(prof.id) + ")");
            }
            case "set" -> {
                if (!sender.hasPermission("mystmines.level.admin")) { tell(sender, "<red>No."); return true; }
                if (args.length < 3) { tell(sender, "<red>/level set <player> <level>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "<red>" + args[1] + " isn't online."); return true; }
                try {
                    levels.setLevel(target, Integer.parseInt(args[2]));
                    tell(sender, "<green>" + target.getName() + " is now level " + args[2] + ".");
                } catch (NumberFormatException e) { tell(sender, "<red>That's not a number."); }
            }
            case "give" -> {
                if (!sender.hasPermission("mystmines.level.admin")) { tell(sender, "<red>No."); return true; }
                if (args.length < 3) { tell(sender, "<red>/level give <player> <xp>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "<red>" + args[1] + " isn't online."); return true; }
                try {
                    levels.give(target, Double.parseDouble(args[2]), false);
                    tell(sender, "<green>Gave " + target.getName() + " " + args[2] + " mining xp.");
                } catch (NumberFormatException e) { tell(sender, "<red>That's not a number."); }
            }
            case "reset" -> {
                if (!sender.hasPermission("mystmines.level.admin")) { tell(sender, "<red>No."); return true; }
                if (args.length < 2) { tell(sender, "<red>/level reset <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "<red>" + args[1] + " isn't online."); return true; }
                levels.reset(target.getUniqueId());
                tell(sender, "<green>" + target.getName() + " is back to level 1.");
            }
            case "prestige" -> {
                if (!(sender instanceof Player p)) { tell(sender, "<red>Players only."); return true; }
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    MineLevels.Profile prof = levels.profile(p);
                    tell(sender, "<gray>Prestige resets you to level 1 for a permanent <white>" + levels.stars(prof)
                            + "★<gray> and a faster climb. <white>/level prestige confirm");
                    return true;
                }
                levels.prestige(p);
            }
            case "reload" -> {
                if (!sender.hasPermission("mystmines.level.admin")) { tell(sender, "<red>No."); return true; }
                levels.reload();
                plugin.getMineChallenges().reload();
                plugin.getLastBlock().reload();
                tell(sender, "<green>Reloaded levelling, challenges and digging configs.");
            }
            default -> tell(sender, "<gray>/level <dark_gray>| <gray>/level top"
                    + (sender.hasPermission("mystmines.level.admin")
                    ? " <dark_gray>| <gray>set, give, reset, check, reload" : ""));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("top", "prestige"));
            if (sender.hasPermission("mystmines.level.admin")) subs.addAll(List.of("set", "give", "reset", "check", "reload"));
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("top")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
