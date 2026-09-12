package com.mcxyd.purearena.board;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.message.Texts;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import com.mcxyd.purearena.service.CpsService;
import com.mcxyd.purearena.service.StatsService;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 右侧计分板：每个玩家一块独立 Scoreboard，
 * 由该玩家的实体调度器周期刷新，全部读写都发生在玩家所有者上下文。
 * 统计数据来自线程安全的 StatsService。
 */
public final class ScoreboardService {

    private static final String OBJECTIVE_NAME = "purearena";
    // 每行使用唯一的隐形条目（§0§r、§1§r…），内容通过 Team 前缀展示，避免闪烁
    private static final int MAX_LINES = 15;

    private final ConfigManager configManager;
    private final StatsService stats;
    private final CpsService cpsService;
    private final TaskScheduler scheduler;

    // 任务句柄与玩家计分板缓存：玩家退出时移除，大小以在线人数为上界
    private final Map<UUID, ScheduledTask> updateTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public ScoreboardService(ConfigManager configManager, StatsService stats,
                             CpsService cpsService, TaskScheduler scheduler) {
        this.configManager = configManager;
        this.stats = stats;
        this.cpsService = cpsService;
        this.scheduler = scheduler;
    }

    /** 玩家加入时启动刷新任务。必须在玩家所有者上下文调用。 */
    public void startFor(Player player) {
        stopFor(player.getUniqueId());
        long interval = configManager.current().scoreboardUpdateIntervalTicks();
        ScheduledTask task = scheduler.runAtEntityTimer(player, () -> update(player), 1L, interval);
        if (task != null) {
            updateTasks.put(player.getUniqueId(), task);
        }
    }

    /** 玩家退出时停止任务并清理缓存。 */
    public void stopFor(UUID playerId) {
        ScheduledTask task = updateTasks.remove(playerId);
        if (task != null) {
            scheduler.cancel(task);
        }
        boards.remove(playerId);
    }

    /** reload 后重启全部在线玩家的刷新任务，以应用新的刷新间隔。 */
    public void restartAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runAtEntity(player, () -> startFor(player));
        }
    }

    /** 插件关闭：取消全部任务并清空缓存，不再持有 Scoreboard 引用。 */
    public void shutdown() {
        for (ScheduledTask task : updateTasks.values()) {
            scheduler.cancel(task);
        }
        updateTasks.clear();
        boards.clear();
    }

    // 在玩家实体线程执行
    private void update(Player player) {
        if (!player.isOnline()) {
            stopFor(player.getUniqueId());
            return;
        }
        PluginConfig config = configManager.current();
        // 统计只属于当天：任意玩家的刷新任务发现跨天即触发一次清空
        if (config.scoreboardResetDaily()) {
            stats.rolloverIfNeeded(LocalDate.now());
        }
        if (!config.scoreboardEnabled()) {
            // 关闭时还原主计分板，避免残留
            if (boards.remove(player.getUniqueId()) != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                    Texts.parse(config.scoreboardTitle()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(Texts.parse(config.scoreboardTitle()));
        }
        // 隐藏右侧红色分数数字（Paper 1.20.4+ NumberFormat API），reload 可切换
        objective.numberFormat(config.scoreboardHideNumbers() ? NumberFormat.blank() : null);

        String date = LocalDate.now().format(config.scoreboardDateFormat());
        StatsService.KillEntry mvp = stats.mvp().orElse(null);
        UUID playerId = player.getUniqueId();
        ScoreboardLineRenderer.LineContext context = new ScoreboardLineRenderer.LineContext(
                date,
                mvp == null ? config.scoreboardNoMvpName() : mvp.name(),
                stats.totalKills(),
                stats.remainingPlayers(Bukkit.getOnlinePlayers().size()),
                // 计分板的“击杀”显示今日全服累计击杀，与 MVP 数量保持一致。
                stats.totalKills(),
                cpsService.cps(playerId, System.currentTimeMillis()),
                cpsService.maxCps(playerId));

        List<String> lines = config.scoreboardLines();
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            String entry = entryFor(i);
            Team team = board.getTeam("pa_line_" + i);
            if (team == null) {
                team = board.registerNewTeam("pa_line_" + i);
                team.addEntry(entry);
            }
            String rendered = ScoreboardLineRenderer.render(lines.get(i), context);
            team.prefix(Texts.parse(rendered));
            objective.getScore(entry).setScore(count - i);
        }
        // reload 后行数减少时清掉多余的行
        for (int i = count; i < MAX_LINES; i++) {
            board.resetScores(entryFor(i));
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private String entryFor(int index) {
        return "§" + Integer.toHexString(index) + "§r";
    }
}
