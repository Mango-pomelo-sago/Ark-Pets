/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import java.io.File;


/** 播放列表里的一首本地音频。
 * <p>
 * 只记录路径，不复制文件：这样列表可以指向用户的音乐库而不占额外空间，代价是源文件被移动或
 * 删除后该条目会失效。失效由 {@code MusicPlayer} 在播放时检测并跳过。
 * <p>
 * 唯一例外是 {@link #downloaded} 为真的条目：那是本程序自己从链接下载到 {@code music/} 里的，
 * 删除该条目时会把磁盘文件一并删掉。用户自己添加的本地文件永远不删 —— 那是别人的文件。
 * @since ArkPets 3.13
 */
public class MusicTrack {
    /** 音频文件的绝对路径。 */
    public String path;
    /** 列表中显示的名称，默认取不含扩展名的文件名。可被用户改写。 */
    public String title;
    /** 这个文件是不是本程序下载来的。只有为真时删除条目才会连带删除磁盘文件。
     * <p>
     * 老版本写下的 {@code UserData.json} 没有这个字段，反序列化后为 {@code false} ——
     * 恰好是安全的那一侧。 */
    public boolean downloaded;
    /** 下载来源（视频页面地址）。仅用于去重与展示，本地添加的曲目为 {@code null}。 */
    public String sourceUrl;

    public MusicTrack() {
    }

    public MusicTrack(String path, String title) {
        this.path = path;
        this.title = title;
    }

    /** 显示名称：优先用 {@link #title}，为空时退回文件名。
     */
    public String displayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (path == null || path.isBlank()) {
            return "";
        }
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
