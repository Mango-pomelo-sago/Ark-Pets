/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.guitasks.requests;

import cn.harryh.arkpets.focus.MusicTrack;
import cn.harryh.arkpets.guitasks.GuiTask;
import cn.harryh.arkpets.network.BiliApi;
import cn.harryh.arkpets.network.Connections;
import cn.harryh.arkpets.utils.GuiPrefabs;
import cn.harryh.arkpets.utils.Logger;
import cn.harryh.arkpets.utils.StringUtils;
import javafx.concurrent.Task;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static cn.harryh.arkpets.Const.httpTimeoutDefault;


/** 把一条 B 站视频链接解析成音频，下载到本地目录，并回传可以加进播放列表的曲目。
 * <p>
 * 不复用 {@code FetchAsFileTask}：它的 {@code getTask()} 是 {@code final}，文件名取自
 * {@code Content-Disposition}（B 站不返回这个头，会退化成随机 UUID），而且一次只能下一个文件，
 * 没办法附加请求头。这里复用更低一层的 {@link Connections}。
 * <p>
 * 失败分两类，因为二者的呈现方式完全不同：
 * <ul>
 *   <li><b>预期内的失败</b>（链接不对、视频不存在、触发风控、没有可用音轨）——{@code call()} 里
 *       捕获，返回 {@code false}，通过 {@code onRejected} 回一句中文说明。这类错误用弹窗会显示成
 *       "启动器抛出了一个异常" 加一整段堆栈，对"链接打错了"来说是灾难性的观感。</li>
 *   <li><b>意外失败</b>（网络中断、磁盘写不进）——原样抛出，走 {@code onFailed} 弹错误框。</li>
 * </ul>
 * @since ArkPets 3.13
 */
public class ImportBiliAudioTask extends GuiTask {
    /** 单个音频文件的大小上限。一首 2 小时的 192k 音轨约 170 MB，512 MB 留足余量。 */
    private static final long perFileLengthLimit = 512L << 20;
    /** 开始下载前要求的最小剩余磁盘空间。 */
    private static final long freeSpaceRequired = 256L << 20;
    /** 文件名里保留的标题长度上限（不含后缀）。 */
    private static final int titleLengthLimit = 60;

    private final String link;
    private final File targetDir;
    private final Consumer<List<MusicTrack>> onImported;
    private final Consumer<String> onRejected;
    /** 无论成功、失败还是取消都会调用一次，用来把入口按钮恢复成可点状态。
     * <p>
     * 不能只在成功回调里恢复：失败和取消走的分别是 {@code onFailed} / {@code onCancelled}，
     * 漏掉任何一条，按钮就会永远停在灰掉的状态。*/
    private final Runnable onFinished;

    /** {@code Task.updateMessage} / {@code updateProgress} 是 protected 的，只能从 Task 自己的
     * 代码里调用。这个接口把它们转发出来，好让下载循环不必是 Task 的内部类。*/
    private interface Reporter {
        void message(String text);

        void progress(double workDone, double max);

        boolean isCancelled();
    }

    /** 后台线程写入、{@code onSucceeded} 读取。 */
    private final List<MusicTrack> imported = new ArrayList<>();
    private String rejectedReason;
    /** 当前正在写的临时文件，取消时要删掉。 */
    private volatile Path activePartial;

    public ImportBiliAudioTask(StackPane parent, String link, File targetDir,
                               Consumer<List<MusicTrack>> onImported, Consumer<String> onRejected,
                               Runnable onFinished) {
        super(parent, GuiTaskStyle.COMMON);
        this.link = link;
        this.targetDir = targetDir;
        this.onImported = onImported;
        this.onRejected = onRejected;
        this.onFinished = onFinished;
    }

    @Override
    protected Task<Boolean> getTask() {
        return new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                // 这个匿名类位于 Task 自己的方法体里，所以这里能调用 protected 的
                // updateMessage / updateProgress。
                Reporter reporter = new Reporter() {
                    @Override
                    public void message(String text) {
                        updateMessage(text);
                    }

                    @Override
                    public void progress(double workDone, double max) {
                        updateProgress(workDone, max);
                    }

                    @Override
                    public boolean isCancelled() {
                        // 必须限定到外层字段：不带前缀的 isCancelled() 会解析到 Reporter 自己刚
                        // 声明的这个方法上，变成无限递归。也不能写 Task.this —— 匿名类没有名字。
                        return ImportBiliAudioTask.this.task.isCancelled();
                    }
                };
                BiliApi.VideoRef ref;
                BiliApi.VideoInfo info;
                List<BiliApi.Page> selected;
                try {
                    updateMessage("正在解析链接");
                    ref = BiliApi.parseLink(link);
                    info = BiliApi.fetchVideo(ref);
                    selected = selectPages(ref, info);
                } catch (BiliApi.BiliException e) {
                    rejectedReason = e.getMessage();
                    Logger.warn("Network", "Rejected bilibili link: " + e.getMessage());
                    return false;
                }

                Files.createDirectories(targetDir.toPath());
                long free = targetDir.getFreeSpace();
                if (free > 0 && free < freeSpaceRequired) {
                    rejectedReason = "磁盘剩余空间不足，请清理后再试。";
                    return false;
                }

                for (int i = 0; i < selected.size(); i++) {
                    BiliApi.Page page = selected.get(i);
                    String title = pageTitle(info, page);
                    String pageLabel = selected.size() > 1
                            ? "第 " + (i + 1) + "/" + selected.size() + " 首：" + title
                            : title;
                    updateMessage("正在准备 " + pageLabel);

                    File target = new File(targetDir, buildFileName(info, page, title));
                    if (target.isFile() && target.length() > 0) {
                        // 同一个视频重复导入：文件已经在盘上了，不必再下一遍。
                        Logger.info("Network", "Reusing existing audio file " + target.getName());
                        imported.add(toTrack(target, title, info, page));
                        updateProgress(i + 1, selected.size());
                        continue;
                    }

                    try {
                        downloadPage(reporter, info, page, target, pageLabel, i, selected.size());
                    } catch (InterruptedIOException e) {
                        return false;
                    }
                    imported.add(toTrack(target, title, info, page));
                    updateProgress(i + 1, selected.size());
                }
                return true;
            }
        };
    }

    /** 决定这次要导入哪几 P。链接里写了 {@code ?p=N} 就只导入那一 P，否则整个视频的每一 P 都导入。 */
    private List<BiliApi.Page> selectPages(BiliApi.VideoRef ref, BiliApi.VideoInfo info) throws BiliApi.BiliException {
        if (!ref.pageSpecified) {
            return info.pages;
        }
        if (ref.page > info.pages.size()) {
            throw new BiliApi.BiliException(
                    "这个视频只有 " + info.pages.size() + " P，没有第 " + ref.page + " P。");
        }
        return List.of(info.pages.get(ref.page - 1));
    }

    /** 解析这一 P 的音频地址并下载到 {@code target}。
     * <p>
     * 地址带签名且会过期，所以是「下一首、解析一首」，不是一开始就把所有地址都取好。 */
    private void downloadPage(Reporter reporter, BiliApi.VideoInfo info, BiliApi.Page page,
                              File target, String pageLabel, int index, int total) throws IOException {
        BiliApi.AudioSource source = BiliApi.fetchAudio(info.bvid, page.cid);
        Path part = target.toPath().resolveSibling(target.getName() + ".part");
        activePartial = part;

        // 主源失败就依次试备用源。B 站的主备源常在不同 CDN 上，一个不通另一个往往能通。
        List<String> candidates = new ArrayList<>();
        candidates.add(source.url);
        candidates.addAll(source.backups);

        IOException lastError = null;
        for (String candidate : candidates) {
            try {
                downloadTo(reporter, candidate, part, pageLabel, index, total);
                moveIntoPlace(part, target.toPath());
                activePartial = null;
                Logger.info("Network", "Imported audio -> " + target.getAbsolutePath());
                return;
            } catch (InterruptedIOException e) {
                throw e;
            } catch (IOException e) {
                lastError = e;
                Logger.warn("Network", "Audio source failed, trying the next one. " + e.getMessage());
                deleteQuietly(part);
            }
        }
        activePartial = null;
        throw lastError != null ? lastError : new IOException("No usable audio source");
    }

    private void downloadTo(Reporter reporter, String url, Path part, String pageLabel,
                            int index, int total) throws IOException {
        HttpURLConnection connection = Connections.createHttpConnection(
                new URL(url), BiliApi.browserHeaders(), httpTimeoutDefault, httpTimeoutDefault);
        try {
            Connections.raiseForStatus(connection);
            long fullSize = connection.getContentLengthLong();
            Connections.Recorder recorder = new Connections.Recorder() {
                @Override
                public void receive(int size) throws IOException {
                    super.receive(size);
                    long received = getTotalBytes();
                    if (reporter.isCancelled()) {
                        InterruptedIOException e = new InterruptedIOException("Cancelled importing manually");
                        e.bytesTransferred = (int) received;
                        throw e;
                    }
                    if (received > perFileLengthLimit) {
                        throw new IOException("Exceeded the per-file content length limit");
                    }
                    String bps = getBytesPerSecondString();
                    reporter.message("正在下载 " + pageLabel + "\n" +
                            getTotalBytesString() +
                            (fullSize <= 0 ? "" : " / " + StringUtils.getFormattedSizeString(fullSize)) +
                            ("0".equals(bps) ? "" : "  (" + bps + "/s)"));
                    // 单文件进度折算进整体进度，多 P 时进度条才不会每首重来一次。
                    double fraction = fullSize <= 0 ? 0 : Math.min(0.999, (double) received / fullSize);
                    reporter.progress(index + fraction, total);
                }
            };
            Connections.consume(connection, Files.newOutputStream(part), recorder);
        } finally {
            connection.disconnect();
        }
    }

    /** 下载写的是 {@code .part}，成功后才改成正式名字。
     * <p>
     * 直接写目标名的话，中途失败或取消会留下一个名字正确、内容残缺的文件 —— 它看起来像一首正常
     * 的歌，还能被播放器打开然后报错，而且因为路径已存在，重试时会被去重逻辑挡掉。 */
    private void moveIntoPlace(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private MusicTrack toTrack(File file, String title, BiliApi.VideoInfo info, BiliApi.Page page) {
        MusicTrack track = new MusicTrack(file.getAbsolutePath(), title);
        track.downloaded = true;
        // 这里必须和 buildFileName 用同一个判断依据（视频有几个 P），不能用"这次导入了几个 P"：
        // 否则只导入某一 P 时，文件名带 P 号而来源地址不带，两者对不上。
        track.sourceUrl = "https://www.bilibili.com/video/" + info.bvid
                + (info.pages.size() > 1 ? "?p=" + page.index : "");
        return track;
    }

    /** 显示名称：分 P 用它自己的名字，没有就退回视频标题。 */
    private String pageTitle(BiliApi.VideoInfo info, BiliApi.Page page) {
        if (page.part != null && !page.part.isBlank()) {
            return page.part;
        }
        return info.title == null || info.title.isBlank() ? "P" + page.index : info.title;
    }

    /** 文件名 = 标题 + 视频号，既稳定又唯一。
     * <p>
     * 带上 bvid 是有意的：不同视频常常同名（"Intro" 之类），只按标题命名会让后下的覆盖先下的，
     * 而两个 {@link MusicTrack} 会指向同一个文件 —— 删掉其中一个就会把另一个也弄坏。
     * 视频号只含字母数字，所以这个后缀同时把 Windows 保留名（CON、NUL 之类）也避开了。 */
    private String buildFileName(BiliApi.VideoInfo info, BiliApi.Page page, String title) {
        String base = sanitize(title);
        StringBuilder name = new StringBuilder(base).append(" [").append(info.bvid);
        if (info.pages.size() > 1) {
            name.append(" P").append(page.index);
        }
        return name.append("].m4a").toString();
    }

    /** 去掉 Windows 文件名里非法的字符，并压掉多余空白。 */
    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "audio";
        }
        // 非法字符 + 控制字符一律换为下划线。
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").replaceAll("\\s+", " ").trim();
        // Windows 不接受文件名以点或空格结尾。
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.length() > titleLengthLimit) {
            cleaned = cleaned.substring(0, titleLengthLimit).trim();
        }
        return cleaned.isEmpty() ? "audio" : cleaned;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    @Override
    protected String getHeader() {
        return "正在从 B 站导入音频";
    }

    @Override
    protected String getInitialContent() {
        return "正在解析链接";
    }

    @Override
    protected void onSucceeded(boolean result) {
        try {
            if (result && !imported.isEmpty()) {
                onImported.accept(imported);
            } else if (!result && rejectedReason != null) {
                onRejected.accept(rejectedReason);
            }
        } finally {
            onFinished.run();
        }
    }

    @Override
    protected void onCancelled() {
        try {
            // 取消时把没写完的 .part 清掉，否则会在 music/ 里留下半个文件。
            Path part = activePartial;
            if (part != null) {
                deleteQuietly(part);
            }
            onRejected.accept("已取消导入。");
        } finally {
            onFinished.run();
        }
    }

    @Override
    protected void onFailed(Throwable e) {
        try {
            // 只有真正意料之外的错误才走到这里（网络断了、盘写不进）。预期内的错误已经在
            // call() 里被转成中文提示了，不会弹这个框。
            if (style != GuiTaskStyle.HIDDEN) {
                GuiPrefabs.Dialogs.createErrorDialog(parent, e).show();
            }
        } finally {
            onFinished.run();
        }
    }
}
