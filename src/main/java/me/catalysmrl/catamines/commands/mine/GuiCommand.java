package me.catalysmrl.catamines.commands.mine;

import me.catalysmrl.catamines.CataMines;
import me.catalysmrl.catamines.command.abstraction.AbstractCommand;
import me.catalysmrl.catamines.command.utils.CommandContext;
import me.catalysmrl.catamines.command.utils.CommandException;
import me.catalysmrl.catamines.utils.helper.Predicates;
import me.catalysmrl.catamines.utils.message.Messages;
import me.catalysmrl.catamines.utils.message.Message;
import org.bukkit.command.CommandSender;

public class GuiCommand extends AbstractCommand {

    public GuiCommand() {
        super("gui", "catamines.gui", Predicates.any(), true);
    }

    @Override
    public void execute(CataMines plugin, CommandSender sender, CommandContext ctx) throws CommandException {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.sendPrefixed(sender, "&cPlayers only.");
            return;
        }
        new me.catalysmrl.catamines.myst.gui.MineGui(plugin).open(player);
    }

    @Override
    public Message getDescription() {
        return Message.GUI_DESCRIPTION;
    }

    @Override
    public Message getUsage() {
        return Message.GUI_USAGE;
    }
}
