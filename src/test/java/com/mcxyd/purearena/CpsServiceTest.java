package com.mcxyd.purearena;

import com.mcxyd.purearena.service.CpsService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CPS 统计测试：1 秒滑动窗口、最高值记录、清理。
 */
class CpsServiceTest {

    private final CpsService cps = new CpsService();
    private final UUID a = UUID.randomUUID();

    @Test
    void 一秒内的点击计入CPS() {
        long now = 1_000_000L;
        for (int i = 0; i < 5; i++) {
            cps.registerClick(a, now + i * 10L);
        }
        assertEquals(5, cps.cps(a, now + 50L));
    }

    @Test
    void 超过一秒的点击被移出窗口() {
        long now = 1_000_000L;
        cps.registerClick(a, now);
        cps.registerClick(a, now + 100L);
        // 1.05 秒后第一个点击（1050ms 前）已过期，第二个（950ms 前）仍在窗口内
        assertEquals(1, cps.cps(a, now + 1_050L));
        // 2.5 秒后全部过期
        assertEquals(0, cps.cps(a, now + 2_500L));
    }

    @Test
    void 记录会话最高CPS() {
        long now = 1_000_000L;
        for (int i = 0; i < 8; i++) {
            cps.registerClick(a, now + i);
        }
        // 窗口过期后当前 CPS 归零，但最高值保留
        assertEquals(0, cps.cps(a, now + 5_000L));
        assertEquals(8, cps.maxCps(a));
    }

    @Test
    void registerClick返回当前CPS() {
        long now = 1_000_000L;
        assertEquals(1, cps.registerClick(a, now));
        assertEquals(2, cps.registerClick(a, now + 1L));
    }

    @Test
    void 清理玩家后归零() {
        cps.registerClick(a, 1_000_000L);
        cps.clearPlayer(a);
        assertEquals(0, cps.cps(a, 1_000_000L));
        assertEquals(0, cps.maxCps(a));
    }

    @Test
    void 连续攻击最高值清零但会话最高值保留() {
        long now = 1_000_000L;
        for (int i = 0; i < 4; i++) {
            cps.registerClick(a, now + i);
        }
        assertEquals(4, cps.actionMaxCps(a));
        assertEquals(4, cps.maxCps(a));

        cps.clearActionMaxCps(a);
        assertEquals(0, cps.actionMaxCps(a));
        assertEquals(4, cps.maxCps(a));
    }
}
