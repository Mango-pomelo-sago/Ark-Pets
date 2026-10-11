/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** 一条循环提醒（喝水 / 久坐 / 自定义闹钟）。
 * @since ArkPets 3.13
 */
public class ReminderItem {
    public String id;
    public String name;
    /** 间隔提醒的分钟数，>0 时按间隔循环提醒。 */
    public int intervalMinutes;
    /** 固定每日提醒时间，格式 HH:mm，可为空。非空时按每日定时提醒。 */
    public String dailyTime;
    public boolean enabled;
    /** 上次提醒时间，ISO-8601 字符串，可为空。 */
    public String lastAlertAt;

    public ReminderItem() {
    }

    public ReminderItem(String id, String name, int intervalMinutes, boolean enabled) {
        this.id = id;
        this.name = name;
        this.intervalMinutes = intervalMinutes;
        this.enabled = enabled;
        this.lastAlertAt = java.time.Instant.now().toString();
    }
}
