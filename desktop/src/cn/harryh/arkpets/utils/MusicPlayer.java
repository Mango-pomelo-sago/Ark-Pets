/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.utils;

import cn.harryh.arkpets.focus.MusicTrack;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/** "音乐"标签页的播放器：播放用户自己添加的本地音频文件。
 * <p>
 * 解码用 JavaFX Media，而不是 {@code javax.sound} 的 SPI 解码器（mp3spi 之类）。原因是打包方式：
 * {@code distJar} 用 {@code zipTree} 把所有 runtime jar 摊平成一个 fat jar，并且
 * {@code duplicatesStrategy = EXCLUDE}，而全仓库没有任何 {@code META-INF/services} 合并，
 * 于是 SPI 型解码器在摊平时是「先到先得」，不可靠。JavaFX Media 不走 SPI，直接读文件，
 * 绕开了这个问题，代价是只认它能解的格式（MP3 / AAC(M4A) / WAV / AIFF，不支持 OGG / FLAC）。
 * <p>
 * 播放列表只存路径不复制文件，所以条目可能失效（用户移动/删除了源文件）。失效和格式不支持的
 * 处理方式一样：记日志、通知 UI、跳到下一首。为了避免整份列表都坏掉时无限重试，
 * {@link #failuresThisAttempt} 会累计本轮连续失败次数，绕满一整圈就放弃。
 * <p>
 * 播放列表播完会从头再来（{@code floorMod} 绕回），所以「下一首」是有环的，专注时音乐不会断。
 * <p>
 * <b>所有方法都必须在 JavaFX 应用线程上调用</b>，这是 {@link MediaPlayer} 的要求。
 * 唯一的例外是 {@link #release()}，但它实际上也是由 {@code ArkHomeFX.stop()} 在 FX 线程上调用的。
 * @since ArkPets 3.13
 */
public final class MusicPlayer {
    /** 进度回调的间隔。比番茄钟那个 1 秒的 ticker 密，否则进度条会一格一格地跳。 */
    private static final Duration PROGRESS_INTERVAL = Duration.millis(200);

    /** 当前播放列表。它直接就是 {@code UserData.playlist} 那个 List 实例，所以 UI 增删曲目后
     * 播放器立刻看得到，不需要重新 set。 */
    private static List<MusicTrack> playlist = Collections.emptyList();
    private static MediaPlayer player;
    private static Media media;
    /** 当前曲目的下标，-1 表示没有曲目在播放器里。 */
    private static int index = -1;
    private static double volume = 0.8f;
    /** 本轮连续失败了几首。用来给「挨个跳过坏文件」封顶，避免整份列表都失效时无限循环。 */
    private static int failuresThisAttempt;
    /** 当前这首是否已经因为错误被跳过，避免 media / player 的两个 error 回调重复推进。 */
    private static boolean trackFailed;
    private static Timeline progressTicker;

    private static Runnable onStateChanged;
    private static Consumer<String> onError;
    private static Runnable onProgress;

    private MusicPlayer() {
    }

    /** 绑定播放列表。传入的应是 {@code UserData.playlist} 本身，而不是副本。
     */
    public static void setPlaylist(List<MusicTrack> tracks) {
        playlist = tracks == null ? Collections.emptyList() : tracks;
    }

    /** 当前曲目下标，-1 表示没有曲目被载入。
     */
    public static int getIndex() {
        return index;
    }

    /** 是否有曲目正在播放（暂停不算）。
     */
    public static boolean isPlaying() {
        return player != null && player.getStatus() == MediaPlayer.Status.PLAYING;
    }

    /** 当前播放位置；没有曲目时返回 0。
     */
    public static Duration getCurrentTime() {
        return player == null ? Duration.ZERO : player.getCurrentTime();
    }

    /** 当前曲目总时长；未知（尚未载入完成）时返回 {@link Duration#UNKNOWN}。
     */
    public static Duration getTotalDuration() {
        return player == null ? Duration.UNKNOWN : player.getTotalDuration();
    }

    /** 设置播放音量。
     * @param value 0~1，超出范围会被截断。
     */
    public static void setVolume(double value) {
        volume = Math.max(0, Math.min(1, value));
        if (player != null) {
            player.setVolume(volume);
        }
    }

    /** 曲目或播放状态变化时回调，用于刷新 UI。 */
    public static void setOnStateChanged(Runnable callback) {
        onStateChanged = callback;
    }

    /** 播放出错时回调，参数是给用户看的中文提示。 */
    public static void setOnError(Consumer<String> callback) {
        onError = callback;
    }

    /** 播放位置推进时回调，用于刷新进度条。 */
    public static void setOnProgress(Runnable callback) {
        onProgress = callback;
    }

    /** 播放列表中的第 i 首（负数或越界会绕回，因此可以放心传 {@code index ± 1}）。
     * <p>
     * 当前曲目失效时会依次尝试后面的曲目，最多绕列表一圈。这是「用户明确点了某一首」的入口，
     * 因此会重置失败计数。
     */
    public static void playIndex(int i) {
        failuresThisAttempt = 0;
        playInternal(i);
    }

    /** 继续播放；没有载入曲目时从当前下标（或第一首）开始。
     */
    public static void resume() {
        if (player == null) {
            playIndex(index < 0 ? 0 : index);
            return;
        }
        player.play();
        startProgressTicker();
        notifyStateChanged();
    }

    /** 暂停。没有载入曲目时什么都不做。
     */
    public static void pause() {
        if (player == null) {
            return;
        }
        player.pause();
        stopProgressTicker();
        notifyStateChanged();
    }

    /** 播放 / 暂停切换。没有载入曲目时等同于开始播放。 */
    public static void toggle() {
        if (player != null && player.getStatus() == MediaPlayer.Status.PLAYING) {
            pause();
        } else {
            resume();
        }
    }

    /** 下一首。列表为空时什么都不做。 */
    public static void next() {
        if (!playlist.isEmpty()) {
            failuresThisAttempt = 0;
            playInternal(index + 1);
        }
    }

    /** 上一首。列表为空时什么都不做。 */
    public static void previous() {
        if (!playlist.isEmpty()) {
            failuresThisAttempt = 0;
            playInternal(index - 1);
        }
    }

    /** 停止并卸下当前曲目，下标回到 -1。清空播放列表时用。 */
    public static void stop() {
        disposePlayer();
        index = -1;
        failuresThisAttempt = 0;
        notifyStateChanged();
    }

    /** 跳转到指定位置。 */
    public static void seek(Duration position) {
        if (player != null && position != null) {
            player.seek(position);
        }
    }

    /** 释放音频资源。应在启动器退出时调用。 */
    public static void release() {
        disposePlayer();
        index = -1;
        failuresThisAttempt = 0;
        playlist = Collections.emptyList();
        onStateChanged = null;
        onError = null;
        onProgress = null;
    }

    /** 载入并播放第 i 首；这一首有问题就顺延到下一首。
     * <p>
     * 不重置 {@link #failuresThisAttempt}，所以递归必然在绕满一圈后收敛。
     */
    private static void playInternal(int i) {
        if (playlist.isEmpty()) {
            return;
        }
        if (failuresThisAttempt >= playlist.size()) {
            // 绕了一圈都没能播出来，与其无限重试，不如停下来告诉用户。
            disposePlayer();
            index = -1;
            reportError("播放列表里没有可以播放的音频。");
            notifyStateChanged();
            return;
        }
        int current = Math.floorMod(i, playlist.size());
        MusicTrack track = playlist.get(current);
        File file = track == null || track.path == null ? null : new File(track.path);
        if (file == null || !file.isFile()) {
            Logger.warn("Music", "Track file not found, skipped: " + (track == null ? "null" : track.path));
            reportError("找不到文件，已跳过：" + displayName(track));
            failuresThisAttempt++;
            playInternal(current + 1);
            return;
        }

        disposePlayer();
        try {
            // 必须用 File.toURI()，不要手拼 "file://"：中文和空格路径会挂，
            // 而用户的音乐文件名大概率是中文。
            media = new Media(file.toURI().toString());
            MediaPlayer newPlayer = new MediaPlayer(media);
            newPlayer.setVolume(volume);
            newPlayer.setOnEndOfMedia(MusicPlayer::next);
            media.setOnError(() -> onPlaybackFailure(current));
            newPlayer.setOnError(() -> onPlaybackFailure(current));
            // Status transitions are asynchronous: right after play() the player is still READY, and
            // right after pause() it can still read PLAYING. Refreshing the UI only from the calls
            // that *request* a change would therefore leave the play/pause button and the "now
            // playing" marker one state behind. Listen to the player instead and refresh when the
            // change has actually happened.
            newPlayer.setOnPlaying(MusicPlayer::notifyStateChanged);
            newPlayer.setOnPaused(MusicPlayer::notifyStateChanged);
            newPlayer.setOnStopped(MusicPlayer::notifyStateChanged);
            newPlayer.setOnReady(MusicPlayer::notifyStateChanged);
            newPlayer.setOnHalted(MusicPlayer::notifyStateChanged);
            player = newPlayer;
            trackFailed = false;
            index = current;
            newPlayer.play();
            failuresThisAttempt = 0;
            startProgressTicker();
            notifyStateChanged();
        } catch (RuntimeException e) {
            Logger.error("Music", "Failed to open the track, details see below.", e);
            reportError("无法播放，已跳过：" + displayName(track));
            failuresThisAttempt++;
            playInternal(current + 1);
        }
    }

    /** 曲目载入后播放失败（格式不支持、文件损坏、设备被占用等）。回调在 FX 线程上。
     */
    private static void onPlaybackFailure(int failedIndex) {
        // 只处理「当前这一首」的失败：换曲时旧 player / media 的回调已经摘掉，
        // 但仍以防万一，避免迟到的回调把新曲目也带跑偏。
        if (trackFailed || failedIndex != index) {
            return;
        }
        trackFailed = true;
        MusicTrack track = failedIndex >= 0 && failedIndex < playlist.size() ? playlist.get(failedIndex) : null;
        Logger.warn("Music", "Playback failed, skipped: " + (track == null ? "null" : track.path));
        reportError("无法播放，已跳过：" + displayName(track));
        failuresThisAttempt++;
        playInternal(failedIndex + 1);
    }

    private static void disposePlayer() {
        stopProgressTicker();
        if (player != null) {
            // 先摘掉回调，否则 dispose 过程中的状态变化可能再触发一次换曲。
            player.setOnEndOfMedia(null);
            player.setOnError(null);
            // stop() 会同步触发 onStopped，此时 index / player 正处于半更新状态，
            // 让它回调 UI 只会把旧状态刷回去。
            player.setOnPlaying(null);
            player.setOnPaused(null);
            player.setOnStopped(null);
            player.setOnReady(null);
            player.setOnHalted(null);
            player.stop();
            player.dispose();
            player = null;
        }
        if (media != null) {
            media.setOnError(null);
            media = null;
        }
    }

    private static void startProgressTicker() {
        if (progressTicker == null) {
            progressTicker = new Timeline(new KeyFrame(PROGRESS_INTERVAL, e -> {
                if (onProgress != null) {
                    onProgress.run();
                }
            }));
            progressTicker.setCycleCount(Timeline.INDEFINITE);
        }
        progressTicker.play();
    }

    private static void stopProgressTicker() {
        if (progressTicker != null) {
            progressTicker.stop();
        }
    }

    private static void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    private static void reportError(String message) {
        if (onError != null) {
            onError.accept(message);
        }
    }

    private static String displayName(MusicTrack track) {
        return track == null ? "" : track.displayTitle();
    }
}
