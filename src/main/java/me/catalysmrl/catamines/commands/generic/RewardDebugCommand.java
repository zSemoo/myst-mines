package me.catalysmrl.catamines.commands.generic;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.command.abstraction.AbstractCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Message;
import me.catalysmrl.catamines.utils.message.Messages;
import me.catalysmrl.catamines.api.rewards.RewardActionLine;
import org.bukkit.command.CommandSender;

public class RewardDebugCommand extends AbstractCommand {
    public RewardDebugCommand() {
        super("testreward", "catamines.admin", Predicates.atLeast(1), false);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx) throws CommandException {
        String raw = String.join(" ", ctx.args());
        Messages.sendColorized(sender, "&eParsing: &f" + raw);

        try {
            RewardActionLine actionLine = plugin.getRewardParser().parseActionLine(raw);
            Messages.sendColorized(sender, "&aSuccessfully parsed!");
            Messages.sendColorized(sender, "&7Action: " + actionLine.getAction().getClass().getSimpleName());
            Messages.sendColorized(sender, "&7Targeter: " + actionLine.getTargeter().getTargeter().getClass().getSimpleName());
            Messages.sendColorized(sender, "&7Conditions: " + actionLine.getConditions().size());
        } catch (Exception e) {
            Messages.sendColorized(sender, "&cFailed: " + e.getMessage());
        }
    }

    @Override
    public Message getDescription() {
        return Message.UNKNOWN_COMMAND;
    }

    @Override
    public Message getUsage() {
        return Message.UNKNOWN_COMMAND;
    }
}
