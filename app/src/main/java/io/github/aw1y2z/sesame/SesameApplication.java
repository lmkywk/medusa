package io.github.aw1y2z.sesame;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.aw1y2z.sesame.data.RunType;
import io.github.aw1y2z.sesame.data.ViewAppInfo;

/**
 * 模块自身 App 进程的 Application。
 * <p>
 * libxposed / LSPosed 在本模块「已被 LSPosed 启用」时，会通过模块 APK 内的 XposedProvider
 * 把 XposedService 推送到本进程；收到 onServiceBind 即代表已启用，
 * 与支付宝是否运行、是否加载配置都无关。
 */
public class SesameApplication extends Application implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
        // 4.0.0：数据 schema 迁移在模块进程后台跑，失败不影响启动；注入进程读到的旧字段都有默认值
        Thread migration = new Thread(() -> {
            try {
                io.github.aw1y2z.sesame.data.migration.DataMigration.runForCurrentAccount(false);
            } catch (Throwable ignored) {
            }
            try {
                // 白天打开 App 时若当天摘要还没生成，这里补一次（AI 未开启会直接返回）
                io.github.aw1y2z.sesame.data.ai.AiDailySummary.maybeRunAsync(null);
            } catch (Throwable ignored) {
            }
        }, "medusa-migration");
        migration.setDaemon(true);
        migration.start();
    }

    @Override
    public void onServiceBind(XposedService service) {
        android.util.Log.i("SesameX", "onServiceBind framework=" + service.getFrameworkName()
                + " scope=" + service.getScope());
        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode());
    }

    @Override
    public void onServiceDied(XposedService service) {
        android.util.Log.i("SesameX", "onServiceDied");
        ViewAppInfo.setRunTypeByCode(RunType.DISABLE.getCode());
    }
}
