package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.BoundingBox;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 竞技场轻量反作弊：拦截未授权飞行并检测明显超出正常范围的近战距离。
 * 这是基础防护，不替代专业反作弊；管理员可用 purearena.anticheat.bypass 绕过。
 */
public final class AntiCheatListener implements Listener {

    private static final String BYPASS_PERMISSION = "purearena.anticheat.bypass";
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final TaskScheduler scheduler;
    private final Map<UUID, Integer> reachViolations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> flightWarnings = new ConcurrentHashMap<>();

    public AntiCheatListener(ConfigManager configManager, MessageManager messageManager,
                             TaskScheduler scheduler) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isFlightCheckEnabled(player) || !event.isFlying()) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        warnFlight(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isFlightCheckEnabled(player)) {
            flightWarnings.remove(player.getUniqueId());
            return;
        }
        if (player.isFlying()) {
            player.setFlying(false);
            warnFlight(player);
        } else {
            flightWarnings.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!isReachCheckEnabled(attacker) || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        double distance = distanceToBoundingBox(attacker, victim.getBoundingBox());
        UUID attackerId = attacker.getUniqueId();
        if (distance <= configManager.current().antiCheatReachDistance()) {
            reachViolations.remove(attackerId);
            return;
        }

        event.setCancelled(true);
        int violations = reachViolations.merge(attackerId, 1, Integer::sum);
        if (violations >= configManager.current().antiCheatReachViolationsToKick()) {
            reachViolations.remove(attackerId);
            scheduler.runAtEntity(attacker, () -> {
                if (attacker.isOnline()) {
                    attacker.kick(messageManager.message("anti-cheat.reach-kick", Map.of()));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }

    public void clearPlayer(UUID playerId) {
        reachViolations.remove(playerId);
        flightWarnings.remove(playerId);
    }

    public void shutdown() {
        reachViolations.clear();
        flightWarnings.clear();
    }

    private boolean isFlightCheckEnabled(Player player) {
        PluginConfig config = configManager.current();
        return config.antiCheatEnabled() && config.antiCheatFlightCheckEnabled() && !isExempt(player)
                && !player.getAllowFlight();
    }

    private boolean isReachCheckEnabled(Player player) {
        PluginConfig config = configManager.current();
        return config.antiCheatEnabled() && config.antiCheatReachCheckEnabled() && !isExempt(player);
    }

    private boolean isExempt(Player player) {
        return player.hasPermission(BYPASS_PERMISSION)
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }

    private void warnFlight(Player player) {
        if (flightWarnings.putIfAbsent(player.getUniqueId(), Boolean.TRUE) == null) {
            messageManager.send(player, "anti-cheat.flight-blocked");
        }
    }

    /** 计算攻击者眼部到受害者碰撞箱的最短欧氏距离，避免只比较脚底导致误判。 */
    private double distanceToBoundingBox(Player attacker, BoundingBox target) {
        double eyeX = attacker.getEyeLocation().getX();
        double eyeY = attacker.getEyeLocation().getY();
        double eyeZ = attacker.getEyeLocation().getZ();
        double x = clamp(eyeX, target.getMinX(), target.getMaxX());
        double y = clamp(eyeY, target.getMinY(), target.getMaxY());
        double z = clamp(eyeZ, target.getMinZ(), target.getMaxZ());
        return Math.sqrt(distanceSquared(eyeX, eyeY, eyeZ, x, y, z));
    }

    private double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
