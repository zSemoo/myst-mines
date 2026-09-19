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
            tell(sender, "<gold>/mine party|vein <mine> [block] [seconds]");
            tell(sender, "<gold>/mine rush|double|meteor <mine> [seconds]");
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
                if (args[1].equalsIgnoreCase("all")) {
                    var names = events.runningMines();
                    events.stopAll();
                    tell(sender, names.isEmpty() ? "<gray>Nothing was running." : "<green>Stopped " + names.size() + ": " + String.join(", ", names));
                    return true;
                }
                if (events.stop(args[1])) { tell(sender, "<green>Stopped and restored."); return true; }
                var names = events.runningMines();
                tell(sender, names.isEmpty()
                        ? "<gray>No events are running anywhere."
                        : "<gray>Nothing running in <white>" + args[1] + "<gray>. Running: <white>" + String.join(", ", names));
            }
            case "restore" -> {
                if (!sender.hasPermission("mystmines.events")) { tell(sender, "<red>No."); return true; }
                if (args.length < 2) { tell(sender, "<red>/mine restore <mine> <gray>— put it back from its pre-event backup"); return true; }
                tell(sender, events.restoreFromBackup(args[1].toLowerCase(Locale.ROOT))
                        ? "<green>Restored from the pre-event backup."
                        : "<red>No backup for " + args[1] + ". <gray>Nothing was saved aside for it.");
            }
            case "debug", "diag" -> {
                if (!sender.hasPermission("mystmines.events")) { tell(sender, "<red>No."); return true; }
                var mines = plugin.getMineManager().getMines();
                tell(sender, "<gold>MystMines diagnostics");
                tell(sender, "<gray>Mines loaded: <white>" + mines.size());
                for (var m : mines) {
                    var c = m.getController();
                    tell(sender, "<dark_gray>• <white>" + m.getName()
                            + " <gray>" + c.getResetMode() + " <dark_gray>delay " + c.getResetDelay()
                            + ", next in " + c.getCountdown() + "s"
                            + (m.getFlags().isStopped() ? " <red>disabled" : "")
                            + (events.running(m.getName()) != null ? " <gold>event" : ""));
                }
                if (sender instanceof Player p) {
                    var here = plugin.getMineManager().getMineAtLocation(p.getLocation()).orElse(null);
                    tell(sender, "<gray>You're standing in: <white>" + (here == null ? "no mine" : here.getName()));
                    var prof = plugin.getMineLevels().profile(p);
                    tell(sender, "<gray>Your level: <white>" + prof.level + " <dark_gray>" + (long) prof.xp
                            + " xp, " + prof.blocks + " blocks counted");
                    tell(sender, "<gray>Levelling enabled: <white>" + plugin.getMineLevels().enabled());
                }
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
                String block = null;
                if (args.length > 2) {
                    try { seconds = Integer.parseInt(args[2]); }
                    catch (NumberFormatException e) {
                        // not a number: treat it as the block for a party or vein
                        if (kind == MineEvents.Kind.PARTY || kind == MineEvents.Kind.GOLDEN_VEIN) block = args[2];
                        else { tell(sender, "<red>'" + args[2] + "' isn't a number of seconds."); return true; }
                    }
                }
                if (args.length > 3) try { seconds = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) { }
                events.start(kind, mine, seconds, block, msg -> tell(sender, msg));
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
            for (String s : List.of("party", "vein", "rush", "double", "meteor", "stop", "restore", "status", "challenges", "debug", "reload"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("status") && !args[0].equalsIgnoreCase("challenges")) {
            plugin.getMineManager().getMines().forEach(m -> {
                if (m.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(m.getName());
            });
        }
        return out;
    }
}
