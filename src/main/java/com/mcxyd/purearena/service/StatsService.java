package com.mcxyd.purearena.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 击杀统计与淘汰状态。
 * 被计分板任务（各玩家实体线程）与事件线程并发读写，全部使用并发集合。
 * 玩家退出时清理对应条目，集合大小以在线玩家数为上界，不会泄漏。
 * 统计只属于当天：跨天后第一次访问会清空全部数据。
 */
public final class StatsService {

    public record KillEntry(String name, int amount) {
    }

    /** 一次击杀更新的结果，用于在业务层判断是否需要广播新的 MVP。 */
    public record KillResult(boolean newMvp, String name, int amount, int totalKills) {
    }

    private final Map<UUID, KillEntry> kills = new ConcurrentHashMap<>();
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();
    // 记录当前领先者，避免同一 MVP 连续击杀时重复广播。
    private UUID mvpPlayerId;
    // 统计归属的日期；多个实体线程可能同时发现跨天，用 CAS 保证只清空一次
    private final AtomicReference<LocalDate> statsDay = new AtomicReference<>(LocalDate.now());

    /**
     * 跨天检查：statsDay 落后于 today 时清空当天统计。
     * 返回是否发生了清空。可从任意线程调用。
     */
    public synchronized boolean rolloverIfNeeded(LocalDate today) {
        LocalDate current = statsDay.get();
        if (current.equals(today)) {
            return false;
        }
        if (statsDay.compareAndSet(current, today)) {
            kills.clear();
            eliminated.clear();
            mvpPlayerId = null;
            return true;
        }
        // 另一个线程已完成同一次跨天清空
        return false;
    }

    /**
     * 记录一次击杀并原子判断是否产生新的 MVP。
     * 只有击杀数严格超过更新前的最高值才算新的 MVP，平局不重复广播。
     */
    public synchronized KillResult addKill(UUID playerId, String playerName) {
        int previousTop = mvp().map(KillEntry::amount).orElse(0);
        UUID previousMvpId = mvpPlayerId;
        KillEntry updated = kills.merge(playerId, new KillEntry(playerName, 1),
                (oldEntry, ignored) -> new KillEntry(playerName, oldEntry.amount() + 1));
        boolean newMvp = previousMvpId == null
                ? true
                : !playerId.equals(previousMvpId) && updated.amount() > previousTop;
        if (newMvp || previousMvpId == null) {
            mvpPlayerId = playerId;
        }
        int total = kills.values().stream().mapToInt(KillEntry::amount).sum();
        return new KillResult(newMvp, updated.name(), updated.amount(), total);
    }

    /**
     * 启动时合并 SQLite 中当天的历史统计。
     * 当前内存数量代表数据库加载期间产生的新击杀，因此在历史数量上累加而不是覆盖。
     */
    public synchronized void mergePersistedToday(Map<UUID, KillEntry> persisted) {
        for (Map.Entry<UUID, KillEntry> row : persisted.entrySet()) {
            KillEntry stored = row.getValue();
            kills.merge(row.getKey(), stored,
                    (current, ignored) -> new KillEntry(current.name(), current.amount() + stored.amount()));
        }
        mvpPlayerId = null;
        int bestAmount = -1;
        for (Map.Entry<UUID, KillEntry> entry : kills.entrySet()) {
            if (entry.getValue().amount() > bestAmount) {
                bestAmount = entry.getValue().amount();
                mvpPlayerId = entry.getKey();
            }
        }
    }

    public int kills(UUID playerId) {
        KillEntry entry = kills.get(playerId);
        return entry == null ? 0 : entry.amount();
    }

    /** 当天全服累计击杀数，用于计分板 %mvp_amount%。 */
    public synchronized int totalKills() {
        int total = 0;
        for (KillEntry entry : kills.values()) {
            total += entry.amount();
        }
        return total;
    }

    /** 当前击杀数最高的玩家；没有任何击杀时为空。 */
    public synchronized Optional<KillEntry> mvp() {
        if (mvpPlayerId != null) {
            KillEntry current = kills.get(mvpPlayerId);
            if (current != null) {
                return Optional.of(current);
            }
        }
        KillEntry best = null;
        for (KillEntry entry : kills.values()) {
            if (best == null || entry.amount() > best.amount()) {
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    public void markEliminated(UUID playerId) {
        eliminated.add(playerId);
    }

    public boolean isEliminated(UUID playerId) {
        return eliminated.contains(playerId);
    }

    public int eliminatedCount() {
        return eliminated.size();
    }

    /** 剩余存活玩家 = 在线人数 - 已淘汰人数，不允许为负。 */
    public int remainingPlayers(int onlineCount) {
        return Math.max(0, onlineCount - eliminated.size());
    }

    /**
     * 清理玩家的会话状态（淘汰标记）。
     * 当天击杀数据刻意保留：MVP 按当天数据统计，玩家退出重进不清零，
     * 只在跨天 rollover 时清空。
     */
    public void clearSession(UUID playerId) {
        eliminated.remove(playerId);
    }
}
