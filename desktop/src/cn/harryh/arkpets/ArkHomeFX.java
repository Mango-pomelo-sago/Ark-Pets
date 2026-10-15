/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets;

import cn.harryh.arkpets.assets.ModelsDataset;
import cn.harryh.arkpets.concurrent.*;
import cn.harryh.arkpets.controllers.BehaviorModule;
import cn.harryh.arkpets.controllers.EfficiencyModule;
import cn.harryh.arkpets.controllers.ModelsModule;
import cn.harryh.arkpets.controllers.RootModule;
import cn.harryh.arkpets.controllers.SettingsModule;
import cn.harryh.arkpets.tray.HostTray;
import cn.harryh.arkpets.utils.DialogComposer;
import cn.harryh.arkpets.utils.FXMLHelper;
import cn.harryh.arkpets.utils.FXMLHelper.LoadFXMLResult;
import cn.harryh.arkpets.utils.GuiComponents.Toast;
import cn.harryh.arkpets.utils.Logger;
import cn.harryh.arkpets.utils.MusicPlayer;
import cn.harryh.arkpets.utils.NotificationSound;
import cn.harryh.arkpets.utils.SentryHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

import static cn.harryh.arkpets.Const.*;


/** ArkPets Homepage the JavaFX app.
 */
public class ArkHomeFX extends Application {
    public Stage stage;
    public ArkConfig config;
    public ModelsDataset modelsDataset;
    public StackPane body;
    public Toast toast;
    public DialogComposer<ArkHomeFX> dialogs;

    public RootModule rootModule;
    public ModelsModule modelsModule;
    public BehaviorModule behaviorModule;
    public SettingsModule settingsModule;
    public EfficiencyModule efficiencyModule;

    static {
        FontsConfig.REGULAR.loadFontToJavafx();
        FontsConfig.BOLD.loadFontToJavafx();
    }

    @Override
    public void start(Stage stage) throws Exception {
        Logger.info("Launcher", "Starting");
        this.stage = stage;
        Platform.setImplicitExit(false);

        // Load FXML for root node.
        LoadFXMLResult<ArkHomeFX> fxml0 = FXMLHelper.loadFXML(getClass().getResource("/UI/RootModule.fxml"));
        fxml0.initializeWith(this);
        rootModule = (RootModule) fxml0.controller();
        body = rootModule.body;

        // Setup scene and primary stage.
        Logger.info("Launcher", "Creating main scene");
        Scene scene = new Scene(rootModule.rootContainer);
        scene.getStylesheets().setAll(Objects.requireNonNull(getClass().getResource("/UI/Main.css")).toExternalForm());
        scene.setFill(Color.TRANSPARENT);
        stage.getIcons().setAll(new Image(Objects.requireNonNull(getClass().getResource(iconFilePng)).toExternalForm()));
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.setTitle(desktopTitle);
        rootModule.titleText.setText(desktopTitle);

        // After the stage is shown, do initialization.
        stage.show();
        rootModule.popSplashScreen(e -> {
            // Initialize socket server and HostTray.
            try {
                HostTray hostTray = HostTray.getInstance();
                hostTray.setOnCloseStage(() -> Platform.runLater(rootModule::exit));
                hostTray.setOnShowStage(() -> Platform.runLater(stage::show));
                hostTray.setOnShowEfficiency(() -> Platform.runLater(() -> {
                    // 效率页住在主窗口里面，所以先把窗口带出来。show() 对被最小化的窗口不做任何事，
                    // 得显式还原，否则点了「效率工具」看上去毫无反应。
                    if (stage.isIconified())
                        stage.setIconified(false);
                    stage.show();
                    stage.toFront();
                    // 3 = 效率页。走 composer 而不是直接改 wrapper4 的可见性，这样侧栏按钮的高亮
                    // 会跟着一起切，不至于指着一个没显示的页面。
                    rootModule.moduleWrapperComposer.activate(3);
                    // 对话框（公告、日志等）盖在模块区上面，只切页的话用户眼前仍是那个对话框，
                    // 看上去和没点一样。让它们按自己的返回键退场。
                    dialogs.closeActivatedDialogs();
                }));
                SocketServer.getInstance().startServer(hostTray);
                hostTray.applyTrayIcon();
            } catch (PortUtils.NoPortAvailableException ex) {
                Logger.error("SocketServer", "No available port, thus server cannot be started");
                // No HostTray icon will be applied when this situation happens.
            } catch (PortUtils.ServerCollisionException ex) {
                Logger.error("SocketServer", "Server is already running");
                SocketClient socketClient = new SocketClient();
                socketClient.connect(() -> {
                            Logger.info("Launcher", "Request to start an existed Launcher");
                            socketClient.sendRequest(SocketData.ofOperation(UUID.randomUUID(), SocketData.Operation.ACTIVATE_LAUNCHER));
                            socketClient.disconnect();
                        },
                        new SocketClient.ClientSocketSession(socketClient, null));
                // Explicitly cancel the followed initialization in this start method.
                Platform.exit();
                return;
            } catch (Exception ex) {
                Logger.error("Launcher", "Failed to initialize socket server or HostTray, details see below.", ex);
            }

            // Initialize modules.
            Logger.info("Launcher", "Loading modules");
            try {
                LoadFXMLResult<ArkHomeFX> fxml1 = FXMLHelper.loadFXML("/UI/ModelsModule.fxml");
                LoadFXMLResult<ArkHomeFX> fxml2 = FXMLHelper.loadFXML("/UI/BehaviorModule.fxml");
                LoadFXMLResult<ArkHomeFX> fxml3 = FXMLHelper.loadFXML("/UI/SettingsModule.fxml");
                LoadFXMLResult<ArkHomeFX> fxml4 = FXMLHelper.loadFXML("/UI/EfficiencyModule.fxml");
                fxml1.addToNode(rootModule.wrapper1);
                fxml2.addToNode(rootModule.wrapper2);
                fxml3.addToNode(rootModule.wrapper3);
                fxml4.addToNode(rootModule.wrapper4);
                modelsModule = (ModelsModule) fxml1.initializeWith(this);
                behaviorModule = (BehaviorModule) fxml2.initializeWith(this);
                settingsModule = (SettingsModule) fxml3.initializeWith(this);
                efficiencyModule = (EfficiencyModule) fxml4.initializeWith(this);
            } catch (Exception ex) {
                Logger.error("Launcher", "Failed to initialize module, details see below.", ex);
            }

            // Post initialization.
            rootModule.configNetwork();
            rootModule.moduleWrapperComposer.activate(0);
            SentryHelper.setEnable(config.enable_telemetry);
            SentryHelper.consumePendingWal();

            // Warm up the notification sound off the JavaFX thread, so that the first reminder is
            // not delayed by decoding it.
            Thread soundLoader = new Thread(NotificationSound::preload, "notification-sound-loader");
            soundLoader.setDaemon(true);
            soundLoader.start();

            Logger.info("Launcher", "Finished starting");
        }, Duration.ZERO, durationFast);
    }

    @Override
    public void stop() {
        if (config != null && config.launcher_solid_exit) {
            // Notify ArkPets core instances that connected to this app to close.
            HostTray.getInstance().forEachMemberTray(memberTray -> memberTray.sendOperation(SocketData.Operation.LOGOUT));
        }
        SocketServer.getInstance().stopServer();
        ProcessPool.getInstance().shutdown();
        NotificationSound.release();
        MusicPlayer.release();
        Logger.debug("Launcher", "Finished stopping");
        SentryHelper.endDesktopSession();
        SentryHelper.consumePendingWal();
    }

    public void popLoading(EventHandler<ActionEvent> handler) {
        rootModule.popLoading(handler);
    }

    public void popBrowser(URI uri) {
        Logger.info("Launcher", "Request to open URI: " + uri);
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                // File URI
                File localFile = new File(uri);
                if (!localFile.isDirectory())
                    throw new IOException("Given file URI should be a directory");
                SwingUtilities.invokeLater(() -> {
                    try {
                        Desktop.getDesktop().open(localFile);
                    } catch (IOException e) {
                        Logger.error("Launcher", "Failed to open the file URI, details see below.", e);
                    }
                });
            } else {
                // Other types of URI (like HTTP/HTTPS)
                Desktop.getDesktop().browse(uri);
            }
        } catch (IOException e) {
            Logger.error("Launcher", "Failed to open the URI, details see below.", e);
        }
    }

    public void popBrowser(String uri) {
        try {
            popBrowser(new URI(uri));
        } catch (URISyntaxException e) {
            Logger.error("Launcher", "Failed to open URI due to bad URI syntax: " + uri);
        }
    }

    public Window getWindow() {
        return rootModule.root.getScene().getWindow();
    }
}
