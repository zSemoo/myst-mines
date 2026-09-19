package me.catalysmrl.catamines.myst.event;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /mine — mine events, and a few things about mines that aren't events.
 *
 *   /mine party|vein|rush|double|meteor <mine> [seconds]
 *   /mine stop <mine>          end an event early
 *   /mine status               what's running
 *   /mine challenges           this week's, and how you're doing
 *   /mine reload
 *
 * `/mine event <...>` is accepted as well, because that's what people type.
 */
public class MineEventCommand implements TabExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CataMines plugin;

    public MineEventCommand(CataMines plugin) { this.plugin = plugin; }

    private void tell(CommandSender to, String msg) { to.sendMessage(MM.deserialize(msg)); }

    private MineEvents.Kind kindOf(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "party" -> MineEvents.Kind.PARTY;
            case "vein", "goldenvein", "golden" -> MineEvents.Kind.GOLDEN_VEIN;
            case "rush" -> MineEvents.Kind.RUSH;
            case "double", "doubledrop", "drops" -> MineEvents.Kind.DOUBLE_DROP;
            case "meteor" -> MineEvents.Kind.METEOR;
            default -> null;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // "/mine event stop king" and "/mine stop king" should both work.
        if (args.length > 0 && args[0].equalsIgnoreCase("event")) args = Arrays.copyOfRange(args, 1, args.length);

        if (args.length == 0) {
            tell(sender, "<gold>/mine party|vein|rush|double|meteor <mine> [seconds]");
            tell(sender, "<gold>/mine stop <mine> <dark_gray>| <gold>status <dark_gray>| <gold>challenges");
            return true;
        }

        MineEvents events = plugin.getMineEvents();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (events.all().isEmpty()) { tell(sender, "<gray>Nothing running."); return true; }
                for (MineEvents.Active a : events.all())
                    tell(sender, "<gray>• <white>" + a.mine + " <dark_gray>" + MineEvents.pretty(a.kind)
                            + " <gray>" + Math.max(0, (a.endsAt - System.currentTimeMillis()) / 1000) + "s left");
            }
            case "challenges", "challenge", "weekly" -> {
                if (!(sender instanceof Player p)) { tell(sender, "<red>Players only."); return true; }
                var all = plugin.getMineChallenges().allThisWeek();
                if (all.isEmpty()) { tell(sender, "<gray>No challenges this week."); return true; }
                tell(sender, "<gradient:#8cff9e:#1fbf5a>This week's challenges</gradient>");
                for (var c : all) {
                    boolean done = plugin.getMineChallenges().claimed(p, c);
                    int prog = plugin.getMineChallenges().progress(p, c);
                    tell(sender, "<gray>• <white>" + c.name() + " <dark_gray>"
                            + (done ? "<green>done" : prog + "/" + c.amount()));
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("mystmines.events")) { tell(sender, "<red>No."); return true; }
                if (args.length < 2) { tell(sender, "<red>/mine stop <mine>"); return true; }
                if (events.running(args[1]) == null) { tell(sender, "<gray>Nothing running in " + args[1] + "."); return true; }
                events.stop(args[1]);
                tell(sender, "<green>Stopped.");
            }
            case "reload" -> {
                if (!sender.hasPermission("mystmines.events")) { tell(sender, "<red>No."); return true; }
                events.reload();
                plugin.getMineChallenges().reload();
                plugin.getContributions().reload();
                tell(sender, "<green>Reloaded events, challenges and digging configs.");
            }
            default -> {
                if (!sender.hasPermission("mystmines.events")) { tell(sender, "<red>No."); return true; }
                MineEvents.Kind kind = kindOf(args[0]);
                if (kind == null) { tell(sender, "<red>party, vein, rush, double, meteor, stop, status, challenges"); return true; }
                if (args.length < 2) { tell(sender, "<red>/mine " + args[0] + " <mine> [seconds]"); return true; }
                CataMine mine = plugin.getMineManager().getMine(args[1]).orElse(null);
                if (mine == null) { tell(sender, "<red>No mine called " + args[1] + "."); return true; }
                int seconds = 0;
                if (args.length > 2) try { seconds = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
                events.start(kind, mine, seconds, msg -> tell(sender, msg));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] rawArgs) {
        // A separate final variable, because the lambda below reads it.
        final String[] args = rawArgs.length > 0 && rawArgs[0].equalsIgnoreCase("event")
                ? Arrays.copyOfRange(rawArgs, 1, rawArgs.length) : rawArgs;
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("party", "vein", "rush", "double", "meteor", "stop", "status", "challenges", "reload"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("status") && !args[0].equalsIgnoreCase("challenges")) {
            plugin.getMineManager().getMines().forEach(m -> {
                if (m.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(m.getName());
            });
        }
        return out;
    }
}
