package com.mcxyd.purearena.command;

import org.bukkit.command.CommandSender;

/**
 * 子指令契约：指令层只做验证与转交，不承载业务。
 */
public interface SubCommand {

    /** 子指令名称（小写）。 */
    String name();

    /** 所需权限节点。 */
    String permission();

    void execute(CommandSender sender, String[] args);
}
