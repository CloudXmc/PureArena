package com.mcxyd.purearena.service;

import com.mcxyd.purearena.message.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 BungeeCord 插件消息通道把玩家送往其他子服。
 * 必须在玩家实体所有者上下文调用 send 方法。
 * 冷却表用于防止重复提交，玩家退出时清理。
 */
public final class LobbyConnectService {

    public static final String CHANNEL = "BungeeCord";

    private final Plugin plugin;
    private final MessageManager messageManager;
    // 事件线程与实体任务并发访问，使用并发 Map；条目在玩家退出时移除
    private final Map<UUID, Long> lastSendMillis = new ConcurrentHashMap<>();

    public LobbyConnectService(Plugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    /** 编码 BungeeCord Connect 报文。 */
    public static byte[] buildConnectPayload(String serverName) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException exception) {
            // 内存流不会真正抛出 IO 异常
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    /**
     * 点击返回按钮触发：带冷却与玩家提示，可选在离开前清空物资并恢复血量。
     * 返回是否真正发送了请求。
     */
    public boolean sendWithCooldown(Player player, String serverName, int cooldownSeconds,
                                    boolean healBeforeSend, boolean clearInventoryBeforeSend) {
        long now = System.currentTimeMillis();
        long cooldownMillis = cooldownSeconds * 1000L;
        // 复合读改写用 compute 保证原子性，防止双击瞬间重复发送
        boolean[] sent = {false};
        lastSendMillis.compute(player.getUniqueId(), (id, last) -> {
            if (last != null && now - last < cooldownMillis) {
                return last;
            }
            sent[0] = true;
            return now;
        });
        if (!sent[0]) {
            long remain = Math.max(1L, (cooldownMillis - (now - lastSendMillis.getOrDefault(player.getUniqueId(), now))) / 1000L + 1L);
            messageManager.send(player, "connect.cooldown", Map.of("seconds", String.valueOf(remain)));
            return false;
        }
        if (clearInventoryBeforeSend) {
            // 清空背包、盔甲与副手，竞技场物资不带回大厅，下次进入为全新状态
            player.getInventory().clear();
        }
        if (healBeforeSend) {
            heal(player);
        }
        messageManager.send(player, "connect.sending", Map.of("server", serverName));
        connect(player, serverName);
        return true;
    }

    /**
     * 恢复血量、饱食度并熄灭火焰，让竞技场服保存的存档保持健康状态。
     * 必须在玩家实体所有者上下文调用；死亡中的玩家跳过。
     */
    private void heal(Player player) {
        if (player.isDead()) {
            return;
        }
        var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
    }

    private void connect(Player player, String serverName) {
        player.sendPluginMessage(plugin, CHANNEL, buildConnectPayload(serverName));
    }

    public void clearPlayer(UUID playerId) {
        lastSendMillis.remove(playerId);
    }
}
