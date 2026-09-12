package com.mcxyd.purearena;

import com.mcxyd.purearena.board.ScoreboardLineRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计分板占位符替换测试。
 */
class ScoreboardLineRendererTest {

    private static final ScoreboardLineRenderer.LineContext CONTEXT =
            new ScoreboardLineRenderer.LineContext("2026/09/10", "Alice", 3, 7, 3, 12, 18);

    @Test
    void 替换全部占位符() {
        String line = " &fMVP: &6%mvp_name%&f(&c%mvp_amount%&f) %date% %remain_player% %kill_amount% %cps% %max_cps%";
        String out = ScoreboardLineRenderer.render(line, CONTEXT);
        assertEquals(" &fMVP: &6Alice&f(&c3&f) 2026/09/10 7 3 12 18", out);
    }

    @Test
    void 无占位符的行保持原样() {
        assertEquals("", ScoreboardLineRenderer.render("", CONTEXT));
        assertEquals(" &7单人模式", ScoreboardLineRenderer.render(" &7单人模式", CONTEXT));
    }
}
