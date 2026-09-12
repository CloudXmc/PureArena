package com.mcxyd.purearena.board;

/**
 * 计分板行占位符渲染，纯字符串处理，无状态。
 */
public final class ScoreboardLineRenderer {

    /** 一次刷新用到的全部占位符数据快照。 */
    public record LineContext(String date, String mvpName, int mvpAmount,
                              int remainPlayers, int killAmount, int cps, int maxCps) {
    }

    private ScoreboardLineRenderer() {
    }

    public static String render(String line, LineContext context) {
        if (line == null || line.isEmpty()) {
            return line == null ? "" : line;
        }
        return line
                .replace("%date%", context.date())
                .replace("%mvp_name%", context.mvpName())
                .replace("%mvp_amount%", String.valueOf(context.mvpAmount()))
                .replace("%remain_player%", String.valueOf(context.remainPlayers()))
                .replace("%kill_amount%", String.valueOf(context.killAmount()))
                .replace("%max_cps%", String.valueOf(context.maxCps()))
                .replace("%cps%", String.valueOf(context.cps()));
    }
}
