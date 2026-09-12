package com.mcxyd.purearena.config;

import org.bukkit.configuration.ConfigurationSection;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 把 YAML 配置解析为 PluginConfig。
 * 非法值不会导致失败：修正为安全值并写入 warnings，由调用方决定如何提示。
 * 材质校验器可注入，便于在无服务端环境下测试。
 */
public final class PluginConfigFactory {

    public static final String DEFAULT_DATE_FORMAT = "yyyy/MM/dd";
    public static final String DEFAULT_MATERIAL = "SLIME_BALL";
    private static final int MAX_SCOREBOARD_LINES = 15;

    private PluginConfigFactory() {
    }

    /** 生产环境入口：用 Bukkit Material 表校验材质名。 */
    public static PluginConfig parse(ConfigurationSection root, List<String> warnings) {
        return parse(root, warnings, PluginConfigFactory::isValidBukkitMaterial);
    }

    private static boolean isValidBukkitMaterial(String name) {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(name);
        return material != null && material.isItem();
    }

    public static PluginConfig parse(ConfigurationSection root, List<String> warnings,
                                     Predicate<String> materialValidator) {
        boolean kbEnabled = root.getBoolean("knockback.enabled", true);
        double horizontal = clampDouble(root.getDouble("knockback.horizontal", 1.0), 0.0, 10.0,
                "knockback.horizontal", warnings);
        double vertical = clampDouble(root.getDouble("knockback.vertical", 1.0), 0.0, 10.0,
                "knockback.vertical", warnings);
        double verticalLimit = clampDouble(root.getDouble("knockback.vertical-limit", 0.45), 0.0, 4.0,
                "knockback.vertical-limit", warnings);

        int slot = clampInt(root.getInt("back_button.slot", 9), 0, 9, "back_button.slot", warnings);
        String materialName = parseMaterialName(root.getString("back_button.material", DEFAULT_MATERIAL),
                materialValidator, warnings);
        int customModelData = clampInt(root.getInt("back_button.custom_model_data", 0), 0, Integer.MAX_VALUE,
                "back_button.custom_model_data", warnings);
        String buttonName = root.getString("back_button.name", "&a返回大厅 &7(左键/右键)");
        List<String> buttonLore = root.isList("back_button.lore")
                ? root.getStringList("back_button.lore")
                : List.of("&7左键或右键点击，返回大厅子服。", "&e该物品无法丢弃或移动。");
        String buttonTarget = requireNonBlank(root.getString("back_button.target-server", "server"),
                "server", "back_button.target-server", warnings);
        int cooldown = clampInt(root.getInt("back_button.cooldown-seconds", 3), 0, Integer.MAX_VALUE,
                "back_button.cooldown-seconds", warnings);

        // 竞技场出生点：开启但世界名为空时自动关闭，避免误传送
        boolean spawnEnabled = root.getBoolean("arena.spawn.enabled", false);
        String spawnWorld = root.getString("arena.spawn.world", "world");
        if (spawnEnabled && (spawnWorld == null || spawnWorld.isBlank())) {
            warnings.add("arena.spawn.world 不能为空，出生点传送已自动关闭。");
            spawnEnabled = false;
        }
        PluginConfig.ArenaSpawn arenaSpawn = new PluginConfig.ArenaSpawn(
                spawnEnabled,
                spawnWorld == null ? "world" : spawnWorld,
                root.getDouble("arena.spawn.x", 0.5),
                root.getDouble("arena.spawn.y", 100.0),
                root.getDouble("arena.spawn.z", 0.5),
                (float) root.getDouble("arena.spawn.yaw", 0.0),
                (float) root.getDouble("arena.spawn.pitch", 0.0));

        boolean healOnReturn = root.getBoolean("heal.on-return", true);
        boolean clearInventoryOnReturn = root.getBoolean("inventory.clear-on-return", true);
        boolean respawnAuto = root.getBoolean("respawn.auto", true);
        long respawnDelay = atLeastOne(root.getLong("respawn.delay-ticks", 20L),
                "respawn.delay-ticks", warnings);

        boolean cpsKickEnabled = root.getBoolean("cps.kick-enabled", true);
        int cpsKickThreshold = clampInt(root.getInt("cps.kick-threshold", 15), 1, Integer.MAX_VALUE,
                "cps.kick-threshold", warnings);
        boolean cpsActionBarEnabled = root.getBoolean("cps.actionbar.enabled", true);
        String cpsActionBarFormat = root.getString("cps.actionbar.format",
                "&fCPS: &a%cps% &7| 最高: &c%max_cps%");

        boolean antiCheatEnabled = root.getBoolean("anti-cheat.enabled", true);
        boolean flightCheckEnabled = root.getBoolean("anti-cheat.flight-check.enabled", true);
        boolean reachCheckEnabled = root.getBoolean("anti-cheat.reach-check.enabled", true);
        double reachDistance = clampDouble(root.getDouble("anti-cheat.reach-check.max-distance", 3.6),
                3.0, 8.0, "anti-cheat.reach-check.max-distance", warnings);
        int reachViolationsToKick = clampInt(root.getInt("anti-cheat.reach-check.violations-to-kick", 3),
                1, 20, "anti-cheat.reach-check.violations-to-kick", warnings);

        boolean sbEnabled = root.getBoolean("scoreboard.enabled", true);
        String sbTitle = root.getString("scoreboard.title", "&a&l纯净竞技场");
        long sbInterval = atLeastOne(root.getLong("scoreboard.update-interval-ticks", 20L),
                "scoreboard.update-interval-ticks", warnings);
        String datePattern = root.getString("scoreboard.date-format", DEFAULT_DATE_FORMAT);
        DateTimeFormatter dateFormat;
        try {
            dateFormat = DateTimeFormatter.ofPattern(datePattern);
        } catch (IllegalArgumentException exception) {
            warnings.add("scoreboard.date-format 格式无效：" + datePattern + "，已回退为 " + DEFAULT_DATE_FORMAT);
            datePattern = DEFAULT_DATE_FORMAT;
            dateFormat = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        }
        String noMvpName = root.getString("scoreboard.no-mvp-name", "暂无");
        boolean resetDaily = root.getBoolean("scoreboard.reset-daily", true);
        boolean hideNumbers = root.getBoolean("scoreboard.hide-numbers", true);
        List<String> lines = root.isList("scoreboard.lines")
                ? new ArrayList<>(root.getStringList("scoreboard.lines"))
                : new ArrayList<>(List.of(
                        " &7%date% 单人模式",
                        "",
                        " &fMVP: &6%mvp_name%&f(&c%mvp_amount%&f)",
                        " &f剩余玩家: &b%remain_player%",
                        "",
                        " &f击杀: &6%kill_amount%",
                        ""));
        if (lines.size() > MAX_SCOREBOARD_LINES) {
            warnings.add("scoreboard.lines 超过 " + MAX_SCOREBOARD_LINES + " 行，已截断多余行。");
            lines = new ArrayList<>(lines.subList(0, MAX_SCOREBOARD_LINES));
        }

        return new PluginConfig(kbEnabled, horizontal, vertical, verticalLimit,
                slot, materialName, customModelData, buttonName, buttonLore, buttonTarget, cooldown,
                arenaSpawn, healOnReturn, clearInventoryOnReturn, respawnAuto, respawnDelay,
                cpsKickEnabled, cpsKickThreshold, cpsActionBarEnabled, cpsActionBarFormat,
                antiCheatEnabled, flightCheckEnabled, reachCheckEnabled, reachDistance, reachViolationsToKick,
                sbEnabled, sbTitle, sbInterval, dateFormat, datePattern, noMvpName, resetDaily, hideNumbers, lines);
    }

    private static double clampDouble(double value, double min, double max, String path, List<String> warnings) {
        if (value < min || value > max) {
            double fixed = Math.min(max, Math.max(min, value));
            warnings.add(path + " 取值 " + value + " 超出范围 [" + min + ", " + max + "]，已修正为 " + fixed);
            return fixed;
        }
        return value;
    }

    private static int clampInt(int value, int min, int max, String path, List<String> warnings) {
        if (value < min || value > max) {
            int fixed = Math.min(max, Math.max(min, value));
            warnings.add(path + " 取值 " + value + " 超出范围，已修正为 " + fixed);
            return fixed;
        }
        return value;
    }

    private static long atLeastOne(long value, String path, List<String> warnings) {
        if (value < 1L) {
            warnings.add(path + " 取值 " + value + " 小于 1 tick，已修正为 1。");
            return 1L;
        }
        return value;
    }

    private static String parseMaterialName(String name, Predicate<String> materialValidator,
                                            List<String> warnings) {
        String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || !materialValidator.test(normalized)) {
            warnings.add("back_button.material 材质无效：" + name + "，已回退为 " + DEFAULT_MATERIAL + "。");
            return DEFAULT_MATERIAL;
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String fallback, String path, List<String> warnings) {
        if (value == null || value.isBlank()) {
            warnings.add(path + " 不能为空，已回退为 " + fallback);
            return fallback;
        }
        return value;
    }

}
