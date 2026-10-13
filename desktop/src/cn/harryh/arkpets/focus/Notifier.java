/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import cn.harryh.arkpets.ArkHomeFX;
import cn.harryh.arkpets.tray.HostTray;
import cn.harryh.arkpets.utils.Logger;

import java.awt.Toolkit;


/** 统一的提醒发出方式：系统提示音 + 每一只桌宠头顶的气泡。
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
     * @param app 绑定的启动器应用（当前仅留作参数，便于日后改回启动器内提示）。
     * @param text 提醒文本。
     */
    public static void alert(ArkHomeFX app, String text) {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // 系统无蜂鸣设备时忽略。
        }
        Logger.info("Notifier", "Tip: " + text);
        try {
            HostTray.getInstance().forEachMemberTray(tray -> tray.showTip(text));
        } catch (Throwable ex) {
            Logger.error("Notifier", "Failed to deliver the tip to ArkPets, details see below.", ex);
        }
    }
}
