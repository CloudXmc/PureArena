package com.mcxyd.purearena.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置管理器：首次生成默认配置、补全缺失节点（含中文注释）、验证并原子替换配置快照。
 * reload 失败时继续使用最后一次有效配置。
 */
public final class ConfigManager {

    private final Plugin plugin;
    // volatile 快照：计分板任务等会从各自线程读取
    private volatile PluginConfig current;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public PluginConfig current() {
        return current;
    }

    /**
     * 加载或重载 config.yml。
     * 成功后原子替换快照并返回修正警告；解析失败时抛出异常且不改动当前快照。
     */
    public synchronized List<String> load() throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception exception) {
            throw new IOException("config.yml 解析失败：" + exception.getMessage(), exception);
        }

        // 用内置默认配置补全缺失节点和注释，只补充，不覆盖用户已有设置
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

        List<String> warnings = new ArrayList<>();
        this.current = PluginConfigFactory.parse(loaded, warnings);
        return warnings;
    }

    private YamlConfiguration loadBundledDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new YamlConfiguration();
        }
    }
}
