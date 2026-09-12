package com.mcxyd.purearena.command.sub;

import com.mcxyd.purearena.board.ScoreboardService;
import com.mcxyd.purearena.command.SubCommand;
import com.mcxyd.purearena.config.ConfigManager;
import com.mcxyd.purearena.item.BackButtonManager;
import com.mcxyd.purearena.message.MessageManager;
import com.mcxyd.purearena.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /purearena reload：异步读取并验证配置，失败时继续使用旧配置。
 * 文件 IO 不在指令线程（Region/实体线程）执行。
 */
public final class ReloadSubCommand implements SubCommand {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final TaskScheduler scheduler;
    private final BackButtonManager backButtonManager;
    private final ScoreboardService scoreboardService;

    public ReloadSubCommand(Plugin plugin, ConfigManager configManager, MessageManager messageManager,
                            TaskScheduler scheduler, BackButtonManager backButtonManager,
                            ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.scheduler = scheduler;
        this.backButtonManager = backButtonManager;
        this.scoreboardService = scoreboardService;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "purearena.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        // 异步线程只负责文件读取与配置验证，不接触 Player、在线玩家集合或背包。
        scheduler.runAsync(() -> {
            try {
                var warnings = configManager.load();
                messageManager.load();
                for (String warning : warnings) {
                    plugin.getLogger().warning("配置修正：" + warning);
                }
                dispatchResult(sender, senderId, "command.reload-success", Map.of(), true);
            } catch (Exception exception) {
                // 解析失败：快照未被替换，旧配置继续生效
                plugin.getLogger().log(Level.WARNING, "配置重载失败", exception);
                dispatchResult(sender, senderId, "command.reload-failed",
                        Map.of("reason", String.valueOf(exception.getMessage())), false);
            }
        });
    }

    private void dispatchResult(CommandSender sender, UUID senderId, String messagePath,
                                Map<String, String> placeholders, boolean success) {
        // 全局上下文负责枚举在线玩家；玩家消息再转回对应实体上下文。
        scheduler.runGlobal(() -> {
            if (senderId == null) {
                messageManager.send(sender, messagePath, placeholders);
            } else {
                Player player = Bukkit.getPlayer(senderId);
                if (player != null) {
                    scheduler.runAtEntity(player, () -> {
                        if (player.isOnline()) {
                            messageManager.send(player, messagePath, placeholders);
                        }
                    });
                }
            }
            if (success) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    scheduler.runAtEntity(player, () -> backButtonManager.give(player));
                }
                scoreboardService.restartAll();
            }
        });
    }
}
