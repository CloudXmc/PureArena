package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.board.ScoreboardService;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.item.LegacyArenaSwordCleaner;
import com.mcxyd.purearena.service.ArenaSpawnService;
import com.mcxyd.purearena.service.CpsService;
import com.mcxyd.purearena.service.LobbyConnectService;
import com.mcxyd.purearena.service.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 加入/退出：发放按钮、传送出生点、启动计分板、清理会话状态。
 * 当天击杀数据不在此清理，MVP 按天保留（见 StatsService）。
 * 两个事件都在玩家所有者上下文触发，可直接操作该玩家。
 */
public final class PlayerConnectionListener implements Listener {

    private final StatsService stats;
    private final BackButtonManager backButtonManager;
    private final ScoreboardService scoreboardService;
    private final LobbyConnectService lobbyConnectService;
    private final ArenaSpawnService arenaSpawnService;
    private final CpsService cpsService;
    private final CpsListener cpsListener;
    private final LegacyArenaSwordCleaner legacyArenaSwordCleaner;

    public PlayerConnectionListener(StatsService stats, BackButtonManager backButtonManager,
                                    ScoreboardService scoreboardService, LobbyConnectService lobbyConnectService,
                                    ArenaSpawnService arenaSpawnService, CpsService cpsService,
                                    CpsListener cpsListener, LegacyArenaSwordCleaner legacyArenaSwordCleaner) {
        this.stats = stats;
        this.backButtonManager = backButtonManager;
        this.scoreboardService = scoreboardService;
        this.lobbyConnectService = lobbyConnectService;
        this.arenaSpawnService = arenaSpawnService;
        this.cpsService = cpsService;
        this.cpsListener = cpsListener;
        this.legacyArenaSwordCleaner = legacyArenaSwordCleaner;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stats.clearSession(player.getUniqueId());
        legacyArenaSwordCleaner.removeFrom(player);
        backButtonManager.give(player);
        // 进入竞技场时统一从快捷栏第一格开始，避免沿用上次服务器的选中槽位。
        player.getInventory().setHeldItemSlot(0);
        scoreboardService.startFor(player);
        arenaSpawnService.teleportToSpawn(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        scoreboardService.stopFor(player.getUniqueId());
        stats.clearSession(player.getUniqueId());
        lobbyConnectService.clearPlayer(player.getUniqueId());
        cpsService.clearPlayer(player.getUniqueId());
        cpsListener.clearPlayer(player.getUniqueId());
    }
}
