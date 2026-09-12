package com.mcxyd.purearena.command.sub;

import com.mcxyd.purearena.command.SubCommand;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.message.Texts;
import org.bukkit.command.CommandSender;

/**
 * /purearena help：按发送者权限展示帮助，内容全部来自 messages.yml。
 */
public final class HelpSubCommand implements SubCommand {

    private final MessageManager messageManager;

    public HelpSubCommand(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String permission() {
        return "purearena.use";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        messageManager.sendRaw(sender, "help.header");
        for (String line : messageManager.stringList("help.player")) {
            sender.sendMessage(Texts.parse(line));
        }
        if (sender.hasPermission("purearena.admin")) {
            for (String line : messageManager.stringList("help.admin")) {
                sender.sendMessage(Texts.parse(line));
            }
        }
        messageManager.sendRaw(sender, "help.footer");
    }
}
