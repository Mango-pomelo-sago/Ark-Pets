/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import java.util.ArrayList;
import java.util.List;


/** "效率"模块的全部用户数据，持久化为工作目录下的 UserData.json。
 * @since ArkPets 3.13
 */
public class UserData {
    public List<TodoItem> todos = new ArrayList<>();
    public List<ScheduleEvent> schedule = new ArrayList<>();
    public List<ReminderItem> reminders = new ArrayList<>();
    public PomodoroSettings pomodoro = new PomodoroSettings();
    public List<DayStat> stats = new ArrayList<>();

    /** 修正反序列化后可能为 null 的字段，使其可直接使用。 */
    public void normalize() {
        if (todos == null) todos = new ArrayList<>();
        if (schedule == null) schedule = new ArrayList<>();
        if (reminders == null) reminders = new ArrayList<>();
        if (pomodoro == null) pomodoro = new PomodoroSettings();
        if (stats == null) stats = new ArrayList<>();
    }
}
