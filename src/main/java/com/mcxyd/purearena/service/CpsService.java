package com.mcxyd.purearena.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * CPS（每秒点击数）统计：1 秒滑动窗口。
 * 点击事件与计分板刷新都在该玩家的实体线程执行，但为防御跨线程读取仍使用并发结构。
 * 玩家退出时清理，窗口有采样数上限，不会无界增长。
 */
public final class CpsService {

    private static final long WINDOW_MILLIS = 1000L;
    // 防御异常客户端刷点击导致窗口无界增长
    private static final int MAX_SAMPLES = 100;

    private static final class Window {
        final ConcurrentLinkedDeque<Long> clicks = new ConcurrentLinkedDeque<>();
        // 只在该玩家实体线程写入，volatile 保证其他线程可见
        volatile int maxCps;
        volatile int actionMaxCps;
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    /** 记录一次点击并返回当前 CPS。 */
    public int registerClick(UUID playerId, long nowMillis) {
        Window window = windows.computeIfAbsent(playerId, id -> new Window());
        window.clicks.addLast(nowMillis);
        prune(window, nowMillis);
        int cps = window.clicks.size();
        if (cps > window.maxCps) {
            window.maxCps = cps;
        }
        if (cps > window.actionMaxCps) {
            window.actionMaxCps = cps;
        }
        return cps;
    }

    /** 当前 CPS（最近 1 秒内的点击数）。 */
    public int cps(UUID playerId, long nowMillis) {
        Window window = windows.get(playerId);
        if (window == null) {
            return 0;
        }
        prune(window, nowMillis);
        return window.clicks.size();
    }

    /** 本次会话的最高 CPS。 */
    public int maxCps(UUID playerId) {
        Window window = windows.get(playerId);
        return window == null ? 0 : window.maxCps;
    }

    /** 当前连续攻击动作的最高 CPS，停止攻击后由监听器清零。 */
    public int actionMaxCps(UUID playerId) {
        Window window = windows.get(playerId);
        return window == null ? 0 : window.actionMaxCps;
    }

    /** 清零当前连续攻击动作的最高 CPS，但保留本次会话最高 CPS。 */
    public void clearActionMaxCps(UUID playerId) {
        Window window = windows.get(playerId);
        if (window != null) {
            window.actionMaxCps = 0;
        }
    }

    /** 玩家退出时清理。 */
    public void clearPlayer(UUID playerId) {
        windows.remove(playerId);
    }

    private void prune(Window window, long nowMillis) {
        Long first;
        while ((first = window.clicks.peekFirst()) != null
                && (nowMillis - first > WINDOW_MILLIS || window.clicks.size() > MAX_SAMPLES)) {
            window.clicks.pollFirst();
        }
    }
}
