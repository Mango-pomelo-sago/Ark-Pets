# 维护本 fork：同步上游 与 发版

本仓库是 [isHarryh/Ark-Pets](https://github.com/isHarryh/Ark-Pets) 的 fork，在 `v3.x` 之上叠加了
效率模块（番茄钟 / 待办 / 日程 / 提醒 / 统计）、桌宠头顶气泡与提示音效等自定义功能。

与上游的取舍是：**程序内的自动更新只认本仓库的 Release，官方版本永远不会被自动装上**；上游的代码
改动通过 `git merge` 人工并进来，随下一次发版一起分发。

## 一次性设置

```bash
git remote add upstream https://github.com/isHarryh/Ark-Pets.git
```

`origin` = 本 fork（push 目标），`upstream` = 官方仓库（只 fetch，不 push）。

## 同步上游

```bash
git fetch upstream
git merge upstream/v3.x          # 或 git rebase upstream/v3.x
```

合并时的预期：

- **新增文件不会冲突。** 本 fork 的功能大多是独立新文件（`desktop/.../controllers/EfficiencyModule.java`、
  `core/.../tray/TipWindow.java`、`desktop/.../utils/NotificationSound.java`、`desktop/.../focus/` 整个包）。
- **可能冲突的共享文件**：`Const.java`、`ArkConfig.java`、`ArkHomeFX.java`、`RootModule.java`、
  `assets/UI/RootModule.fxml`、`SocketData.java`、`MemberTray*.java`。本 fork 在这些文件里的改动都是几行到几十行，
  逐个手动合并即可。
- 合并完先编译，再实跑一次启动器，确认桌宠能正常启动、提醒能弹出气泡。

> 注意：`Const.PathConfig` 里指向本 fork 的几条 URL（`urlRepoApiLatestRelease`、
> `urlOfficialDownloadPage`、`urlRepoPage`、`urlRepoReadmePage`）是**故意偏离上游**的，
> 合并时如果上游改了这几行，要保留 fork 的值。

## 发版流程

版本号散落在 **三处**，必须同步修改，否则 CI 的版本一致性校验会直接让构建失败：

| 位置 | 作用 |
|---|---|
| `build.gradle:22` `version` | jar / zip 名、jpackage `--app-version` |
| `core/src/cn/harryh/arkpets/Const.java` `appVersion` | 运行时与 Release tag 比较 |
| `docs/scripts/ExePacking.iss` `MyAppVersion` | 安装包文件名 `ArkPets-v<版本>-Setup.exe` |

`Version` 类只支持三段整数（`MAJOR.MINOR.PATCH`），且 fork 的版本号**必须永远高于上游**，
否则已安装的机器会认为自己比"最新版"还新、永远收不到更新。合并了上游的 `3.14.0` 之后，
fork 就要发 `3.14.1` 或 `3.15.0`。

步骤：

```bash
# 1. 改上面三处版本号，提交
# 2. 推分支可先看构建是否变绿（只出 Artifact，不发 Release）
git push origin v3.x

# 3. 打 tag 才会发布 Release
git tag v3.13.2
git push origin v3.13.2
```

CI 会做三件事：校验三处版本号与 tag 一致 → `gradle clean distAll` → 用 `gh release create`
把 `ArkPets-v<版本>-Setup.exe` 与 zip 挂到 Release 上。之后所有已安装该版本的机器，
下次启动就会自己发现并一键更新。

## 陷阱

- **别复用 tag。** Release 是按 tag 名定位的；同一个 tag 重新推送不会覆盖已发布的附件。
  重跑构建任务时，`gh release upload --clobber` 那段兜底会覆盖附件，但前提是 tag 没变。
- **`[InstallDelete]` 必须清掉旧主 jar。** 主 jar 以版本号命名（`ArkPets-v3.13.2.jar`），
  所以 `ExePacking.iss` 里用的是 `{app}\app\*.jar` 通配符。若写死成某个版本名，
  每次更新都会在安装目录里残留一个约 78 MB 的死 jar。
- **不要手动运行官方安装包。** 本 fork 与官方共用同一个 `AppId`（`{{213DB689-…}}`）与安装目录，
  手动装官方版会把 fork 整个覆盖掉。
- **`ChangesEnvironment` 那段 `[Code]` 是上游遗留。** 它往 `HKEY_LOCAL_MACHINE` 写 PATH，
  而安装是 `PrivilegesRequired=lowest`（不提权），所以每次安装都会静默失败并写一行日志，
  不影响使用。
