package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.item.LegacyArenaSwordCleaner;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import com.mcxyd.purearena.service.StatsService;
import com.mcxyd.purearena.service.StatsPersistenceService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡处理：记录击杀、移除按钮掉落、延迟自动复活。
 * 玩家死亡后留在竞技场，复活位置由 RespawnListener 传送到竞技场出生点。
 * 事件在死亡玩家所有者上下文触发。
 */
public final class DeathListener implements Listener {

    private final ConfigManager configManager;
    private final StatsService stats;
    private final BackButtonManager backButtonManager;
    private final TaskScheduler scheduler;
    private final MessageManager messageManager;
    private final LegacyArenaSwordCleaner legacyArenaSwordCleaner;
    private final StatsPersistenceService persistence;
    private final Map<UUID, ScheduledTask> respawnTasks = new ConcurrentHashMap<>();

    public DeathListener(ConfigManager configManager, StatsService stats,
                         BackButtonManager backButtonManager, TaskScheduler scheduler,
                         MessageManager messageManager, LegacyArenaSwordCleaner legacyArenaSwordCleaner,
                         StatsPersistenceService persistence) {
        this.configManager = configManager;
        this.stats = stats;
        this.backButtonManager = backButtonManager;
        this.scheduler = scheduler;
        this.messageManager = messageManager;
        this.legacyArenaSwordCleaner = legacyArenaSwordCleaner;
        this.persistence = persistence;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PluginConfig config = configManager.current();

        // 防止死亡时按钮掉落造成刷物品
        event.getDrops().removeIf(backButtonManager::isBackButton);
        event.getDrops().removeIf(legacyArenaSwordCleaner::isLegacyArenaSword);

        // 死亡到复活期间计入已淘汰，计分板“剩余玩家”随之减少；复活后由 RespawnListener 恢复
        stats.markEliminated(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            // 击杀结算本身也检查日期，避免关闭计分板或刷新尚未执行时沿用昨日数据。
            stats.rolloverIfNeeded(LocalDate.now());
            StatsService.KillResult result = stats.addKill(killer.getUniqueId(), killer.getName());
            persistence.recordKill(killer.getUniqueId(), killer.getName());
            if (result.newMvp()) {
                // 广播属于全局操作，使用全局调度器避免从玩家实体线程直接触碰全服消息。
                scheduler.runGlobal(() -> messageManager.broadcast("mvp.new", java.util.Map.of(
                        "player", result.name(),
                        "amount", String.valueOf(result.totalKills()))));
            }
            // 击杀完成后回到击杀者实体线程恢复生命值，避免跨区域直接修改 Player。
            scheduler.runAtEntity(killer, () -> {
                if (!killer.isOnline() || killer.isDead()) {
                    return;
                }
                var maxHealth = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    killer.setHealth(maxHealth.getValue());
                }
                killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            });
        }

        if (config.respawnAuto()) {
            // 延迟至少 1 tick，在死亡玩家自己的实体线程强制复活，跳过死亡界面
            ScheduledTask previous = respawnTasks.remove(victim.getUniqueId());
            if (previous != null) {
                scheduler.cancel(previous);
            }
            ScheduledTask task = scheduler.runAtEntityLater(victim, () -> {
                respawnTasks.remove(victim.getUniqueId());
                if (victim.isOnline() && victim.isDead()) {
                    victim.spigot().respawn();
                }
            }, config.respawnDelayTicks());
            if (task != null) {
                respawnTasks.put(victim.getUniqueId(), task);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScheduledTask task = respawnTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            scheduler.cancel(task);
        }
    }

    public void shutdown() {
        for (ScheduledTask task : respawnTasks.values()) {
            scheduler.cancel(task);
        }
        respawnTasks.clear();
    }
}
