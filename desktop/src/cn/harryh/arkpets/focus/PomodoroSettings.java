/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** 番茄钟的可持久化设置项。
 * @since ArkPets 3.13
 */
public class PomodoroSettings {
    /** 一次专注的时长（分钟）。 */
    public int workMinutes = 25;
    /** 短休息时长（分钟）。 */
    public int shortBreakMinutes = 5;
    /** 长休息时长（分钟）。 */
    public int longBreakMinutes = 15;
    /** 每完成多少次专注后进入一次长休息。 */
    public int longBreakEvery = 4;
    /** 是否自动循环（专注→休息→专注）。 */
    public boolean autoLoop = false;
}
