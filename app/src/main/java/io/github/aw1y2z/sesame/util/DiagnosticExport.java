package io.github.aw1y2z.sesame.util;

import android.os.Build;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.aw1y2z.sesame.data.ViewAppInfo;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 完整日志包：把 log/ 下的全部分类日志打成一个 zip，便于一次性导出排查。
 * <p>只打包日志，不打包配置/令牌：config_v2.json 可能含 AI Key，profile.json 含账号资料。
 */
public final class DiagnosticExport {

    private DiagnosticExport() {
    }

    public static File export() throws Exception {
        File logDir = FileUtil.LOG_DIRECTORY_FILE;
        if (!logDir.isDirectory()) {
            return null;
        }
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloads.exists() && !downloads.mkdirs()) {
            return null;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File target = new File(downloads, "美杜莎日志_" + stamp + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            File[] files = logDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        addFile(zip, file, file.getName());
                    }
                }
            }
            addText(zip, "环境信息.txt", buildEnvironmentInfo());
        }
        return target;
    }

    private static String buildEnvironmentInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("导出时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("模块版本: ").append(ViewAppInfo.getAppVersion()).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("当前账号: ").append(UserIdMap.getAccountLabel(UserIdMap.getCurrentUid())).append('\n');
        return sb.toString();
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            zip.putNextEntry(new ZipEntry(entryName));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    private static void addText(ZipOutputStream zip, String entryName, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        OutputStreamWriter writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        writer.write(text);
        writer.flush();
        zip.closeEntry();
    }
}
