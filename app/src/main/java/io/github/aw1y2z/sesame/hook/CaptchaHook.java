package io.github.aw1y2z.sesame.hook;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.Log;

import java.lang.reflect.Field;

/**
 * 支付宝滑块验证码Hook工具类（UI层拦截）
 * <p>
 * 核心策略：
 * Hook CaptchaDialog.show() - 阻止验证码对话框显示（UI层拦截）
 * <p>
 * 独立开关：
 * - enableCaptchaUIHook：UI层拦截开关（阻止对话框显示）
 * <p>
 * 使用方式：
 * CaptchaHook.setupHook(classLoader)
 * CaptchaHook.updateHooks(enableUI)  // 动态更新开关状态
 *
 * @author ghostxx
 * @since 2025-10-23
 */
public class CaptchaHook {
    
    // 定义静态实例
    public static final CaptchaHook INSTANCE = new CaptchaHook();
    
    // 私有构造方法，确保单例
    private CaptchaHook() {
    }
    
    private static final String TAG = "CaptchaHook";
    
    /**
     * 验证码对话框类名
     */
    private static final String CLASS_CAPTCHA_DIALOG = "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog";
    
    /**
     * UI层Hook卸载器（用于动态控制）
     */
    private static XC_MethodHook.Unhook uiHookUnhook;

    /** 是否自动关闭 VPN/代理类弹窗；探测钩子始终常驻，不受此开关影响。 */
    private static volatile boolean dismissEnabled = true;

    /** 人脸拒绝弹窗会反复 show，日志按 10 秒节流，避免一次重试刷满运行日志。 */
    private static volatile long lastFaceRejectLogAt = 0L;
    
    /**
     * 保存ClassLoader供后续使用
     */
    private static ClassLoader savedClassLoader;
    
    /**
     * 初始化Hook系统
     *
     * @param classLoader 目标应用的ClassLoader
     */
    public static void setupHook(ClassLoader classLoader) {
        savedClassLoader = classLoader;
        Log.i(TAG + "验证码Hook系统初始化完成");
        Log.i(TAG + "⚠️ Hook配置将在配置文件加载后同步");
        
        // 注意：此时配置文件还未加载，不能立即应用Hook
        // 实际的Hook应用会在BaseModel.boot()中进行
    }
    
    /**
     * 动态更新开关状态。
     * <p>探测钩子必须常驻：只有 CaptchaDialog 真的 show 出来，才说明账号被要求验证；
     * 关掉「自动关闭弹窗」不应该连探测一起关掉，否则又得靠 antcaptcha 请求猜。
     */
    public static void updateHooks(boolean enableUI) {
        ClassLoader classLoader = savedClassLoader;
        if (classLoader == null) {
            Log.i("⚠️ ClassLoader未初始化，请先调用setupHook()");
            return;
        }
        
        dismissEnabled = enableUI;
        Log.i(TAG + "📝 更新验证码Hook状态:");
        Log.record(TAG + "  弹窗自动关闭: " + (enableUI ? "✅ 开启" : "⚠️ 关闭") + "；弹窗探测常驻");
        
        if (uiHookUnhook == null) {
            uiHookUnhook = hookCaptchaDialogShowAndClose(classLoader);
        }
    }
    
    /**
     * 拦截逻辑：弹窗真的 show 出来时先通知风控保护，再按开关决定是否关闭 VPN/代理类弹窗。
     */
    private static XC_MethodHook.Unhook hookCaptchaDialogShowAndClose(ClassLoader classLoader) {
        try {
            Class<?> captchaDialogClass = XHelpers.findClass(CLASS_CAPTCHA_DIALOG, classLoader);

            return XHelpers.findAndHookMethod(captchaDialogClass, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object dialogObj = param.thisObject;
                        Dialog dialog = getDialogInstance(dialogObj);
                        if (dialog == null) return;

                        // 收集对话框信息用于判断是否为VPN/代理弹窗
                        StringBuilder dialogAllInfo = new StringBuilder();
                        collectDialogInfo(dialog, dialogAllInfo);
                        String info = dialogAllInfo.toString();

                        // 人脸识别（Zoloz/Toyger）的“访问被拒绝”也是 CaptchaDialog，但不是广告：
                        // 关闭它只会让用户反复看到网络错误，必须保留弹窗让用户看到原因和反馈码。
                        boolean faceReject = info.contains("zoloz") || info.contains("Toyger")
                                || info.contains("访问被拒绝") || info.contains("反馈码");
                        if (faceReject) {
                            long now = System.currentTimeMillis();
                            if (now - lastFaceRejectLogAt > 10_000L) {
                                lastFaceRejectLogAt = now;
                                Log.record("人脸识别被拒绝：请关闭代理软件/VPN 后重试（已保留提示弹窗）");
                            }
                            return;
                        }

                        // 宽松匹配：包含"VPN"或"代理"即视为需拦截的弹窗
                        boolean shelter = info.contains("VPN") || info.contains("代理")
                                || info.contains("人气太旺");
                        if (!shelter) {
                            // 真正的滑块/安全验证界面：账号级待验证，交给风控保护统一处理
                            ApplicationHook.notifyCaptchaDialogShown();
                            return;
                        }
                        if (dismissEnabled) {
                            Log.record("检测到可屏蔽弹窗，自动关闭: " + info.replaceAll("\n", " | "));
                            dialog.dismiss();
                        }
                    } catch (Throwable t) {
                        // 验证码弹窗结构随版本变化，探测失败绝不能影响宿主
                        Log.err(TAG, "验证码弹窗探测失败", t);
                    }
                }
            });
        } catch (Throwable e) {
            Log.i("⚠️ Hook CaptchaDialog.show() 失败");
            Log.printStackTrace(TAG, e);
            return null;
        }
    }
    
    /**
     * 获取Dialog实例（兼容直接实例和反射获取）
     */
    private static Dialog getDialogInstance(Object dialogObj) {
        if (dialogObj instanceof Dialog) {
            return (Dialog) dialogObj;
        }
        // 尝试反射获取内部Dialog实例
        try {
            Field dialogField = dialogObj.getClass().getDeclaredField("mDialog");
            dialogField.setAccessible(true);
            return (Dialog) dialogField.get(dialogObj);
        } catch (Exception e) {
            Log.i("反射获取Dialog实例失败：" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 收集对话框信息（复用原有逻辑）
     */
    private static void collectDialogInfo(Dialog dialog, StringBuilder info) {
        // 获取上下文
        try {
            Field mContextField = dialog.getClass().getSuperclass().getDeclaredField("mContext");
            mContextField.setAccessible(true);
            Context context = (Context) mContextField.get(dialog);
            info.append("所属上下文：").append(context != null ? context.getClass().getName() : "null").append("\n");
        } catch (Exception e) {
            info.append("所属上下文：获取失败 - ").append(e.getMessage()).append("\n");
        }
        
        // 系统标准控件信息
        TextView titleView = dialog.findViewById(android.R.id.title);
        info.append("系统标题：").append(titleView != null ? titleView.getText().toString().trim() : "无").append("\n");
        
        TextView messageView = dialog.findViewById(android.R.id.message);
        info.append("系统消息：").append(messageView != null ? messageView.getText().toString().trim() : "无").append("\n");
        
        // 收集所有TextView内容
        info.append("===== 自定义布局文本 =====\n");
        View rootView = dialog.getWindow().getDecorView().getRootView();
        collectAllTextViewText(rootView, info);
    }
    
    public static void collectAllTextViewText(View rootView, StringBuilder info) {
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                collectAllTextViewText(viewGroup.getChildAt(i), info);
            }
        } else if (rootView instanceof TextView) {
            TextView textView = (TextView) rootView;
            String text = textView.getText().toString().trim();
            if (!text.isEmpty()) {
                info.append("TextView：").append(text).append("\n");
            }
        }
    }
    
    
}
