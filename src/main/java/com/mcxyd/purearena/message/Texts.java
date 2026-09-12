package com.mcxyd.purearena.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 无状态文本解析工具。
 * 兼容三种格式：§ 传统颜色代码、& 传统颜色代码、MiniMessage。
 * 传统颜色代码统一走 Adventure Legacy Serializer，不做手写替换。
 */
public final class Texts {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Texts() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (text.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0) {
            return SECTION.deserialize(text);
        }
        if (text.indexOf(LegacyComponentSerializer.AMPERSAND_CHAR) >= 0) {
            return AMPERSAND.deserialize(text);
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /** 物品名称与 lore 专用：解析后显式关闭斜体。 */
    public static Component parseItemText(String text) {
        return parse(text).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
