/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.guitasks.requests;

import cn.harryh.arkpets.Const;
import cn.harryh.arkpets.network.SourceStrategy;
import cn.harryh.arkpets.utils.GuiPrefabs;
import cn.harryh.arkpets.utils.Logger;
import cn.harryh.arkpets.utils.StringUtils;
import cn.harryh.arkpets.utils.Version;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import javafx.scene.layout.StackPane;

import java.net.URL;

import static cn.harryh.arkpets.Const.PathConfig.urlRepoApiLatestRelease;
import static cn.harryh.arkpets.Const.appVersion;


/** The task checks whether a newer release than the running build has been published.
 * <p>
 * It queries the GitHub Releases API of this fork itself instead of the upstream version API, so a
 * release of the upstream project can never be offered — and therefore never silently installed —
 * over this fork's build. A repository that has no release yet answers {@code 404}; that code is
 * forgiven and reported through {@link #onAPIFailed()} rather than raised as an error.
 */
public class CheckAppUpdateTask extends FetchAsDataTask {
    /** The GitHub API answers 404 when the repository holds no release at all, which is not an error here. */
    private static final int[] FORGIVE_CODES = {404};
    /** The suffix of the installer asset attached by the release workflow. */
    private static final String SETUP_ASSET_SUFFIX = "-Setup.exe";
    /** The upstream author's GitHub proxy, kept as a mirror in case GitHub itself is unreachable. */
    private static final String GH_PROXY_PREFIX = "https://ghproxy.harryh.cn/";

    public CheckAppUpdateTask(StackPane parent, GuiTaskStyle style) {
        super(parent, style, FORGIVE_CODES);
    }

    protected void onHasNewStableVersion(Version stableVersion) {
    }

    protected void onUpToDated(Version stableVersion) {
    }

    protected void onAPIFailed() {
    }

    @Override
    protected String getHeader() {
        return "正在下载软件版本信息...";
    }

    @Override
    protected URL getTargetURL() {
        return new StringUtils.URLStringBuilder(urlRepoApiLatestRelease).toURL();
    }

    @Override
    protected void onReceivedData(JSONObject json) {
        try {
            String tagName = json.getString("tag_name");
            String assetUrl = findSetupAssetURL(json);
            if (tagName == null || assetUrl == null) {
                // On API failed:
                // Either the repository has no release yet (404), or the newest release has no installer.
                Logger.warn("Checker", "Application version check failed (no usable release found)");
                onAPIFailed();
                return;
            }

            Version stableVersion = new Version(tagName);
            Logger.info("Checker", "Application version check finished, newest: " + stableVersion);
            setAppBackupSource(assetUrl);

            if (appVersion.lessThan(stableVersion)) {
                Const.isUpdateAvailable = true;
                onHasNewStableVersion(stableVersion);
            } else {
                Const.isUpdateAvailable = false;
                onUpToDated(stableVersion);
            }
        } catch (Exception e) {
            // On parsing failed:
            Logger.error("Checker", "Application version check failed unexpectedly, details see below.", e);
            if (style != GuiTaskStyle.HIDDEN)
                GuiPrefabs.Dialogs.createErrorDialog(parent, e).show();
        }
    }

    /** Finds the browser download URL of the installer attached to the given release.
     * @param json The release object returned by the GitHub Releases API.
     * @return The asset URL, or {@code null} if the release carries no installer.
     */
    private static String findSetupAssetURL(JSONObject json) {
        JSONArray assets = json.getJSONArray("assets");
        if (assets == null)
            return null;
        for (int i = 0; i < assets.size(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset == null)
                continue;
            String name = asset.getString("name");
            if (name != null && name.endsWith(SETUP_ASSET_SUFFIX))
                return asset.getString("browser_download_url");
        }
        return null;
    }

    /** Points the app-installer download source at the given release asset.
     * @param assetUrl The installer's direct download URL, taken from the release asset list.
     */
    protected static void setAppBackupSource(String assetUrl) {
        SourceStrategy.getStrategy("AppDownload")
                .clearBackupSource()
                .addBackupSource("GitHub", assetUrl)
                .addBackupSource("GHProxy", GH_PROXY_PREFIX + assetUrl);
    }
}
