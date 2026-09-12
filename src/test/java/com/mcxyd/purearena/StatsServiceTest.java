package com.mcxyd.purearena;

import com.mcxyd.purearena.service.StatsService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 击杀统计与 MVP 逻辑测试。
 */
class StatsServiceTest {

    private final StatsService stats = new StatsService();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void 击杀数累加() {
        assertTrue(stats.addKill(a, "Alice").newMvp());
        assertFalse(stats.addKill(a, "Alice").newMvp());
        assertEquals(2, stats.kills(a));
        assertEquals(0, stats.kills(b));
        assertEquals(2, stats.totalKills());
        assertEquals(3, stats.addKill(b, "Bob").totalKills());
    }

    @Test
    void 没有击杀时没有MVP() {
        assertTrue(stats.mvp().isEmpty());
    }

    @Test
    void MVP为击杀数最高的玩家() {
        stats.addKill(a, "Alice");
        assertFalse(stats.addKill(b, "Bob").newMvp(), "并列最高不应切换 MVP");
        StatsService.KillResult result = stats.addKill(b, "Bob");
        assertTrue(result.newMvp());
        StatsService.KillEntry mvp = stats.mvp().orElseThrow();
        assertEquals("Bob", mvp.name());
        assertEquals(2, mvp.amount());
        assertEquals(3, stats.totalKills());
    }

    @Test
    void 淘汰状态清理不影响击杀数() {
        stats.addKill(a, "Alice");
        stats.markEliminated(a);
        assertEquals(1, stats.eliminatedCount());
        stats.clearSession(a);
        assertEquals(0, stats.eliminatedCount());
        assertFalse(stats.isEliminated(a));
        // 当天击杀数据保留，玩家退出重进后 MVP 不丢失
        assertEquals(1, stats.kills(a));
        assertEquals("Alice", stats.mvp().orElseThrow().name());
    }

    @Test
    void 剩余玩家等于在线人数减去被淘汰人数() {
        stats.markEliminated(a);
        assertEquals(4, stats.remainingPlayers(5));
        // 不允许出现负数
        assertEquals(0, stats.remainingPlayers(0));
    }

    @Test
    void 跨天后统计被清空() {
        stats.addKill(a, "Alice");
        stats.markEliminated(b);
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);
        assertTrue(stats.rolloverIfNeeded(tomorrow));
        assertEquals(0, stats.kills(a));
        assertEquals(0, stats.totalKills());
        assertEquals(0, stats.eliminatedCount());
        assertTrue(stats.mvp().isEmpty());
    }

    @Test
    void 同一天不会清空统计() {
        stats.addKill(a, "Alice");
        assertFalse(stats.rolloverIfNeeded(java.time.LocalDate.now()));
        assertEquals(1, stats.kills(a));
    }

    @Test
    void 同一次跨天只清空一次() {
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);
        assertTrue(stats.rolloverIfNeeded(tomorrow));
        // 第二次调用同一天不再触发清空
        stats.addKill(a, "Alice");
        assertFalse(stats.rolloverIfNeeded(tomorrow));
        assertEquals(1, stats.kills(a));
    }
}
