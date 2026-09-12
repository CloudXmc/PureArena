package com.mcxyd.purearena;

import com.mcxyd.purearena.message.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本解析测试：MiniMessage、& 颜色代码、§ 颜色代码。
 */
class TextsTest {

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void 解析Ampersand颜色代码() {
        assertEquals("纯净竞技场", plain(Texts.parse("&a&l纯净竞技场")));
    }

    @Test
    void 解析Section颜色代码() {
        assertEquals("标题", plain(Texts.parse("§e标题")));
    }

    @Test
    void 解析MiniMessage() {
        assertEquals("标题", plain(Texts.parse("<green><bold>标题</bold></green>")));
    }

    @Test
    void 空文本返回空组件() {
        assertEquals("", plain(Texts.parse(null)));
        assertEquals("", plain(Texts.parse("")));
    }

    @Test
    void 物品文本关闭斜体() {
        Component c = Texts.parseItemText("&a返回大厅");
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE,
                c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
    }
}
