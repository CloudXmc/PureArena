package com.mcxyd.purearena;

import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.config.PluginConfigFactory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置解析与校验测试：默认值、越界修正、非法值回退。
 * 材质校验器注入假实现，避免在无服务端环境初始化 Bukkit 注册表。
 */
class PluginConfigFactoryTest {

    /** 测试用材质表。 */
    private static final Predicate<String> MATERIALS =
            Set.of("SLIME_BALL", "MAGMA_CREAM", "PAPER")::contains;

    private PluginConfig parse(String yaml, List<String> warnings) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return PluginConfigFactory.parse(cfg, warnings, MATERIALS);
    }

    @Test
    void 空配置使用内置默认值() throws Exception {
        PluginConfig cfg = parse("", new ArrayList<>());
        assertTrue(cfg.knockbackEnabled());
        assertEquals(1.0, cfg.knockbackHorizontal());
        assertEquals(1.0, cfg.knockbackVertical());
        assertEquals(9, cfg.backButtonSlot());
        assertEquals("SLIME_BALL", cfg.backButtonMaterialName());
        assertEquals(0, cfg.backButtonCustomModelData());
        assertEquals("server", cfg.backButtonTargetServer());
        assertTrue(cfg.scoreboardEnabled());
        assertFalse(cfg.scoreboardLines().isEmpty());
    }

    @Test
    void 击退倍率越界会被修正并产生警告() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse("knockback:\n  horizontal: 99.0\n  vertical: -1.0\n", warnings);
        assertEquals(10.0, cfg.knockbackHorizontal());
        assertEquals(0.0, cfg.knockbackVertical());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 槽位0表示隐藏按钮且越界槽位被修正() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig hidden = parse("back_button:\n  slot: 0\n", warnings);
        assertEquals(0, hidden.backButtonSlot());
        assertFalse(hidden.backButtonVisible());
        PluginConfig clamped = parse("back_button:\n  slot: 12\n", warnings);
        assertEquals(9, clamped.backButtonSlot());
        assertEquals(8, clamped.backButtonInventoryIndex());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 合法材质名被规范化() throws Exception {
        PluginConfig cfg = parse("back_button:\n  material: magma_cream\n", new ArrayList<>());
        assertEquals("MAGMA_CREAM", cfg.backButtonMaterialName());
    }

    @Test
    void 非法材质回退为史莱姆球() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse("back_button:\n  material: NOT_A_MATERIAL\n", warnings);
        assertEquals("SLIME_BALL", cfg.backButtonMaterialName());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 刷新间隔小于1会被修正为1() throws Exception {
        PluginConfig cfg = parse("scoreboard:\n  update-interval-ticks: -5\n", new ArrayList<>());
        assertEquals(1, cfg.scoreboardUpdateIntervalTicks());
    }

    @Test
    void 非法日期格式回退默认格式() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse("scoreboard:\n  date-format: 'bbbbb'\n", warnings);
        String expected = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertEquals(expected, LocalDate.now().format(cfg.scoreboardDateFormat()));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 计分板行数超过15行会被截断() throws Exception {
        StringBuilder yaml = new StringBuilder("scoreboard:\n  lines:\n");
        for (int i = 0; i < 20; i++) {
            yaml.append("  - 'line").append(i).append("'\n");
        }
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse(yaml.toString(), warnings);
        assertEquals(15, cfg.scoreboardLines().size());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 每日重置默认开启且可关闭() throws Exception {
        assertTrue(parse("", new ArrayList<>()).scoreboardResetDaily());
        assertFalse(parse("scoreboard:\n  reset-daily: false\n", new ArrayList<>()).scoreboardResetDaily());
    }

    @Test
    void 隐藏分数数字默认开启且可关闭() throws Exception {
        assertTrue(parse("", new ArrayList<>()).scoreboardHideNumbers());
        assertFalse(parse("scoreboard:\n  hide-numbers: false\n", new ArrayList<>()).scoreboardHideNumbers());
    }

    @Test
    void 竞技场出生点默认关闭() throws Exception {
        PluginConfig cfg = parse("", new ArrayList<>());
        assertFalse(cfg.arenaSpawn().enabled());
    }

    @Test
    void 竞技场出生点坐标解析() throws Exception {
        PluginConfig cfg = parse("""
                arena:
                  spawn:
                    enabled: true
                    world: arena_world
                    x: 100.5
                    y: 65.0
                    z: -20.5
                    yaw: 90.0
                    pitch: -10.0
                """, new ArrayList<>());
        PluginConfig.ArenaSpawn spawn = cfg.arenaSpawn();
        assertTrue(spawn.enabled());
        assertEquals("arena_world", spawn.world());
        assertEquals(100.5, spawn.x());
        assertEquals(65.0, spawn.y());
        assertEquals(-20.5, spawn.z());
        assertEquals(90.0f, spawn.yaw());
        assertEquals(-10.0f, spawn.pitch());
    }

    @Test
    void 出生点开启但世界名为空时自动关闭并警告() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse("arena:\n  spawn:\n    enabled: true\n    world: ''\n", warnings);
        assertFalse(cfg.arenaSpawn().enabled());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 返回大厅恢复血量默认开启且可关闭() throws Exception {
        assertTrue(parse("", new ArrayList<>()).healOnReturn());
        assertFalse(parse("heal:\n  on-return: false\n", new ArrayList<>()).healOnReturn());
    }

    @Test
    void 返回大厅清空物资默认开启且可关闭() throws Exception {
        assertTrue(parse("", new ArrayList<>()).clearInventoryOnReturn());
        assertFalse(parse("inventory:\n  clear-on-return: false\n", new ArrayList<>()).clearInventoryOnReturn());
    }


    @Test
    void CPS踢出默认开启且阈值为15() throws Exception {
        PluginConfig cfg = parse("", new ArrayList<>());
        assertTrue(cfg.cpsKickEnabled());
        assertEquals(15, cfg.cpsKickThreshold());
    }

    @Test
    void CPS阈值小于1会被修正() throws Exception {
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = parse("cps:\n  kick-enabled: false\n  kick-threshold: 0\n", warnings);
        assertFalse(cfg.cpsKickEnabled());
        assertEquals(1, cfg.cpsKickThreshold());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void CPS动作栏默认开启且格式含占位符() throws Exception {
        PluginConfig cfg = parse("", new ArrayList<>());
        assertTrue(cfg.cpsActionBarEnabled());
        assertTrue(cfg.cpsActionBarFormat().contains("%cps%"));
        assertTrue(cfg.cpsActionBarFormat().contains("%max_cps%"));
        assertFalse(parse("cps:\n  actionbar:\n    enabled: false\n", new ArrayList<>()).cpsActionBarEnabled());
    }

    @Test
    void 反作弊默认开启且超距参数可修正() throws Exception {
        PluginConfig defaults = parse("", new ArrayList<>());
        assertTrue(defaults.antiCheatEnabled());
        assertTrue(defaults.antiCheatFlightCheckEnabled());
        assertTrue(defaults.antiCheatReachCheckEnabled());
        assertEquals(3.6, defaults.antiCheatReachDistance());
        assertEquals(3, defaults.antiCheatReachViolationsToKick());

        List<String> warnings = new ArrayList<>();
        PluginConfig clamped = parse("anti-cheat:\n  reach-check:\n    max-distance: 1\n    violations-to-kick: 99\n", warnings);
        assertEquals(3.0, clamped.antiCheatReachDistance());
        assertEquals(20, clamped.antiCheatReachViolationsToKick());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void 自动复活默认开启且延迟不小于1() throws Exception {
        PluginConfig cfg = parse("", new ArrayList<>());
        assertTrue(cfg.respawnAuto());
        assertEquals(20, cfg.respawnDelayTicks());
        PluginConfig clamped = parse("respawn:\n  auto: false\n  delay-ticks: 0\n", new ArrayList<>());
        assertFalse(clamped.respawnAuto());
        assertEquals(1, clamped.respawnDelayTicks());
    }
}
