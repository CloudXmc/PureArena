package com.mcxyd.purearena.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.item.LegacyArenaSwordCleaner;
import com.mcxyd.purearena.service.ArenaSpawnService;
import com.mcxyd.purearena.service.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 复活处理：复活完成后把玩家送到竞技场出生点、补发返回按钮并解除淘汰标记。
 * 使用 Paper 的 PlayerPostRespawnEvent（复活已完成，事件在玩家所有者上下文触发），
 * 不使用本项目 Folia 规范禁用的 PlayerRespawnEvent。
 */
public final class RespawnListener implements Listener {

    private final ArenaSpawnService arenaSpawnService;
    private final BackButtonManager backButtonManager;
    private final StatsService stats;
    private final LegacyArenaSwordCleaner legacyArenaSwordCleaner;

    public RespawnListener(ArenaSpawnService arenaSpawnService, BackButtonManager backButtonManager,
                           StatsService stats, LegacyArenaSwordCleaner legacyArenaSwordCleaner) {
        this.arenaSpawnService = arenaSpawnService;
        this.backButtonManager = backButtonManager;
        this.stats = stats;
        this.legacyArenaSwordCleaner = legacyArenaSwordCleaner;
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        // 复活即重新参战，剩余玩家数恢复
        stats.clearSession(player.getUniqueId());
        legacyArenaSwordCleaner.removeFrom(player);
        // 死亡掉落会清空按钮，复活后补发
        backButtonManager.give(player);
        // 复活重新进入竞技场时回到快捷栏第一格。
        player.getInventory().setHeldItemSlot(0);
        arenaSpawnService.teleportToSpawn(player);
    }
}
