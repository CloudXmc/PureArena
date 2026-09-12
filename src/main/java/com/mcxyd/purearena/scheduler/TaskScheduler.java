package com.mcxyd.purearena.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 统一调度器。
 * 全部走 Paper 官方实体/区域/全局/异步调度 API，
 * 这些 API 在目标 Paper 1.21.11 非 Folia 与 Folia 上均可用，因此不需要两套线程模型。
 * 实体/区域延迟不允许小于 1 tick，小于 1 会被强制修正。
 */
public final class TaskScheduler {

    private final Plugin plugin;
    // 跨线程注册/移除任务句柄，必须使用并发集合
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();
    private volatile boolean shutdown;

    public TaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    private static long atLeastOne(long ticks) {
        return Math.max(1L, ticks);
    }

    private Consumer<ScheduledTask> wrap(Runnable runnable) {
        return task -> {
            try {
                runnable.run();
            } finally {
                tasks.remove(task);
            }
        };
    }

    private ScheduledTask track(ScheduledTask task) {
        if (task != null) {
            if (shutdown) {
                task.cancel();
            } else {
                tasks.add(task);
                // runNow 可能在 track 返回前已完成；加入后再次检查，避免完成任务永久残留。
                if (task.isCancelled()
                        || task.getExecutionState() == ScheduledTask.ExecutionState.FINISHED) {
                    tasks.remove(task);
                }
            }
        }
        return task;
    }

    private Runnable retiredCallback(AtomicReference<ScheduledTask> taskReference,
                                     AtomicBoolean retiredBeforeRegistration) {
        return () -> {
            ScheduledTask task = taskReference.get();
            if (task == null) {
                retiredBeforeRegistration.set(true);
            } else {
                tasks.remove(task);
            }
        };
    }

    private ScheduledTask trackEntityTask(ScheduledTask task, AtomicReference<ScheduledTask> taskReference,
                                          AtomicBoolean retiredBeforeRegistration) {
        taskReference.set(task);
        track(task);
        if (task != null && retiredBeforeRegistration.get()) {
            tasks.remove(task);
        }
        return task;
    }

    /** 实体任务：若实体已失效则任务不执行。 */
    public ScheduledTask runAtEntity(Entity entity, Runnable runnable) {
        AtomicReference<ScheduledTask> taskReference = new AtomicReference<>();
        AtomicBoolean retiredBeforeRegistration = new AtomicBoolean();
        ScheduledTask task = entity.getScheduler().run(plugin, wrap(runnable),
                retiredCallback(taskReference, retiredBeforeRegistration));
        return trackEntityTask(task, taskReference, retiredBeforeRegistration);
    }

    /** 实体延迟任务，延迟至少 1 tick。 */
    public ScheduledTask runAtEntityLater(Entity entity, Runnable runnable, long delayTicks) {
        AtomicReference<ScheduledTask> taskReference = new AtomicReference<>();
        AtomicBoolean retiredBeforeRegistration = new AtomicBoolean();
        ScheduledTask task = entity.getScheduler().runDelayed(plugin, wrap(runnable),
                retiredCallback(taskReference, retiredBeforeRegistration), atLeastOne(delayTicks));
        return trackEntityTask(task, taskReference, retiredBeforeRegistration);
    }

    /** 实体重复任务，延迟与周期至少 1 tick。重复任务不会自动移除，需调用方取消。 */
    public ScheduledTask runAtEntityTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        AtomicReference<ScheduledTask> taskReference = new AtomicReference<>();
        AtomicBoolean retiredBeforeRegistration = new AtomicBoolean();
        ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, ignored -> runnable.run(),
                retiredCallback(taskReference, retiredBeforeRegistration),
                atLeastOne(delayTicks), atLeastOne(periodTicks));
        return trackEntityTask(task, taskReference, retiredBeforeRegistration);
    }

    /** 区域任务。 */
    public ScheduledTask runAtRegion(Location location, Runnable runnable) {
        return track(Bukkit.getRegionScheduler().run(plugin, location, wrap(runnable)));
    }

    /** 区域延迟任务，延迟至少 1 tick。 */
    public ScheduledTask runAtRegionLater(Location location, Runnable runnable, long delayTicks) {
        return track(Bukkit.getRegionScheduler().runDelayed(plugin, location, wrap(runnable), atLeastOne(delayTicks)));
    }

    /** 全局任务。 */
    public ScheduledTask runGlobal(Runnable runnable) {
        return track(Bukkit.getGlobalRegionScheduler().run(plugin, wrap(runnable)));
    }

    /** 全局延迟任务。 */
    public ScheduledTask runGlobalLater(Runnable runnable, long delayTicks) {
        return track(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, wrap(runnable), atLeastOne(delayTicks)));
    }

    /** 全局重复任务。 */
    public ScheduledTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return track(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(),
                atLeastOne(delayTicks), atLeastOne(periodTicks)));
    }

    /** 异步任务：用于文件、网络等阻塞操作，不得访问实时 Bukkit 对象。 */
    public ScheduledTask runAsync(Runnable runnable) {
        return track(Bukkit.getAsyncScheduler().runNow(plugin, wrap(runnable)));
    }

    /** 异步延迟任务。 */
    public ScheduledTask runAsyncLater(Runnable runnable, long delayTicks) {
        return track(Bukkit.getAsyncScheduler().runDelayed(plugin, wrap(runnable),
                atLeastOne(delayTicks) * 50L, TimeUnit.MILLISECONDS));
    }

    /** 取消单个任务。 */
    public void cancel(ScheduledTask task) {
        if (task != null) {
            task.cancel();
            tasks.remove(task);
        }
    }

    /** 插件关闭时取消全部未完成任务。 */
    public void cancelAll() {
        shutdown = true;
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}
