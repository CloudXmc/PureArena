package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.service.KnockbackService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * PVP 伤害监听：提取攻击者位置快照后转交击退服务。
 * 事件在受害者所有者上下文触发。
 */
public final class CombatListener implements Listener {

    private final KnockbackService knockbackService;

    public CombatListener(KnockbackService knockbackService) {
        this.knockbackService = knockbackService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // 伤害事件双方处于同一区域交互范围内，此处读取的是事件时刻的位置快照
        Location attackerLocation = event.getDamager() instanceof Projectile projectile
                ? projectile.getLocation().clone()
                : attacker.getLocation().clone();
        knockbackService.applyKnockback(victim, attackerLocation);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        knockbackService.clearPlayer(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        knockbackService.shutdown();
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
