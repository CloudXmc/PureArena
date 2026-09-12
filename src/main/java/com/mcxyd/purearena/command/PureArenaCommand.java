package com.mcxyd.purearena.command;

import com.mcxyd.purearena.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/**
 * 主指令分发器：解析子指令、统一权限判断，业务在各子指令类中。
 */
public final class PureArenaCommand implements CommandExecutor {

    private final MessageManager messageManager;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public PureArenaCommand(MessageManager messageManager, SubCommand... commands) {
        this.messageManager = messageManager;
        for (SubCommand command : commands) {
            subCommands.put(command.name(), command);
        }
    }

    public Map<String, SubCommand> subCommands() {
        return subCommands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subCommands.get(name);
        if (sub == null) {
            messageManager.send(sender, "command.unknown");
            return true;
        }
        if (!sender.hasPermission(sub.permission())) {
            messageManager.send(sender, "command.no-permission");
            return true;
        }
        sub.execute(sender, args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length));
        return true;
    }
}
