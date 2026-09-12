package com.mcxyd.purearena.listener;

import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.service.LobbyConnectService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 返回大厅按钮：点击触发跨服，并阻止按钮被丢弃、移动或换手。
 * 所有事件都在对应玩家所有者上下文触发。
 */
public final class BackButtonListener implements Listener {

    private final ConfigManager configManager;
    private final BackButtonManager backButtonManager;
    private final LobbyConnectService lobbyConnectService;

    public BackButtonListener(ConfigManager configManager, BackButtonManager backButtonManager,
                              LobbyConnectService lobbyConnectService) {
        this.configManager = configManager;
        this.backButtonManager = backButtonManager;
        this.lobbyConnectService = lobbyConnectService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        ItemStack item = event.getItem();
        if (!backButtonManager.isBackButton(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PluginConfig config = configManager.current();
        // 冷却防抖由服务内部保证，双击不会重复发送
        lobbyConnectService.sendWithCooldown(player, config.backButtonTargetServer(),
                config.backButtonCooldownSeconds(), config.healOnReturn(), config.clearInventoryOnReturn());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (backButtonManager.isBackButton(event.getCurrentItem())
                || backButtonManager.isBackButton(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        // 数字键把按钮换到其他位置
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (backButtonManager.isBackButton(hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (backButtonManager.isBackButton(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (backButtonManager.isBackButton(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (backButtonManager.isBackButton(event.getMainHandItem())
                || backButtonManager.isBackButton(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}
