package com.mcxyd.purearena.config;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 解析后的不可变配置快照。
 * reload 时整体替换，旧快照不再控制新业务。
 * 材质保存为名称字符串，解析为 Material 的动作放在物品工厂，
 * 避免配置层在无服务端环境（单元测试）初始化 Bukkit 注册表。
 */
public record PluginConfig(
        boolean knockbackEnabled,
        double knockbackHorizontal,
        double knockbackVertical,
        double knockbackVerticalLimit,
        int backButtonSlot,
        String backButtonMaterialName,
        int backButtonCustomModelData,
        String backButtonName,
        List<String> backButtonLore,
        String backButtonTargetServer,
        int backButtonCooldownSeconds,
        ArenaSpawn arenaSpawn,
        boolean healOnReturn,
        boolean clearInventoryOnReturn,
        boolean respawnAuto,
        long respawnDelayTicks,
        boolean cpsKickEnabled,
        int cpsKickThreshold,
        boolean cpsActionBarEnabled,
        String cpsActionBarFormat,
        boolean antiCheatEnabled,
        boolean antiCheatFlightCheckEnabled,
        boolean antiCheatReachCheckEnabled,
        double antiCheatReachDistance,
        int antiCheatReachViolationsToKick,
        boolean scoreboardEnabled,
        String scoreboardTitle,
        long scoreboardUpdateIntervalTicks,
        DateTimeFormatter scoreboardDateFormat,
        String scoreboardDateFormatPattern,
        String scoreboardNoMvpName,
        boolean scoreboardResetDaily,
        boolean scoreboardHideNumbers,
        List<String> scoreboardLines
) {

    /** 竞技场出生点坐标，世界以名称保存，进入时再解析。 */
    public record ArenaSpawn(boolean enabled, String world, double x, double y, double z,
                             float yaw, float pitch) {
    }

    public PluginConfig {
        backButtonLore = List.copyOf(backButtonLore);
        scoreboardLines = List.copyOf(scoreboardLines);
    }

    /** 槽位为 0 表示不显示按钮。 */
    public boolean backButtonVisible() {
        return backButtonSlot >= 1;
    }

    /** 转换为 0 基物品栏下标。 */
    public int backButtonInventoryIndex() {
        return backButtonSlot - 1;
    }
}
