/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import cn.harryh.arkpets.ArkHomeFX;
import cn.harryh.arkpets.utils.GuiPrefabs;
import javafx.application.Platform;

import java.awt.Toolkit;

import static cn.harryh.arkpets.Const.durationLong;


/** 统一的提醒发出方式：系统提示音 + 启动器 Toast。
 * @since ArkPets 3.13
 */
public final class Notifier {
    private Notifier() {
    }

    /** 发出一则提醒。可在任意线程调用。
     * @param app 绑定的启动器应用。
     * @param text 提醒文本。
     */
    public static void alert(ArkHomeFX app, String text) {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // 系统无蜂鸣设备时忽略。
        }
        Platform.runLater(() -> {
            if (app != null && app.toast != null) {
                app.toast.showText(text,
                        GuiPrefabs.Icons.getIcon(GuiPrefabs.Icons.SVG_WARNING_ALT, GuiPrefabs.COLOR_WARNING),
                        durationLong);
            }
        });
    }
}
