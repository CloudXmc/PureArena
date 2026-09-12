package com.mcxyd.purearena.service;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义击退。
 * 在伤害事件线程（受害者所有者上下文）读取双方位置快照，
 * 1 tick 后在受害者实体线程写入速度，覆盖原版击退。
 */
public final class KnockbackService {

    // 原版近战击退的近似基准速度
    private static final double BASE_HORIZONTAL = 0.4;
    private static final double BASE_VERTICAL = 0.4;

    private final ConfigManager configManager;
    private final TaskScheduler scheduler;
    private final Map<UUID, ScheduledTask> pendingTasks = new ConcurrentHashMap<>();

    public KnockbackService(ConfigManager configManager, TaskScheduler scheduler) {
        this.configManager = configManager;
        this.scheduler = scheduler;
    }

    /**
     * 必须在受害者所有者上下文（伤害事件线程）调用。
     * attackerLocation 是事件时刻的快照，跨 tick 传递的只有坐标副本。
     */
    public void applyKnockback(Player victim, Location attackerLocation) {
        PluginConfig config = configManager.current();
        if (!config.knockbackEnabled()) {
            return;
        }

        Vector direction = victim.getLocation().toVector().subtract(attackerLocation.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            // 双方几乎重叠时沿攻击者水平朝向击退，避免零向量
            direction = attackerLocation.getDirection().setY(0);
            if (direction.lengthSquared() < 1.0E-6) {
                direction = new Vector(0, 0, 1);
            }
        }
        direction.normalize();

        double horizontal = BASE_HORIZONTAL * config.knockbackHorizontal();
        double vertical = Math.min(config.knockbackVerticalLimit(), BASE_VERTICAL * config.knockbackVertical());
        Vector velocity = new Vector(direction.getX() * horizontal, vertical, direction.getZ() * horizontal);

        // 同一玩家只保留最新一次击退任务，避免连续攻击积累延迟回调。
        UUID victimId = victim.getUniqueId();
        ScheduledTask previous = pendingTasks.remove(victimId);
        if (previous != null) {
            scheduler.cancel(previous);
        }
        ScheduledTask task = scheduler.runAtEntityLater(victim, () -> {
            pendingTasks.remove(victimId);
            if (victim.isOnline() && !victim.isDead()) {
                victim.setVelocity(velocity);
            }
        }, 1L);
        if (task != null) {
            pendingTasks.put(victimId, task);
        }
    }

    public void clearPlayer(UUID playerId) {
        ScheduledTask task = pendingTasks.remove(playerId);
        if (task != null) {
            scheduler.cancel(task);
        }
    }

    public void shutdown() {
        for (ScheduledTask task : pendingTasks.values()) {
            scheduler.cancel(task);
        }
        pendingTasks.clear();
    }
}
