package com.mcxyd.purearena.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** 玩家低于竞技场安全高度时自动死亡。 */
public final class VoidDeathListener implements Listener {

    private static final double DEATH_Y = 70.0D;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && !player.isDead() && event.getTo().getY() < DEATH_Y) {
            // 交给 Bukkit 正常死亡事件链处理统计、掉落清理和自动复活。
            player.setHealth(0.0D);
        }
    }
}
