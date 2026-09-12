package com.mcxyd.purearena.service;

import com.mcxyd.purearena.scheduler.TaskScheduler;
import com.mcxyd.purearena.storage.SqliteDailyKillRepository;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 每日击杀统计持久化服务：数据库 IO 全部异步执行，游戏线程只提交不可变击杀快照。
 */
public final class StatsPersistenceService {

    private record KillWrite(LocalDate date, UUID playerId, String playerName) {
    }

    private final Plugin plugin;
    private final StatsService stats;
    private final TaskScheduler scheduler;
    private final ConcurrentLinkedDeque<KillWrite> pendingWrites = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final Object flushLock = new Object();

    private volatile SqliteDailyKillRepository repository;
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile ScheduledTask flushTask;

    public StatsPersistenceService(Plugin plugin, StatsService stats, TaskScheduler scheduler) {
        this.plugin = plugin;
        this.stats = stats;
        this.scheduler = scheduler;
    }

    public void start() {
        scheduler.runAsync(() -> initialize());
    }

    /** 在击杀事件线程提交不可变数据，不读取 Player 或其他 Bukkit 实时对象。 */
    public void recordKill(UUID playerId, String playerName) {
        if (closed) {
            return;
        }
        pendingWrites.add(new KillWrite(LocalDate.now(), playerId, playerName));
        if (ready) {
            scheduleFlush();
        }
    }

    private void initialize() {
        if (closed) {
            return;
        }
        SqliteDailyKillRepository loadedRepository = new SqliteDailyKillRepository(
                Path.of(plugin.getDataFolder().toString(), "stats.db"));
        try {
            loadedRepository.initialize();
            stats.mergePersistedToday(loadedRepository.load(LocalDate.now()));
            synchronized (flushLock) {
                if (closed) {
                    loadedRepository.close();
                    return;
                }
                repository = loadedRepository;
                ready = true;
            }
            plugin.getLogger().info("SQLite 每日击杀统计已启用：stats.db");
            scheduleFlush();
        } catch (Exception exception) {
            loadedRepository.close();
            plugin.getLogger().log(Level.WARNING, "SQLite 击杀统计初始化失败，将继续使用内存统计。", exception);
            pendingWrites.clear();
        }
    }

    private void scheduleFlush() {
        if (closed || !ready || pendingWrites.isEmpty()
                || !flushScheduled.compareAndSet(false, true)) {
            return;
        }
        ScheduledTask task = scheduler.runAsync(this::flushQueue);
        flushTask = task;
        if (task == null) {
            flushScheduled.set(false);
        }
    }

    private void flushQueue() {
        synchronized (flushLock) {
            SqliteDailyKillRepository current = repository;
            if (current == null) {
                flushScheduled.set(false);
                return;
            }
            KillWrite write = null;
            try {
                while ((write = pendingWrites.pollFirst()) != null) {
                    current.increment(write.date(), write.playerId(), write.playerName());
                }
            } catch (Exception exception) {
                if (write != null) {
                    pendingWrites.addFirst(write);
                }
                // 暂停自动重试，避免数据库不可用时形成异步任务和日志风暴；数据保留到关闭阶段再尝试落盘。
                ready = false;
                plugin.getLogger().log(Level.WARNING, "SQLite 击杀统计写入失败，未写入数据将保留在内存队列。", exception);
            } finally {
                flushTask = null;
                flushScheduled.set(false);
            }
        }
        if (!closed && !pendingWrites.isEmpty()) {
            scheduleFlush();
        }
    }

    /** 插件关闭阶段停止接收新数据，完成最后一次队列落盘并关闭连接。 */
    public void shutdown() {
        closed = true;
        ScheduledTask task = flushTask;
        if (task != null) {
            scheduler.cancel(task);
        }
        synchronized (flushLock) {
            flushQueue();
            SqliteDailyKillRepository current = repository;
            if (current != null) {
                current.close();
                repository = null;
            }
            ready = false;
        }
    }
}
