package io.github.aw1y2z.sesame.util;

import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;

public class PermissionUtil {
    private static final String TAG = AntForestRpcCall.class.getSimpleName();

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static final String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
    };

    public static Boolean checkOrRequestAllPermissions(AppCompatActivity activity) {
        return checkOrRequestFilePermissions(activity) && checkOrRequestAlarmPermissions(activity);
    }

    public static boolean checkFilePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //判断是否有管理外部存储的权限
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : PERMISSIONS_STORAGE) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }

    public static Boolean checkOrRequestFilePermissions(AppCompatActivity activity) {
        try {
            if (checkFilePermissions(activity)) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openAllFilesAccessSettings(activity);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    /** 权限已授予且共享目录真的能写：两个条件缺一不可，否则保存必然失败 */
    public static boolean isSharedStorageWritable(Context context) {
        return checkFilePermissions(context) && FileUtil.isMainDirectoryWritable();
    }

    /** 权限/写入状态的人类可读描述，直接显示在首页和设置页 */
    public static String getStorageStatusText(Context context) {
        if (!checkFilePermissions(context)) {
            return "未授予「所有文件访问权限」，配置与日志无法读写";
        }
        if (!FileUtil.isMainDirectoryWritable()) {
            String detail = FileUtil.getLastWriteError();
            return "权限已授予，但共享目录仍不可写" + (StringUtil.isEmpty(detail) ? "" : "（" + detail + "）");
        }
        return "已授权，共享目录可写";
    }

    /** 打开「所有文件访问权限」系统页；个别 ROM 没有应用级入口时退回总入口 */
    public static void openAllFilesAccessSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        appIntent.setData(Uri.parse("package:" + context.getPackageName()));
        try {
            context.startActivity(appIntent);
        } catch (ActivityNotFoundException ex) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException ex2) {
                Log.err(TAG, "打开所有文件访问权限页失败", ex2);
            }
        }
    }

    public static boolean checkAlarmPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Throwable e) {
            // 必须 catch Throwable：模块 App 自己的进程里没有 libxposed，触达 ApplicationHook
            // 抛的是 NoClassDefFoundError（Error），catch Exception 接不住会把界面搞崩
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //判断是否有使用闹钟的权限
            AlarmManager systemService = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (systemService != null) {
                return systemService.canScheduleExactAlarms();
            }
            return true;
        }
        return true;
    }

    public static Boolean checkOrRequestAlarmPermissions(Context context) {
        try {
            if (checkAlarmPermissions()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                try {
                    context.startActivity(appIntent);
                } catch (ActivityNotFoundException ex) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    public static boolean checkBatteryPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Throwable e) {
            // 同 checkAlarmPermissions：ApplicationHook 在 App 进程里会抛 Error，必须 catch Throwable
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //判断是否有始终在后台运行的权限
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(ClassUtil.PACKAGE_NAME);
            }
            return true;
        }
        return true;
    }

    public static Boolean checkOrRequestBatteryPermissions(Context context) {
        try {
            if (context == null) {
                return false;
            }
            if (checkBatteryPermissions()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                context.startActivity(appIntent);
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
}
