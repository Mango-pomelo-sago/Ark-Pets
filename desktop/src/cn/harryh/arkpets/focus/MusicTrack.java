/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


import java.io.File;


/** 播放列表里的一首本地音频。
 * <p>
 * 只记录路径，不复制文件：这样列表可以指向用户的音乐库而不占额外空间，代价是源文件被移动或
 * 删除后该条目会失效。失效由 {@code MusicPlayer} 在播放时检测并跳过。
 * @since ArkPets 3.13
 */
public class MusicTrack {
    /** 音频文件的绝对路径。 */
    public String path;
    /** 列表中显示的名称，默认取不含扩展名的文件名。可被用户改写。 */
    public String title;

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
