package com.mcxyd.purearena;

import com.mcxyd.purearena.config.PluginConfig;
import com.mcxyd.purearena.config.PluginConfigFactory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 打包资源 YAML 解析检查：config.yml 与 messages.yml 必须可解析，
 * 且默认值与需求一致。
 */
class BundledResourcesTest {

    private YamlConfiguration loadResource(String name) {
        var stream = getClass().getResourceAsStream("/" + name);
        assertNotNull(stream, name + " 资源缺失");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test
    void 默认config可解析且与需求一致() {
        YamlConfiguration yaml = loadResource("config.yml");
        List<String> warnings = new ArrayList<>();
        PluginConfig cfg = PluginConfigFactory.parse(yaml, warnings, "SLIME_BALL"::equals);
        assertTrue(warnings.isEmpty(), "默认配置不应产生修正警告：" + warnings);
        assertEquals(9, cfg.backButtonSlot());
        assertEquals("SLIME_BALL", cfg.backButtonMaterialName());
        assertEquals(0, cfg.backButtonCustomModelData());
        assertEquals("server", cfg.backButtonTargetServer());
        assertFalse(cfg.arenaSpawn().enabled(), "默认配置的竞技场出生点应为关闭");
        assertTrue(cfg.healOnReturn());
        assertEquals("&a&l纯净竞技场", cfg.scoreboardTitle());
        assertEquals(7, cfg.scoreboardLines().size());
        assertEquals(" &7%date% 单人模式", cfg.scoreboardLines().get(0));
        assertEquals(" &fMVP: &6%mvp_name%&f(&c%mvp_amount%&f)", cfg.scoreboardLines().get(2));
        assertEquals(" &f剩余玩家: &b%remain_player%", cfg.scoreboardLines().get(3));
        assertEquals(" &f击杀: &6%kill_amount%", cfg.scoreboardLines().get(5));
        assertTrue(cfg.cpsActionBarEnabled());
        assertTrue(cfg.antiCheatEnabled());
    }

    @Test
    void 默认messages可解析且关键节点齐全() {
        YamlConfiguration yaml = loadResource("messages.yml");
        assertNotNull(yaml.getString("prefix"));
        assertTrue(yaml.getString("prefix").contains("gradient"));
        assertTrue(yaml.getString("mvp.new").contains("今日累计击杀"));
        for (String key : new String[]{"connect.sending", "connect.cooldown", "arena.spawn-failed",
                "mvp.new",
                "command.no-permission", "command.unknown", "command.reload-success", "command.reload-failed",
                "anti-cheat.flight-blocked", "anti-cheat.reach-kick",
                "help.header", "help.footer"}) {
            assertNotNull(yaml.getString(key), key + " 节点缺失");
        }
        assertFalse(yaml.getStringList("help.player").isEmpty());
        assertFalse(yaml.getStringList("help.admin").isEmpty());
    }
}
