/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** 某一天的专注统计。
 * @since ArkPets 3.13
 */
public class DayStat {
    /** 日期，格式 yyyy-MM-dd。 */
    public String date;
    /** 完成的番茄个数。 */
    public int sessionCount;
    /** 累计专注分钟数。 */
    public int focusMinutes;

    public DayStat() {
    }

    public DayStat(String date) {
        this.date = date;
    }
}
