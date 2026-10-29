package io.github.aw1y2z.sesame.model.task.antOcean;

import io.github.aw1y2z.sesame.util.Log;

/**
 * 4.0.0 god class 试点：把「摸鱼」从 {@link AntOcean} 里拆出来。
 * <p>本类只负责编排（状态 → 主页 → 任务 → 摸鱼），具体动作仍由宿主通过 {@link Port} 提供；
 * 这样拆分不搬动 600 行有状态的实现，失败面最小。旧实现保留为 {@code antfishRunLegacy}，
 * 由 {@code AppConfig.oceanFishSplit} 开关回退。
 */
public final class OceanFishTasks {

    private static final String TAG = "OceanFishTasks";

    private OceanFishTasks() {
    }

    /** 宿主能力：由 AntOcean 以内部类实现，方法体仍是原实现。 */
    public interface Port {
        void migrateBlacklist();

        boolean queryStatus();

        void queryHomePage();

        boolean autoTask();

        void handleTasks();

        void touchfish();
    }

    public static void run(Port port) {
        try {
            port.migrateBlacklist();
            if (!port.queryStatus()) {
                return;
            }
            port.queryHomePage();
            if (port.autoTask()) {
                port.handleTasks();
            }
            port.touchfish();
        } catch (Throwable t) {
            Log.err(TAG, "摸鱼编排失败", t);
        }
    }
}
