/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.focus;


/** "音乐"标签页的设置。
 * <p>
 * 与提醒提示音的音量是两套独立音量，语义不同，不要合并：提示音是提醒事件的一次性音效，
 * 音量存在 {@code ArkConfig}（{@code notify_sound_volume}）；音乐是持续播放的媒体，
 * 音量存在这里。
 * @since ArkPets 3.13
 */
public class MusicSettings {
    /** 播放音量，0~1。 */
    public float volume = 0.8f;
    /** 番茄钟进入专注阶段时自动播放、进入休息阶段时自动暂停。 */
    public boolean autoplayWithPomodoro = true;

    public MusicSettings() {
    }
}
