package com.mcxyd.purearena;

import com.mcxyd.purearena.board.ScoreboardService;
import com.mcxyd.purearena.command.PureArenaCommand;
import com.mcxyd.purearena.command.PureArenaTabCompleter;
import com.mcxyd.purearena.command.sub.HelpSubCommand;
import com.mcxyd.purearena.command.sub.ReloadSubCommand;
import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.item.LegacyArenaSwordCleaner;
import com.mcxyd.purearena.listener.BackButtonListener;
import com.mcxyd.purearena.listener.AntiCheatListener;
import com.mcxyd.purearena.listener.CombatListener;
import com.mcxyd.purearena.listener.CpsListener;
import com.mcxyd.purearena.listener.DeathListener;
import com.mcxyd.purearena.listener.PlayerConnectionListener;
import com.mcxyd.purearena.listener.RespawnListener;
import com.mcxyd.purearena.listener.VoidDeathListener;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import com.mcxyd.purearena.service.ArenaSpawnService;
import com.mcxyd.purearena.service.CpsService;
import com.mcxyd.purearena.service.KnockbackService;
import com.mcxyd.purearena.service.LobbyConnectService;
import com.mcxyd.purearena.service.StatsService;
import com.mcxyd.purearena.service.StatsPersistenceService;
import com.mcxyd.purearena.util.FoliaDetector;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * PureArena 纯净竞技场主类。
 * 只负责生命周期、模块装配与关闭顺序。
 */
public final class PureArenaPlugin extends JavaPlugin {

    private TaskScheduler scheduler;
    private ScoreboardService scoreboardService;
    private CpsListener cpsListener;
    private AntiCheatListener antiCheatListener;
    private CombatListener combatListener;
    private DeathListener deathListener;
    private StatsPersistenceService statsPersistenceService;

    @Override
    public void onEnable() {
        // Folia 检测只在启动时执行一次，之后使用缓存结果
        getLogger().info(FoliaDetector.isFolia()
                ? "检测到 Folia 环境，使用区域化调度。"
                : "检测到 Paper/Bukkit 环境。");

        this.scheduler = new TaskScheduler(this);

        ConfigManager configManager = new ConfigManager(this);
        MessageManager messageManager = new MessageManager(this);
        try {
            for (String warning : configManager.load()) {
                getLogger().warning("配置修正：" + warning);
            }
            messageManager.load();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "配置加载失败，插件无法启动。", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        StatsService stats = new StatsService();
        this.statsPersistenceService = new StatsPersistenceService(this, stats, scheduler);
        statsPersistenceService.start();
        CpsService cpsService = new CpsService();
        LobbyConnectService lobbyConnectService = new LobbyConnectService(this, messageManager);
        KnockbackService knockbackService = new KnockbackService(configManager, scheduler);
        BackButtonManager backButtonManager = new BackButtonManager(this, configManager);
        LegacyArenaSwordCleaner legacyArenaSwordCleaner = new LegacyArenaSwordCleaner(this);
        ArenaSpawnService arenaSpawnService = new ArenaSpawnService(this, configManager, messageManager);
        this.scoreboardService = new ScoreboardService(configManager, stats, cpsService, scheduler);
        this.cpsListener = new CpsListener(configManager, messageManager, cpsService, scheduler);
        this.antiCheatListener = new AntiCheatListener(configManager, messageManager, scheduler);

        // 跨服依赖 BungeeCord/Velocity 插件消息通道
        getServer().getMessenger().registerOutgoingPluginChannel(this, LobbyConnectService.CHANNEL);

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(stats, backButtonManager, scoreboardService,
                        lobbyConnectService, arenaSpawnService, cpsService, cpsListener,
                        legacyArenaSwordCleaner), this);
        this.combatListener = new CombatListener(knockbackService);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(antiCheatListener, this);
        getServer().getPluginManager().registerEvents(new VoidDeathListener(), this);
        getServer().getPluginManager().registerEvents(
                cpsListener, this);
        this.deathListener = new DeathListener(configManager, stats, backButtonManager, scheduler,
                messageManager, legacyArenaSwordCleaner, statsPersistenceService);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(
                new RespawnListener(arenaSpawnService, backButtonManager, stats, legacyArenaSwordCleaner), this);
        getServer().getPluginManager().registerEvents(
                new BackButtonListener(configManager, backButtonManager, lobbyConnectService), this);

        PureArenaCommand command = new PureArenaCommand(messageManager,
                new HelpSubCommand(messageManager),
                new ReloadSubCommand(this, configManager, messageManager, scheduler,
                        backButtonManager, scoreboardService));
        PluginCommand pluginCommand = getCommand("purearena");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(new PureArenaTabCompleter(command));
        }

        // 热重载插件时可能已有在线玩家：回到各自实体上下文补发按钮和计分板
        for (Player player : getServer().getOnlinePlayers()) {
            scheduler.runAtEntity(player, () -> {
                legacyArenaSwordCleaner.removeFrom(player);
                backButtonManager.give(player);
                scoreboardService.startFor(player);
            });
        }

        getLogger().info("PureArena 已启用。");
    }

    @Override
    public void onDisable() {
        if (scoreboardService != null) {
            scoreboardService.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (cpsListener != null) {
            cpsListener.shutdown();
        }
        if (antiCheatListener != null) {
            antiCheatListener.shutdown();
        }
        if (combatListener != null) {
            combatListener.shutdown();
        }
        if (deathListener != null) {
            deathListener.shutdown();
        }
        if (statsPersistenceService != null) {
            statsPersistenceService.shutdown();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        HandlerList.unregisterAll(this);
        getLogger().info("PureArena 已关闭。");
    }
}
