package com.mcxyd.purearena.service;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 竞技场出生点传送：加入与复活后把玩家送到配置的坐标。
 * 统一走 teleportAsync，失败时给玩家可配置中文提示，不静默失败。
 * 必须在玩家实体所有者上下文调用。
 */
public final class ArenaSpawnService {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public ArenaSpawnService(Plugin plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    /** 出生点未启用或世界不存在时不做任何事。 */
    public void teleportToSpawn(Player player) {
        PluginConfig.ArenaSpawn spawn = configManager.current().arenaSpawn();
        if (!spawn.enabled()) {
            return;
        }
        World world = Bukkit.getWorld(spawn.world());
        if (world == null) {
            plugin.getLogger().warning("竞技场出生点世界不存在：" + spawn.world() + "，本次传送已跳过。");
            return;
        }
        Location target = new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        player.teleportAsync(target).whenComplete((success, throwable) -> {
            // Paper 的消息发送线程安全，回调里只发提示、不触碰实时对象
            if (throwable != null || !Boolean.TRUE.equals(success)) {
                messageManager.send(player, "arena.spawn-failed");
                if (throwable != null) {
                    plugin.getLogger().warning("竞技场出生点传送失败：" + throwable.getMessage());
                }
            }
        });
    }
}
