/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** 一条待办事项。
 * @since ArkPets 3.13
 */
public class TodoItem {
    public String id;
    public String title;
    public boolean done;
    /** 截止日期，格式 yyyy-MM-dd，可为空。 */
    public String dueDate;
    /** 创建时间，ISO-8601 字符串。 */
    public String createdAt;

    public TodoItem() {
    }

    public TodoItem(String id, String title) {
        this.id = id;
        this.title = title;
        this.done = false;
        this.createdAt = java.time.Instant.now().toString();
    }
}
