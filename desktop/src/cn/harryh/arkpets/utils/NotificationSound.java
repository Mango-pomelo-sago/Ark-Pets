/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.utils;

import cn.harryh.arkpets.Const;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


/** 提醒提示音的播放器：播放内置的风铃音效。
 * <p>
 * 音效是 16 位 PCM WAV，所以只用到 {@code java.desktop} 里的 Java Sound，不需要任何第三方解码器，
 * 也不需要改动打包时的 jlink 模块表。{@link Clip} 首次使用时懒加载，之后复用同一个实例，
 * 因此重复提醒可以立刻从头重播。
 * <p>
 * 所有方法都可以在任意线程调用。
 * @since ArkPets 3.13
 */
public final class NotificationSound {
    /** 音量换算成分贝时的下限，用来把 0 音量映射成「几乎无声」而不是负无穷。 */
    private static final float MIN_VOLUME = 0.001f;

    private static Clip clip;
    /** 音效加载失败后不再反复重试。 */
    private static boolean unavailable = false;
    private static float volume = 0.8f;

    private NotificationSound() {
    }

    /** 设置播放音量，并立即作用于已加载的音效。
     * @param value 0~1，超出范围会被截断。
     */
    public static synchronized void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (clip != null)
            applyVolume(clip);
    }

    /** 提前加载音效，避免第一次提醒时才产生加载延迟。可在后台线程调用。
     */
    public static void preload() {
        ensureLoaded();
    }

    /** 播放一次音效；若上次的还没播完，会从头重播。
     * @return 是否成功播放。{@code false} 表示音效不可用（无音频设备、格式不支持等），
     *         调用方可以改用它自己的提醒方式。
     */
    public static boolean play() {
        synchronized (NotificationSound.class) {
            if (ensureLoaded() == null)
                return false;
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
            return true;
        }
    }

    /** 释放音频资源。应在启动器退出时调用。
     */
    public static synchronized void release() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    /** 懒加载音效；失败只记一次日志，之后不再重试。
     */
    private static synchronized Clip ensureLoaded() {
        if (clip != null || unavailable)
            return clip;
        try (InputStream stream = new BufferedInputStream(Objects.requireNonNull(
                NotificationSound.class.getResourceAsStream(Const.notifySoundInternal)))) {
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(stream)) {
                Clip newClip = AudioSystem.getClip();
                newClip.open(audio);
                clip = newClip;
                applyVolume(clip);
                Logger.debug("Sound", "Notification sound loaded, format: " + audio.getFormat());
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | NullPointerException e) {
            unavailable = true;
            Logger.error("Sound", "Failed to load the notification sound, details see below.", e);
        }
        return clip;
    }

    /** 把 0~1 的音量换算成分贝写进 {@code MASTER_GAIN}。声卡不支持音量控制时保持原样。
     */
    private static void applyVolume(Clip clip) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
            return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float decibel = (float) (20 * Math.log10(Math.max(volume, MIN_VOLUME)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibel)));
        } catch (IllegalArgumentException e) {
            Logger.warn("Sound", "Failed to apply the notification sound volume: " + e.getMessage());
        }
    }
}
