package com.mcxyd.purearena.util;

/**
 * Folia 环境检测。
 * 反射检测只在首次调用时执行一次，之后使用缓存结果。
 */
public final class FoliaDetector {

    private static Boolean IS_FOLIA;

    private FoliaDetector() {
    }

    public static boolean isFolia() {
        if (IS_FOLIA != null) {
            return IS_FOLIA;
        }

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            IS_FOLIA = true;
        } catch (ClassNotFoundException exception) {
            IS_FOLIA = false;
        }

        return IS_FOLIA;
    }
}
