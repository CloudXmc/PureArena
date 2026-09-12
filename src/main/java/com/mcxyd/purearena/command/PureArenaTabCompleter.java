package com.mcxyd.purearena.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 主指令 Tab 补全：只联想发送者有权限的子指令。
 */
public final class PureArenaTabCompleter implements TabCompleter {

    private final PureArenaCommand command;

    public PureArenaTabCompleter(PureArenaCommand command) {
        this.command = command;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (SubCommand sub : command.subCommands().values()) {
            if (sub.name().startsWith(prefix) && sender.hasPermission(sub.permission())) {
                suggestions.add(sub.name());
            }
        }
        return suggestions;
    }
}
