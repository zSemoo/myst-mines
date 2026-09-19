package me.catalysmrl.catamines.myst.event;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.api.mine.CataMine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /mine — the staff switches for mine events.
 *
 *   /mine party <mine> [seconds]
 *   /mine vein <mine> [seconds]
 *   /mine rush <mine> [seconds]
 *   /mine double <mine> [seconds]
 *   /mine meteor <mine> [seconds]
 *   /mine stop <mine>
 *   /mine status
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
        MineEvents events = plugin.getMineEvents();
        // /mine challenges is for everyone; the rest is staff
        boolean staff = sender.hasPermission("mystmines.events");
        if (args.length > 0 && !args[0].equalsIgnoreCase("challenge") && !args[0].equalsIgnoreCase("challenges") && !staff) {
            tell(sender, "<gray>/mine challenges <dark_gray>— this week's mine challenges");
            return true;
        }
        if (args.length == 0 && !staff) { tell(sender, "<gray>/mine challenges"); return true; }

        if (args.length == 0) {
            tell(sender, "<gold>/mine party|vein|rush|double|meteor <mine> [seconds]");
            tell(sender, "<gold>/mine stop <mine> <dark_gray>| <gold>/mine status <dark_gray>| <gold>/mine reload");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (events.all().isEmpty()) { tell(sender, "<gray>Nothing running."); return true; }
                for (MineEvents.Active a : events.all())
                    tell(sender, "<gray>• <white>" + a.mine + " <dark_gray>" + MineEvents.pretty(a.kind)
                            + " <gray>" + Math.max(0, (a.endsAt - System.currentTimeMillis()) / 1000) + "s left");
            }
            case "stop" -> {
                if (args.length < 2) { tell(sender, "<red>/mine stop <mine>"); return true; }
                events.stop(args[1]);
                tell(sender, "<green>Stopped.");
            }
            case "reload" -> {
                events.reload(); plugin.getBoards().reload(); plugin.getSouls().reload(); plugin.getExtras().reload();
                tell(sender, "<green>events, boards, souls and extras reloaded.");
            }
            case "board" -> {
                // /mine board <mine>       place the live top-three hologram where you stand
                // /mine board <mine> remove
                if (!(sender instanceof org.bukkit.entity.Player p)) return true;
                if (args.length < 2) { tell(sender, "<red>/mine board <mine> [remove]"); return true; }
                if (args.length > 2 && args[2].equalsIgnoreCase("remove")) {
                    tell(sender, plugin.getBoards().removeBoard(args[1]) ? "<green>Board removed." : "<gray>No board for that mine.");
                    return true;
                }
                CataMine mine = plugin.getMineManager().getMine(args[1]).orElse(null);
                if (mine == null) { tell(sender, "<red>No mine called " + args[1] + "."); return true; }
                plugin.getBoards().placeBoard(mine, p.getLocation().add(0, 1.5, 0));
                tell(sender, "<green>Board for " + mine.getName() + " placed here.");
            }
            case "challenge", "challenges" -> {
                // what's on this week, for anyone
                if (!(sender instanceof org.bukkit.entity.Player p)) return true;
                tell(sender, "<gradient:#7de2ff:#1fa9d6>This week's mine challenges</gradient>");
                for (CataMine m : plugin.getMineManager().getMines()) {
                    var c = plugin.getExtras().challengeFor(m.getName());
                    if (c == null) continue;
                    boolean fin = plugin.getExtras().finished(p, m.getName());
                    tell(sender, "<gray>• <white>" + m.getName() + "<gray>: " + c.name() + " <dark_gray>"
                            + (fin ? "<green>done" : plugin.getExtras().progressOf(p, m.getName()) + "/" + c.target()));
                }
            }
            default -> {
                MineEvents.Kind kind = kindOf(args[0]);
                if (kind == null) { tell(sender, "<red>party, vein, rush, double, meteor, stop, status"); return true; }
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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("party", "vein", "rush", "double", "meteor", "stop", "status", "reload"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("status")) {
            plugin.getMineManager().getMines().forEach(m -> {
                if (m.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(m.getName());
            });
        }
        return out;
    }
}
