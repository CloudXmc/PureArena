package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.message.Texts;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import com.mcxyd.purearena.service.CpsService;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CPS 监听：统计攻击/空气挥动次数，排除准星命中方块的挖掘挥动，实时在动作栏显示，超过阈值时踢出。
 * PlayerArmSwingEvent 在该玩家所有者上下文触发。
 */
public final class CpsListener implements Listener {

    private static final long ACTION_BAR_CLEAR_DELAY_TICKS = 20L;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CpsService cpsService;
    private final TaskScheduler scheduler;
    // 每个玩家只保留一个清理任务，避免停止攻击后积累延迟任务。
    private final Map<UUID, ScheduledTask> actionBarClearTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionBarGenerations = new ConcurrentHashMap<>();

    public CpsListener(ConfigManager configManager, MessageManager messageManager,
                       CpsService cpsService, TaskScheduler scheduler) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.cpsService = cpsService;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onArmSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        // PlayerArmSwingEvent 同时覆盖攻击和挖方块；若准星首先命中方块，则视为挖掘动作。
        // 命中实体时仍计入 CPS，完全没有目标时也保留空气挥动统计。
        RayTraceResult entityHit = player.rayTraceEntities(5);
        RayTraceResult blockHit = player.rayTraceBlocks(4.5);
        if (blockHit != null && (entityHit == null || isBlockInFront(player, blockHit, entityHit))) {
            return;
        }
        int cps = cpsService.registerClick(playerId, now);
        // 每次挥动都立即重置原版攻击蓄力，保持插件竞技场的无攻击冷却行为。
        player.resetCooldown();
        PluginConfig config = configManager.current();

        // 动作栏最高值只属于当前连续攻击动作，停止 1 秒后与动作栏一起清零。
        if (config.cpsActionBarEnabled()) {
            String rendered = config.cpsActionBarFormat()
                    .replace("%max_cps%", String.valueOf(cpsService.actionMaxCps(playerId)))
                    .replace("%cps%", String.valueOf(cps));
            player.sendActionBar(Texts.parse(rendered));
            scheduleActionBarClear(player, playerId);
        }

        if (config.cpsKickEnabled() && cps > config.cpsKickThreshold()) {
            // 踢出放到下一 tick 的玩家实体线程执行，不在事件分发中直接断开连接
            scheduler.runAtEntity(player, () -> {
                if (player.isOnline()) {
                    player.kick(messageManager.message("cps.kick-message", Map.of()));
                }
            });
        }
    }

    private boolean isBlockInFront(Player player, RayTraceResult blockHit, RayTraceResult entityHit) {
        if (blockHit.getHitPosition() == null || entityHit.getHitPosition() == null) {
            return true;
        }
        var eye = player.getEyeLocation().toVector();
        return eye.distanceSquared(blockHit.getHitPosition()) <= eye.distanceSquared(entityHit.getHitPosition());
    }

    private void scheduleActionBarClear(Player player, UUID playerId) {
        ScheduledTask previous = actionBarClearTasks.remove(playerId);
        if (previous != null) {
            scheduler.cancel(previous);
        }

        long generation = actionBarGenerations.merge(playerId, 1L, Long::sum);
        ScheduledTask task = scheduler.runAtEntityLater(player, () -> {
            if (!Long.valueOf(generation).equals(actionBarGenerations.get(playerId))) {
                return;
            }
            actionBarClearTasks.remove(playerId);
            actionBarGenerations.remove(playerId);
            cpsService.clearActionMaxCps(playerId);
            if (player.isOnline()) {
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
            }
        }, ACTION_BAR_CLEAR_DELAY_TICKS);
        if (task != null) {
            actionBarClearTasks.put(playerId, task);
        }
    }

    /** 玩家退出时清理动作栏状态和延迟任务。 */
    public void clearPlayer(UUID playerId) {
        ScheduledTask task = actionBarClearTasks.remove(playerId);
        if (task != null) {
            scheduler.cancel(task);
        }
        actionBarGenerations.remove(playerId);
    }

    /** 插件关闭时取消所有动作栏清理任务。 */
    public void shutdown() {
        for (ScheduledTask task : actionBarClearTasks.values()) {
            scheduler.cancel(task);
        }
        actionBarClearTasks.clear();
        actionBarGenerations.clear();
    }
}
