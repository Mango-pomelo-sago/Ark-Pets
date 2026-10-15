/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.network;

import cn.harryh.arkpets.Const;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.harryh.arkpets.Const.httpTimeoutDefault;


/** 访问 B 站公开接口，用视频链接换取可下载的音频地址。
 * <p>
 * 全程匿名，不做登录、不参与 WBI 签名，也不绕过任何访问限制。只支持<b>单个视频</b>（多 P 时每
 * 一 P 各作一首），不支持合集 / 歌单。
 * <p>
 * 两个容易踩的点：
 * <ul>
 *   <li>这些接口<b>出错时也返回 HTTP 200</b>，真正的结果在 JSON 的 {@code code} 字段里。
 *       所以判错必须看 {@code code}，不能看 HTTP 状态码。</li>
 *   <li>拉取 CDN 上的音频必须带 {@code Referer} 与浏览器 {@code User-Agent}，否则返回 403。
 *       请求头必须在连接建立<b>之前</b>设置，因此这里走
 *       {@link Connections#createHttpConnection} 而不是老的几个重载。</li>
 * </ul>
 * @since ArkPets 3.13
 */
public final class BiliApi {
    private static final String URL_VIEW = "https://api.bilibili.com/x/web-interface/view";
    private static final String URL_PLAYURL = "https://api.bilibili.com/x/player/playurl";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    /** 请求 B 站接口与 CDN 时统一附加的请求头。 */
    private static final Map<String, String> BROWSER_HEADERS;

    static {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://www.bilibili.com");
        BROWSER_HEADERS = Collections.unmodifiableMap(headers);
    }

    private static final Pattern BV_PATTERN = Pattern.compile("BV[0-9A-Za-z]{10}");
    private static final Pattern AV_PATTERN = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_PATTERN = Pattern.compile("[?&]p=(\\d+)");

    private BiliApi() {
    }


    /** 链接无法处理时抛出，{@link #getMessage()} 是可以直接展示给用户的中文说明。 */
    public static class BiliException extends IOException {
        public BiliException(String message) {
            super(message);
        }
    }

    /** 解析出来的视频定位。{@code page} 是 1 起的 P 号。 */
    public static class VideoRef {
        public final String bvid;
        public final String aid;
        public final int page;
        /** 链接里是不是明确写了 {@code ?p=N}。决定只导入那一 P，还是把整个视频的每一 P 都导入。 */
        public final boolean pageSpecified;

        VideoRef(String bvid, String aid, int page, boolean pageSpecified) {
            this.bvid = bvid;
            this.aid = aid;
            this.page = page;
            this.pageSpecified = pageSpecified;
        }
    }

    /** 视频的一个分 P。{@code index} 是 1 起的 P 号，{@code cid} 是这一 P 自己的 cid。 */
    public static class Page {
        public final int index;
        public final String cid;
        public final String part;

        Page(int index, String cid, String part) {
            this.index = index;
            this.cid = cid;
            this.part = part;
        }
    }

    /** 一个视频的元信息。 */
    public static class VideoInfo {
        public final String bvid;
        public final String title;
        public final List<Page> pages;

        VideoInfo(String bvid, String title, List<Page> pages) {
            this.bvid = bvid;
            this.title = title;
            this.pages = pages;
        }
    }

    /** 一条可下载的音轨。{@code backups} 是备用源，主源失败时可以挨个试。 */
    public static class AudioSource {
        public final String url;
        public final List<String> backups;
        public final int bandwidth;

        AudioSource(String url, List<String> backups, int bandwidth) {
            this.url = url;
            this.backups = backups;
            this.bandwidth = bandwidth;
        }
    }


    /** 从用户输入里解析出要处理哪个视频、哪一 P。
     * <p>
     * 接受整条链接、裸 {@code BV...} 号、{@code av...} 号，以及 {@code b23.tv} 短链
     * （短链会先跟随重定向拿到真实链接）。
     * @param input 用户粘贴的原始文本。
     * @return 解析结果。
     * @throws BiliException 输入里找不到可识别的视频号。
     */
    public static VideoRef parseLink(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new BiliException("请先粘贴一个 B 站视频链接。");
        }
        String text = input.trim();

        // 短链：它本身不含视频号，必须先跟随重定向落到真实链接上。
        if (text.startsWith("http://") || text.startsWith("https://")) {
            if (isShortLink(text)) {
                text = followRedirect(text);
            }
        }

        Matcher bv = BV_PATTERN.matcher(text);
        Matcher av = AV_PATTERN.matcher(text);
        String bvid = bv.find() ? bv.group() : null;
        String aid = bvid == null && av.find() ? av.group(1) : null;
        if (bvid == null && aid == null) {
            throw new BiliException("没有从这段文字里认出视频号，请粘贴完整的视频链接。");
        }

        int page = 1;
        boolean pageSpecified = false;
        Matcher p = PAGE_PATTERN.matcher(text);
        if (p.find()) {
            pageSpecified = true;
            try {
                page = Math.max(1, Integer.parseInt(p.group(1)));
            } catch (NumberFormatException ignored) {
                // 超出 int 范围的 P 号当作第 1 P，后面越界检查会兜住。
            }
        }
        return new VideoRef(bvid, aid, page, pageSpecified);
    }

    /** 取视频标题与分 P 列表。
     * @param ref {@link #parseLink} 的结果。
     * @return 视频元信息，{@code pages} 至少有一项。
     * @throws BiliException 视频不存在、链接无效或触发风控。
     */
    public static VideoInfo fetchVideo(VideoRef ref) throws IOException {
        String query = ref.bvid != null ? "bvid=" + ref.bvid : "aid=" + ref.aid;
        JSONObject data = requestData(URL_VIEW + "?" + query);

        String bvid = data.getString("bvid");
        String title = data.getString("title");
        List<Page> pages = new ArrayList<>();

        JSONArray rawPages = data.getJSONArray("pages");
        if (rawPages != null && !rawPages.isEmpty()) {
            for (int i = 0; i < rawPages.size(); i++) {
                JSONObject raw = rawPages.getJSONObject(i);
                // 每个分 P 有它自己的 cid。用顶层那个 cid 的话，多 P 视频会把第 1 P 下 N 遍。
                String cid = raw.getString("cid");
                if (cid == null || cid.isBlank()) {
                    continue;
                }
                String part = raw.getString("part");
                pages.add(new Page(raw.getIntValue("page", i + 1),
                        cid, part == null || part.isBlank() ? null : part));
            }
        }
        if (pages.isEmpty()) {
            // 理论上 pages 总在，但结构变了也不该整个失败：退回顶层 cid 当单 P 处理。
            String cid = data.getString("cid");
            if (cid == null || cid.isBlank()) {
                throw new BiliException("这个视频没有可播放的分 P。");
            }
            pages.add(new Page(1, cid, null));
        }
        return new VideoInfo(bvid, title == null ? "" : title, pages);
    }

    /** 取某一 P 的最高码率 AAC 音轨地址。
     * <p>
     * 返回的地址<b>带签名且会过期</b>，拿到就该立刻下载，不能存起来稍后再用。
     * @param bvid 视频号。
     * @param cid 这一 P 自己的 cid。
     * @return 主源与备用源。
     * @throws BiliException 没有可用的 AAC 音轨（例如仅大会员可听、或只有 FLAC / 杜比）。
     */
    public static AudioSource fetchAudio(String bvid, String cid) throws IOException {
        String url = URL_PLAYURL + "?bvid=" + bvid + "&cid=" + cid + "&fnval=16&fnver=0&fourk=1";
        JSONObject data = requestData(url);

        JSONObject dash = data.getJSONObject("dash");
        JSONArray audios = dash == null ? null : dash.getJSONArray("audio");
        if (audios == null || audios.isEmpty()) {
            throw new BiliException("这个视频没有可直接下载的音频轨道（可能需要登录或会员）。");
        }

        JSONObject best = null;
        for (int i = 0; i < audios.size(); i++) {
            JSONObject audio = audios.getJSONObject(i);
            String codecs = audio.getString("codecs");
            // dash 里可能还有 flac / dolby 音轨，JavaFX Media 解不了，必须只挑 mp4a。
            if (codecs == null || !codecs.startsWith("mp4a")) {
                continue;
            }
            if (best == null || audio.getIntValue("bandwidth") > best.getIntValue("bandwidth")) {
                best = audio;
            }
        }
        if (best == null) {
            throw new BiliException("这个视频只有无损音轨，当前播放器不支持。");
        }

        String baseUrl = best.getString("baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BiliException("没有取到音频地址，请稍后再试。");
        }
        List<String> backups = new ArrayList<>();
        JSONArray rawBackups = best.getJSONArray("backupUrl");
        if (rawBackups != null) {
            for (int i = 0; i < rawBackups.size(); i++) {
                String backup = rawBackups.getString(i);
                if (backup != null && !backup.isBlank()) {
                    backups.add(backup);
                }
            }
        }
        return new AudioSource(baseUrl, backups, best.getIntValue("bandwidth"));
    }

    /** 发起一次 GET 并返回响应里的 {@code data} 对象。
     * @param url 完整请求地址。
     * @return {@code data} 字段。
     * @throws BiliException 接口返回了非 0 的 {@code code}。
     */
    private static JSONObject requestData(String url) throws IOException {
        String body;
        HttpURLConnection connection =
                Connections.createHttpConnection(new URL(url), BROWSER_HEADERS, httpTimeoutDefault, httpTimeoutDefault);
        try {
            Connections.raiseForStatus(connection);
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }

        JSONObject json = JSON.parseObject(body);
        if (json == null) {
            throw new BiliException("B 站返回了无法解析的内容，请稍后再试。");
        }
        int code = json.getIntValue("code");
        if (code != 0) {
            throw new BiliException(describeCode(code, json.getString("message")));
        }
        JSONObject data = json.getJSONObject("data");
        if (data == null) {
            throw new BiliException("B 站没有返回视频数据，请稍后再试。");
        }
        return data;
    }

    /** 把接口的 {@code code} 翻译成能直接给用户看的一句话。
     * <p>
     * 不需要提示"稍后再试"以外的处理，所以不保留原始 code 供上层判断。
     */
    private static String describeCode(int code, String message) {
        return switch (code) {
            // -400 既覆盖格式不对的号，也覆盖格式对但不存在的号，所以措辞要能兼容两者。
            case -400 -> "视频号无效，请检查链接是否正确。";
            case -404 -> "找不到这个视频，可能已被删除或链接有误。";
            // 风控。请求本身没毛病，纯粹是短时间内的请求太多，等一会儿再来就好。
            case -412 -> "请求被 B 站拦截了（触发访问限制），请稍后再试。";
            case -352 -> "B 站要求进行安全校验，请稍后再试。";
            case -403 -> "这个视频不允许匿名访问，需要登录后才能在网页端观看。";
            default -> {
                String detail = message == null || message.isBlank() ? "未知错误" : message;
                yield "B 站返回错误：" + detail + "（代码 " + code + "）";
            }
        };
    }

    private static boolean isShortLink(String url) {
        return url.contains("b23.tv") || url.contains("bili2233.cn");
    }

    /** 跟随短链重定向，拿到真实地址。
     * <p>
     * {@link HttpURLConnection} 默认就会自己跟随重定向，所以连接建立完之后 {@code getURL()}
     * 已经是终点地址，不需要手工解析 {@code Location}。
     */
    private static String followRedirect(String url) throws IOException {
        HttpURLConnection connection =
                Connections.createHttpConnection(new URL(url), BROWSER_HEADERS, httpTimeoutDefault, httpTimeoutDefault);
        try {
            // 强制把请求发出去，否则重定向还没有发生，getURL() 仍是短链本身。
            connection.getResponseCode();
            String resolved = connection.getURL().toString();
            return resolved.isBlank() ? url : resolved;
        } finally {
            connection.disconnect();
        }
    }

    /** 常量：下载音频时要带的请求头，供下载任务复用。 */
    public static Map<String, String> browserHeaders() {
        return BROWSER_HEADERS;
    }

    /** 常量：默认超时。 */
    public static int defaultTimeout() {
        return Const.httpTimeoutDefault;
    }
}
