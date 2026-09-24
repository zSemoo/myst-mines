package me.catalysmrl.catamines.commands.generic;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.command.abstraction.AbstractCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.message.Messages;

import org.bukkit.command.CommandSender;

public class ReloadCommand extends AbstractCommand {
    public ReloadCommand() {
        super("reload", "catamines.reload", Predicates.any(), false);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx) throws CommandException {
        assertArgLength(ctx);
        // Everything the fork added reads its own file, so each gets a nudge
        // alongside the mines themselves.
        int loaded = plugin.getMineManager().reload();
        plugin.reloadConfig();
        plugin.getMineLevels().reload();
        plugin.getMineEvents().reload();
        plugin.getLastBlock().reload();
        plugin.getContributions().reload();
        plugin.getMineChallenges().reload();

        if (loaded < 0) {
            Messages.sendPrefixed(sender, "&cThe mines folder couldn't be read — check the console.");
            return;
        }
        Messages.sendPrefixed(sender, "&aReloaded &f" + loaded + "&a mine(s) and every config.");
    }

    @Override
    public Message getDescription() {
        return Message.RELOAD_DESCRIPTION;
    }

    @Override
    public Message getUsage() {
        return Message.RELOAD_USAGE;
    }
}
