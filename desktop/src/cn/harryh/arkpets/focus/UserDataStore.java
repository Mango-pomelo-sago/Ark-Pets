/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import cn.harryh.arkpets.utils.IOUtils.FileUtil;
import cn.harryh.arkpets.utils.Logger;
import com.alibaba.fastjson2.JSON;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static cn.harryh.arkpets.Const.charsetDefault;
import static com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat;


/** 读写"效率"模块的用户数据文件（UserData.json）。
 * 与 ArkConfig 一样，文件位于程序的工作目录。
 * @since ArkPets 3.13
 */
public final class UserDataStore {
    private static final File dataFile = new File("UserData.json");
    private static UserData instance;

    private UserDataStore() {
    }

    /** 获取内存中的用户数据单例，必要时从文件加载。
     */
    public static synchronized UserData get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** 将内存中的用户数据写回文件。
     */
    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        try {
            FileUtil.writeString(dataFile, charsetDefault, JSON.toJSONString(instance, PrettyFormat), false);
            Logger.debug("Focus", "User data saved");
        } catch (IOException e) {
            Logger.error("Focus", "User data saving failed, details see below.", e);
        }
    }

    /** 生成一个新的随机 ID。
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private static UserData load() {
        UserData data = null;
        if (dataFile.exists()) {
            try {
                data = JSON.parseObject(FileUtil.readString(dataFile, charsetDefault), UserData.class);
            } catch (Exception e) {
                // 文件损坏：备份后重建，避免直接崩溃。
                Logger.warn("Focus", "User data is broken, a new one will be generated. Details: " + e);
                try {
                    FileUtil.writeString(new File("UserData.json.bak"), charsetDefault,
                            FileUtil.readString(dataFile, charsetDefault), false);
                } catch (IOException ignored) {
                }
                data = null;
            }
        }
        boolean created = false;
        if (data == null) {
            data = new UserData();
            created = true;
        }
        data.normalize();
        if (created && data.reminders.isEmpty()) {
            seedDefaultReminders(data);
        }
        instance = data;
        if (created) {
            save();
        }
        return data;
    }

    private static void seedDefaultReminders(UserData data) {
        data.reminders.add(new ReminderItem(newId(), "喝水", 60, true));
        data.reminders.add(new ReminderItem(newId(), "久坐起身", 45, false));
    }
}
