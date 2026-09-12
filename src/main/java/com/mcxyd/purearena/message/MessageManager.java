package com.mcxyd.purearena.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 消息管理器：加载 messages.yml、补全缺失节点、统一前缀发送。
 * 消息读取全部走本类，业务代码不直接接触 YAML。
 */
public final class MessageManager {

    private final Plugin plugin;
    // reload 时整体替换快照，避免旧配置继续控制新业务
    private volatile YamlConfiguration messages = new YamlConfiguration();

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 加载或重载消息文件。失败时抛出异常，由调用方决定是否保留旧消息。 */
    public synchronized void load() throws IOException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception exception) {
            throw new IOException("messages.yml 解析失败：" + exception.getMessage(), exception);
        }
        // 用内置默认值补全缺失节点（含注释），不覆盖用户已有设置
        YamlConfiguration defaults = loadBundledDefaults();
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!loaded.contains(key)) {
                loaded.set(key, defaults.get(key));
                loaded.setComments(key, defaults.getComments(key));
                changed = true;
            }
        }
        if (changed) {
            loaded.save(file);
        }
        this.messages = loaded;
    }

    private YamlConfiguration loadBundledDefaults() {
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new YamlConfiguration();
        }
    }

    public Component prefix() {
        return Texts.parse(messages.getString("prefix", ""));
    }

    /** 读取消息并替换 {key} 形式的占位符。 */
    public Component message(String path, Map<String, String> placeholders) {
        String raw = messages.getString(path, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return Texts.parse(raw);
    }

    /** 带统一前缀发送。 */
    public void send(CommandSender receiver, String path) {
        send(receiver, path, Map.of());
    }

    public void send(CommandSender receiver, String path, Map<String, String> placeholders) {
        receiver.sendMessage(prefix().append(message(path, placeholders)));
    }

    /** 在全局调度上下文向全服广播带统一前缀的消息。 */
    public void broadcast(String path, Map<String, String> placeholders) {
        Bukkit.broadcast(prefix().append(message(path, placeholders)));
    }

    /** 不带前缀发送（帮助列表等整行内容）。 */
    public void sendRaw(CommandSender receiver, String path) {
        receiver.sendMessage(message(path, Map.of()));
    }

    public List<String> stringList(String path) {
        return messages.getStringList(path);
    }
}
