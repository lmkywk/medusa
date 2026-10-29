package io.github.aw1y2z.sesame.util;

import java.util.concurrent.atomic.AtomicBoolean;

/** 3.0：用户停止当前运行的共享标记；桥接层据此拒绝后续写调用。 */
public final class RunControl {

    private static final AtomicBoolean STOP_REQUESTED = new AtomicBoolean(false);

    private RunControl() {
    }

    public static boolean isStopRequested() {
        return STOP_REQUESTED.get();
    }

    public static void requestStop() {
        STOP_REQUESTED.set(true);
    }

    public static void clearStop() {
        STOP_REQUESTED.set(false);
    }
}
