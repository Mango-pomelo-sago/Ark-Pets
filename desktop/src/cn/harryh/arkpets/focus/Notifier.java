/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import cn.harryh.arkpets.ArkHomeFX;
import cn.harryh.arkpets.tray.HostTray;
import cn.harryh.arkpets.utils.Logger;
import cn.harryh.arkpets.utils.NotificationSound;

import java.awt.Toolkit;


/** 统一的提醒发出方式：提示音 + 每一只桌宠头顶的气泡。
 * <p>
 * 气泡由桌宠进程自己绘制（见 {@code cn.harryh.arkpets.tray.TipWindow}），因此即使把启动器窗口
 * 收进托盘，提醒依然可见。
 * @since ArkPets 3.13
 */
public final class Notifier {
    private Notifier() {
    }

    /** 发出一则提醒。可在任意线程调用。
     * <p>
     * 注意：提醒不会再出现在启动器窗口内部；若此刻没有任何桌宠在运行，则只有提示音。
     * @param app 绑定的启动器应用，用于读取提示音设置。
     * @param text 提醒文本。
     */
    public static void alert(ArkHomeFX app, String text) {
        Logger.info("Notifier", "Tip: " + text);
        playSound(app);
        try {
            HostTray.getInstance().forEachMemberTray(tray -> tray.showTip(text));
        } catch (Throwable ex) {
            Logger.error("Notifier", "Failed to deliver the tip to ArkPets, details see below.", ex);
        }
    }

    /** 播放提醒音效。
     * <p>
     * 音效被关掉时保持安静；音效开着但播不出来（没有声卡、格式不支持）时退回系统提示音，
     * 保证提醒在任何机器上都「响得了」。
     */
    private static void playSound(ArkHomeFX app) {
        boolean enabled = app == null || app.config == null || app.config.notify_sound_enabled;
        if (!enabled)
            return;
        if (app != null && app.config != null)
            NotificationSound.setVolume(app.config.notify_sound_volume);
        if (NotificationSound.play())
            return;
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // 系统无蜂鸣设备时忽略。
        }
    }
}
