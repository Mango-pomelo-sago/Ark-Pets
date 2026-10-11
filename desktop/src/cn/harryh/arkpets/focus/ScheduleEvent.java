/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** 一条日程（一次性、指定日期的事件）。
 * @since ArkPets 3.13
 */
public class ScheduleEvent {
    public String id;
    /** 日期，格式 yyyy-MM-dd。 */
    public String date;
    /** 时间，格式 HH:mm，可为空（表示当天全天）。 */
    public String time;
    public String title;
    public String note;
    /** 提前提醒的分钟数，0 表示准点提醒。 */
    public int remindBeforeMinutes;
    /** 是否已经提醒过（用于去重，落盘后重启不会重复提醒）。 */
    public boolean alerted;

    public ScheduleEvent() {
    }

    public ScheduleEvent(String id, String date, String time, String title, int remindBeforeMinutes) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.title = title;
        this.remindBeforeMinutes = remindBeforeMinutes;
        this.alerted = false;
    }
}
