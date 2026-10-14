/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.controllers;

import cn.harryh.arkpets.ArkHomeFX;
import cn.harryh.arkpets.focus.DayStat;
import cn.harryh.arkpets.focus.MusicSettings;
import cn.harryh.arkpets.focus.MusicTrack;
import cn.harryh.arkpets.focus.Notifier;
import cn.harryh.arkpets.focus.PomodoroSettings;
import cn.harryh.arkpets.focus.ReminderItem;
import cn.harryh.arkpets.focus.ScheduleEvent;
import cn.harryh.arkpets.focus.TodoItem;
import cn.harryh.arkpets.focus.UserData;
import cn.harryh.arkpets.focus.UserDataStore;
import cn.harryh.arkpets.utils.GuiComponents;
import cn.harryh.arkpets.utils.GuiPrefabs;
import cn.harryh.arkpets.utils.MusicPlayer;
import cn.harryh.arkpets.utils.NotificationSound;
import cn.harryh.arkpets.utils.ScrollUtils;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static cn.harryh.arkpets.Const.durationLong;
import static cn.harryh.arkpets.Const.durationNormal;


/** "效率"模块的控制器：番茄钟 / 待办 / 日程 / 提醒 / 统计 / 音乐。
 * @since ArkPets 3.13
 */
public final class EfficiencyModule implements Controller<ArkHomeFX> {
    @FXML
    private ScrollPane moduleScroll;
    @FXML
    private JFXTabPane efficiencyTabPane;

    // 番茄钟
    @FXML
    private Label pomodoroTimeLabel;
    @FXML
    private Label pomodoroPhaseLabel;
    @FXML
    private HBox pomodoroDots;
    @FXML
    private JFXButton pomodoroStartBtn;
    @FXML
    private JFXButton pomodoroPauseBtn;
    @FXML
    private JFXButton pomodoroResetBtn;
    @FXML
    private JFXSlider pomodoroWorkSlider;
    @FXML
    private Label pomodoroWorkValue;
    @FXML
    private JFXSlider pomodoroShortSlider;
    @FXML
    private Label pomodoroShortValue;
    @FXML
    private JFXSlider pomodoroLongSlider;
    @FXML
    private Label pomodoroLongValue;
    @FXML
    private JFXSlider pomodoroEverySlider;
    @FXML
    private Label pomodoroEveryValue;
    @FXML
    private JFXCheckBox pomodoroAutoLoop;

    // 待办
    @FXML
    private JFXTextField todoInput;
    @FXML
    private JFXButton todoAddBtn;
    @FXML
    private DatePicker todoDueDate;
    @FXML
    private JFXListView<TodoItem> todoList;

    // 日程
    @FXML
    private DatePicker scheduleDate;
    @FXML
    private Spinner<Integer> scheduleHour;
    @FXML
    private Spinner<Integer> scheduleMinute;
    @FXML
    private JFXTextField scheduleTitle;
    @FXML
    private JFXComboBox<String> scheduleRemindBefore;
    @FXML
    private JFXButton scheduleAddBtn;
    @FXML
    private JFXListView<ScheduleEvent> scheduleList;

    // 提醒
    @FXML
    private JFXTextField reminderName;
    @FXML
    private JFXSlider reminderIntervalSlider;
    @FXML
    private Label reminderIntervalValue;
    @FXML
    private JFXCheckBox reminderUseDaily;
    @FXML
    private Spinner<Integer> reminderHour;
    @FXML
    private Spinner<Integer> reminderMinute;
    @FXML
    private JFXButton reminderAddBtn;
    @FXML
    private VBox reminderListBox;

    // 提示音效
    @FXML
    private JFXCheckBox soundEnabled;
    @FXML
    private JFXButton soundTestBtn;
    @FXML
    private JFXSlider soundVolumeSlider;
    @FXML
    private Label soundVolumeValue;

    // 统计
    @FXML
    private Label statsToday;
    @FXML
    private VBox statsWeekBox;

    // 音乐
    @FXML
    private JFXButton musicAddFilesBtn;
    @FXML
    private JFXButton musicAddFolderBtn;
    @FXML
    private JFXButton musicClearBtn;
    @FXML
    private VBox musicListBox;
    @FXML
    private JFXButton musicPrevBtn;
    @FXML
    private JFXButton musicPlayPauseBtn;
    @FXML
    private JFXButton musicNextBtn;
    @FXML
    private JFXSlider musicProgressSlider;
    @FXML
    private Label musicTimeLabel;
    @FXML
    private JFXSlider musicVolumeSlider;
    @FXML
    private Label musicVolumeValue;
    @FXML
    private JFXCheckBox musicAutoplay;

    /** 支持的文件扩展名。JavaFX Media 在 Windows 上只认这些，OGG / FLAC 不在其中。 */
    private static final List<String> musicExtensions = List.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    private ArkHomeFX app;
    private UserData data;
    private Timeline ticker;
    /** 进度条的位置是不是我们自己写进去的，用来区分「用户拖动」和「播放推进」。 */
    private boolean updatingMusicProgress = false;

    /** 番茄钟运行阶段。 */
    private enum PomodoroPhase {IDLE, WORK, SHORT_BREAK, LONG_BREAK}

    private PomodoroPhase phase = PomodoroPhase.IDLE;
    private boolean paused = false;
    private long endMillis = 0;
    private long pausedRemaining = 0;
    private int completedInCycle = 0;

    @Override
    public void initializeWith(ArkHomeFX app) {
        this.app = app;
        this.data = UserDataStore.get();
        initPomodoro();
        initTodo();
        initSchedule();
        initReminder();
        initNotificationSound();
        initStats();
        initMusic();
        startTicker();
        ScrollUtils.addSmoothScrolling(moduleScroll);
        Platform.runLater(() -> GuiPrefabs.disableScrollPaneCache(moduleScroll));
    }

    // ============================================================ 番茄钟

    private void initPomodoro() {
        PomodoroSettings s = data.pomodoro;

        new GuiComponents.SimpleIntegerSliderSetup(pomodoroWorkSlider)
                .setDisplay(pomodoroWorkValue, "%d", "专注时长（分钟）")
                .setRange(1, 90)
                .setTicks(5, 5)
                .setSliderValue(s.workMinutes)
                .setOnChanged((o, ov, nv) -> {
                    s.workMinutes = (int) Math.round(nv.doubleValue());
                    UserDataStore.save();
                    refreshPomodoroIdleLabel();
                });
        new GuiComponents.SimpleIntegerSliderSetup(pomodoroShortSlider)
                .setDisplay(pomodoroShortValue, "%d", "短休息时长（分钟）")
                .setRange(1, 60)
                .setTicks(5, 5)
                .setSliderValue(s.shortBreakMinutes)
                .setOnChanged((o, ov, nv) -> {
                    s.shortBreakMinutes = (int) Math.round(nv.doubleValue());
                    UserDataStore.save();
                });
        new GuiComponents.SimpleIntegerSliderSetup(pomodoroLongSlider)
                .setDisplay(pomodoroLongValue, "%d", "长休息时长（分钟）")
                .setRange(1, 60)
                .setTicks(5, 5)
                .setSliderValue(s.longBreakMinutes)
                .setOnChanged((o, ov, nv) -> {
                    s.longBreakMinutes = (int) Math.round(nv.doubleValue());
                    UserDataStore.save();
                });
        new GuiComponents.SimpleIntegerSliderSetup(pomodoroEverySlider)
                .setDisplay(pomodoroEveryValue, "%d", "每多少次专注后长休息")
                .setRange(1, 8)
                .setTicks(1, 0)
                .setSliderValue(s.longBreakEvery)
                .setOnChanged((o, ov, nv) -> {
                    s.longBreakEvery = (int) Math.round(nv.doubleValue());
                    UserDataStore.save();
                    updateDots();
                });

        pomodoroAutoLoop.setSelected(s.autoLoop);
        pomodoroAutoLoop.setOnAction(e -> {
            s.autoLoop = pomodoroAutoLoop.isSelected();
            UserDataStore.save();
        });

        pomodoroStartBtn.setOnAction(e -> onPomodoroStart());
        pomodoroPauseBtn.setOnAction(e -> onPomodoroPause());
        pomodoroResetBtn.setOnAction(e -> resetPomodoro());

        resetPomodoro();
    }

    private void onPomodoroStart() {
        if (phase == PomodoroPhase.IDLE) {
            startPhase(PomodoroPhase.WORK);
        } else if (paused) {
            resumePhase();
        }
    }

    private void onPomodoroPause() {
        if (phase != PomodoroPhase.IDLE && !paused) {
            pausedRemaining = Math.max(0, endMillis - System.currentTimeMillis());
            paused = true;
            updatePhaseLabel("已暂停");
            syncMusicWithPomodoro(false);
        }
    }

    private void startPhase(PomodoroPhase p) {
        PomodoroSettings s = data.pomodoro;
        phase = p;
        paused = false;
        int minutes = switch (p) {
            case SHORT_BREAK -> s.shortBreakMinutes;
            case LONG_BREAK -> s.longBreakMinutes;
            default -> s.workMinutes;
        };
        endMillis = System.currentTimeMillis() + minutes * 60_000L;
        updateTimeLabel(minutes * 60_000L);
        updatePhaseLabel(phaseText(p));
        updateDots();
        syncMusicWithPomodoro(p == PomodoroPhase.WORK);
    }

    private void resumePhase() {
        paused = false;
        endMillis = System.currentTimeMillis() + pausedRemaining;
        updatePhaseLabel(phaseText(phase));
        syncMusicWithPomodoro(phase == PomodoroPhase.WORK);
    }

    private void resetPomodoro() {
        phase = PomodoroPhase.IDLE;
        paused = false;
        completedInCycle = 0;
        endMillis = 0;
        updatePhaseLabel("未开始");
        refreshPomodoroIdleLabel();
        updateDots();
        // 只暂停不停止，重新开始时能接着放。
        syncMusicWithPomodoro(false);
    }

    /** 一个阶段计时结束时的推进逻辑。 */
    private void finishPhase() {
        PomodoroSettings s = data.pomodoro;
        PomodoroPhase finished = phase;
        if (finished == PomodoroPhase.WORK) {
            completedInCycle++;
            recordStat(s.workMinutes);
            Notifier.alert(app, "专注结束！" + (s.autoLoop ? "开始休息。" : "起来休息一下吧。"));
        } else {
            Notifier.alert(app, "休息结束！" + (s.autoLoop ? "开始下一轮专注。" : "继续保持。"));
        }

        PomodoroPhase next;
        if (finished == PomodoroPhase.WORK) {
            int every = Math.max(1, s.longBreakEvery);
            next = (completedInCycle % every == 0) ? PomodoroPhase.LONG_BREAK : PomodoroPhase.SHORT_BREAK;
        } else {
            next = PomodoroPhase.WORK;
        }

        if (s.autoLoop) {
            startPhase(next);
        } else {
            phase = PomodoroPhase.IDLE;
            paused = false;
            updatePhaseLabel("未开始");
            refreshPomodoroIdleLabel();
            updateDots();
        }
    }

    private void updatePomodoroTick() {
        if (phase == PomodoroPhase.IDLE || paused) {
            return;
        }
        long remaining = endMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            finishPhase();
        } else {
            updateTimeLabel(remaining);
        }
    }

    private void refreshPomodoroIdleLabel() {
        if (phase == PomodoroPhase.IDLE) {
            updateTimeLabel(data.pomodoro.workMinutes * 60_000L);
        }
    }

    private void updateTimeLabel(long millis) {
        long totalSec = Math.max(0, (millis + 999) / 1000);
        pomodoroTimeLabel.setText(String.format("%02d:%02d", totalSec / 60, totalSec % 60));
    }

    private void updatePhaseLabel(String text) {
        pomodoroPhaseLabel.setText(text);
    }

    private void updateDots() {
        pomodoroDots.getChildren().clear();
        int every = Math.max(1, data.pomodoro.longBreakEvery);
        int filled = completedInCycle % every;
        if (completedInCycle > 0 && filled == 0) {
            filled = every;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < every; i++) {
            sb.append(i < filled ? "●" : "○");
        }
        Label dots = new Label(sb.toString());
        dots.setStyle("-fx-font-size: 15px;");
        pomodoroDots.getChildren().add(dots);
    }

    private String phaseText(PomodoroPhase p) {
        return switch (p) {
            case WORK -> "专注中";
            case SHORT_BREAK -> "短休息";
            case LONG_BREAK -> "长休息";
            default -> "未开始";
        };
    }

    private void recordStat(int minutes) {
        String today = LocalDate.now().toString();
        DayStat stat = null;
        for (DayStat d : data.stats) {
            if (today.equals(d.date)) {
                stat = d;
                break;
            }
        }
        if (stat == null) {
            stat = new DayStat(today);
            data.stats.add(stat);
        }
        stat.sessionCount++;
        stat.focusMinutes += minutes;
        UserDataStore.save();
        refreshStats();
    }

    // ============================================================ 待办

    private void initTodo() {
        todoList.setCellFactory(lv -> new TodoCell());
        todoAddBtn.setOnAction(e -> addTodo());
        todoInput.setOnAction(e -> addTodo());
        refreshTodoList();
    }

    private void addTodo() {
        String text = todoInput.getText() == null ? "" : todoInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        TodoItem item = new TodoItem(UserDataStore.newId(), text);
        if (todoDueDate.getValue() != null) {
            item.dueDate = todoDueDate.getValue().toString();
        }
        data.todos.add(item);
        UserDataStore.save();
        todoInput.clear();
        todoDueDate.setValue(null);
        refreshTodoList();
    }

    private void refreshTodoList() {
        todoList.getItems().setAll(data.todos);
    }

    private final class TodoCell extends ListCell<TodoItem> {
        private final HBox root = new HBox(8);
        private final CheckBox done = new CheckBox();
        private final Label title = new Label();
        private final Label due = new Label();
        private final Region spacer = new Region();
        private final JFXButton edit = new JFXButton("编辑");
        private final JFXButton del = new JFXButton("删除");

        TodoCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            due.setStyle("-fx-text-fill: #888;");
            edit.getStyleClass().add("btn-plain-dark");
            del.getStyleClass().add("btn-plain-dark");
            root.getChildren().addAll(done, title, due, spacer, edit, del);

            done.setOnAction(e -> {
                TodoItem item = getItem();
                if (item == null) return;
                item.done = done.isSelected();
                UserDataStore.save();
                applyStyle(item);
            });
            edit.setOnAction(e -> {
                TodoItem item = getItem();
                if (item == null) return;
                TextInputDialog dialog = new TextInputDialog(item.title);
                dialog.setTitle("编辑待办");
                dialog.setHeaderText(null);
                dialog.setContentText("内容：");
                if (app.getWindow() != null) dialog.initOwner(app.getWindow());
                dialog.showAndWait().ifPresent(v -> {
                    String t = v.trim();
                    if (!t.isEmpty()) {
                        item.title = t;
                        UserDataStore.save();
                        refreshTodoList();
                    }
                });
            });
            del.setOnAction(e -> {
                TodoItem item = getItem();
                if (item == null) return;
                data.todos.remove(item);
                UserDataStore.save();
                refreshTodoList();
            });
        }

        @Override
        protected void updateItem(TodoItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            done.setSelected(item.done);
            title.setText(item.title);
            due.setText(item.dueDate == null || item.dueDate.isEmpty() ? "" : "截止 " + item.dueDate);
            applyStyle(item);
            setGraphic(root);
        }

        private void applyStyle(TodoItem item) {
            title.setStyle(item.done ? "-fx-text-fill: #999;" : "");
        }
    }

    // ============================================================ 日程

    private void initSchedule() {
        scheduleRemindBefore.getItems().setAll("不提醒", "提前 5 分钟", "提前 10 分钟", "提前 30 分钟", "提前 1 小时");
        scheduleRemindBefore.getSelectionModel().select(0);
        scheduleDate.setValue(LocalDate.now());
        scheduleHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 9));
        scheduleMinute.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0, 5));
        scheduleList.setCellFactory(lv -> new ScheduleCell());
        scheduleAddBtn.setOnAction(e -> addSchedule());
        refreshScheduleList();
    }

    private int spinnerValue(Spinner<Integer> spinner, int fallback) {
        Integer v = spinner.getValue();
        return v == null ? fallback : v;
    }

    private int remindMinutes() {
        return switch (scheduleRemindBefore.getSelectionModel().getSelectedIndex()) {
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 30;
            case 4 -> 60;
            default -> 0;
        };
    }

    private void addSchedule() {
        String title = scheduleTitle.getText() == null ? "" : scheduleTitle.getText().trim();
        LocalDate date = scheduleDate.getValue();
        if (title.isEmpty() || date == null) {
            app.toast.showText("请填写日程标题并选择日期", durationNormal);
            return;
        }
        String time = String.format("%02d:%02d", spinnerValue(scheduleHour, 9), spinnerValue(scheduleMinute, 0));
        data.schedule.add(new ScheduleEvent(UserDataStore.newId(), date.toString(), time, title, remindMinutes()));
        UserDataStore.save();
        scheduleTitle.clear();
        refreshScheduleList();
    }

    private void refreshScheduleList() {
        List<ScheduleEvent> sorted = new ArrayList<>(data.schedule);
        sorted.sort(Comparator.comparing((ScheduleEvent e) ->
                e.date + " " + (e.time == null || e.time.isEmpty() ? "23:59" : e.time)));
        scheduleList.getItems().setAll(sorted);
    }

    private LocalDateTime eventDateTime(ScheduleEvent e) {
        try {
            LocalDate d = LocalDate.parse(e.date);
            // 全天日程以当天 09:00 作为提醒基准时间。
            LocalTime t = (e.time == null || e.time.isEmpty()) ? LocalTime.of(9, 0) : LocalTime.parse(e.time);
            return LocalDateTime.of(d, t);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private final class ScheduleCell extends ListCell<ScheduleEvent> {
        private final HBox root = new HBox(8);
        private final Label when = new Label();
        private final Label title = new Label();
        private final Region spacer = new Region();
        private final JFXButton del = new JFXButton("删除");

        ScheduleCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            del.getStyleClass().add("btn-plain-dark");
            root.getChildren().addAll(when, title, spacer, del);
            del.setOnAction(e -> {
                ScheduleEvent item = getItem();
                if (item == null) return;
                data.schedule.remove(item);
                UserDataStore.save();
                refreshScheduleList();
            });
        }

        @Override
        protected void updateItem(ScheduleEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            String t = (item.time == null || item.time.isEmpty()) ? "" : " " + item.time;
            when.setText(item.date + t);
            title.setText(item.title + (item.alerted ? "（已提醒）" : ""));
            LocalDateTime at = eventDateTime(item);
            boolean past = at != null && at.isBefore(LocalDateTime.now());
            String color = past ? "#999" : "";
            when.setStyle(color.isEmpty() ? "" : "-fx-text-fill: " + color + ";");
            title.setStyle(color.isEmpty() ? "" : "-fx-text-fill: " + color + ";");
            setGraphic(root);
        }
    }

    // ============================================================ 提醒

    private void initReminder() {
        new GuiComponents.SimpleIntegerSliderSetup(reminderIntervalSlider)
                .setDisplay(reminderIntervalValue, "%d", "提醒间隔（分钟）")
                .setRange(5, 240)
                .setTicks(15, 5)
                .setSliderValue(60);
        reminderHour.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 9));
        reminderMinute.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0, 5));
        reminderUseDaily.setOnAction(e -> updateReminderDailyState());
        updateReminderDailyState();
        reminderAddBtn.setOnAction(e -> addReminder());
        refreshReminders();
    }

    private void updateReminderDailyState() {
        boolean daily = reminderUseDaily.isSelected();
        reminderIntervalSlider.setDisable(daily);
        reminderHour.setDisable(!daily);
        reminderMinute.setDisable(!daily);
    }

    private void addReminder() {
        String name = reminderName.getText() == null ? "" : reminderName.getText().trim();
        if (name.isEmpty()) {
            app.toast.showText("请填写提醒名称", durationNormal);
            return;
        }
        ReminderItem r = new ReminderItem(UserDataStore.newId(), name, 0, true);
        if (reminderUseDaily.isSelected()) {
            r.dailyTime = String.format("%02d:%02d", spinnerValue(reminderHour, 9), spinnerValue(reminderMinute, 0));
        } else {
            r.intervalMinutes = (int) Math.round(reminderIntervalSlider.getValue());
        }
        data.reminders.add(r);
        UserDataStore.save();
        reminderName.clear();
        refreshReminders();
    }

    private void refreshReminders() {
        reminderListBox.getChildren().clear();
        for (ReminderItem r : data.reminders) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            JFXCheckBox enable = new JFXCheckBox(r.name);
            enable.setSelected(r.enabled);
            enable.setOnAction(e -> {
                r.enabled = enable.isSelected();
                UserDataStore.save();
            });
            Label desc = new Label(describe(r));
            desc.setStyle("-fx-text-fill: #888;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            JFXButton del = new JFXButton("删除");
            del.getStyleClass().add("btn-plain-dark");
            del.setOnAction(e -> {
                data.reminders.remove(r);
                UserDataStore.save();
                refreshReminders();
            });
            row.getChildren().addAll(enable, desc, spacer, del);
            reminderListBox.getChildren().add(row);
        }
    }

    private String describe(ReminderItem r) {
        if (r.dailyTime != null && !r.dailyTime.isEmpty()) {
            return "每日 " + r.dailyTime;
        }
        return "每隔 " + r.intervalMinutes + " 分钟";
    }

    // ============================================================ 提示音效

    private void initNotificationSound() {
        soundEnabled.setSelected(app.config.notify_sound_enabled);

        GuiComponents.SliderSetup<Integer> setupVolume = new GuiComponents.SimpleIntegerSliderSetup(soundVolumeSlider);
        setupVolume
                .setDisplay(soundVolumeValue, "%d%%", "提醒提示音音量")
                .setRange(0, 100)
                .setTicks(20, 10)
                .setSliderValue(Math.round(app.config.notify_sound_volume * 100))
                .setOnChanged((observable, oldValue, newValue) -> {
                    float volume = setupVolume.getValidatedValue() / 100f;
                    app.config.notify_sound_volume = volume;
                    app.config.save();
                    NotificationSound.setVolume(volume);
                });

        soundEnabled.selectedProperty().addListener((observable, oldValue, newValue) -> {
            app.config.notify_sound_enabled = newValue;
            app.config.save();
            applySoundDisabledState();
        });
        soundTestBtn.setOnAction(e -> NotificationSound.play());

        NotificationSound.setVolume(app.config.notify_sound_volume);
        applySoundDisabledState();
    }

    /** 关掉提示音后，音量与试听一并变灰。
     */
    private void applySoundDisabledState() {
        boolean disabled = !soundEnabled.isSelected();
        soundVolumeSlider.setDisable(disabled);
        soundVolumeValue.setDisable(disabled);
        soundTestBtn.setDisable(disabled);
    }

    // ============================================================ 统计

    private void initStats() {
        refreshStats();
    }

    private void refreshStats() {
        String today = LocalDate.now().toString();
        DayStat todayStat = null;
        for (DayStat d : data.stats) {
            if (today.equals(d.date)) {
                todayStat = d;
                break;
            }
        }
        int mins = todayStat == null ? 0 : todayStat.focusMinutes;
        int cnt = todayStat == null ? 0 : todayStat.sessionCount;
        statsToday.setText(mins + " 分钟 · " + cnt + " 个番茄");

        statsWeekBox.getChildren().clear();
        List<DayStat> week = new ArrayList<>();
        int max = 1;
        for (int i = 6; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).toString();
            DayStat ds = null;
            for (DayStat d : data.stats) {
                if (date.equals(d.date)) {
                    ds = d;
                    break;
                }
            }
            DayStat entry = ds != null ? ds : new DayStat(date);
            week.add(entry);
            max = Math.max(max, entry.focusMinutes);
        }
        for (DayStat ds : week) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label day = new Label(ds.date.length() >= 10 ? ds.date.substring(5) + " " + weekdayCn(ds.date) : ds.date);
            day.setMinWidth(88.0);
            ProgressBar bar = new ProgressBar((double) ds.focusMinutes / max);
            bar.setPrefWidth(150.0);
            Label info = new Label(ds.sessionCount + " 个番茄 · " + ds.focusMinutes + " 分钟");
            row.getChildren().addAll(day, bar, info);
            statsWeekBox.getChildren().add(row);
        }
    }

    private String weekdayCn(String date) {
        try {
            return switch (LocalDate.parse(date).getDayOfWeek()) {
                case MONDAY -> "周一";
                case TUESDAY -> "周二";
                case WEDNESDAY -> "周三";
                case THURSDAY -> "周四";
                case FRIDAY -> "周五";
                case SATURDAY -> "周六";
                case SUNDAY -> "周日";
            };
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================ 音乐

    private void initMusic() {
        MusicSettings s = data.music;

        // 直接把 UserData 里那个 List 实例交给播放器，之后增删曲目它自己就看得到。
        MusicPlayer.setPlaylist(data.playlist);
        MusicPlayer.setVolume(s.volume);
        MusicPlayer.setOnStateChanged(() -> {
            refreshMusicList();
            refreshMusicTransport();
        });
        MusicPlayer.setOnProgress(this::refreshMusicProgress);
        MusicPlayer.setOnError(message -> {
            if (app != null && app.toast != null) {
                app.toast.showText(message, durationLong);
            }
        });

        musicAddFilesBtn.setOnAction(e -> addMusicFiles());
        musicAddFolderBtn.setOnAction(e -> addMusicFolder());
        musicClearBtn.setOnAction(e -> clearMusic());
        musicPrevBtn.setOnAction(e -> MusicPlayer.previous());
        musicPlayPauseBtn.setOnAction(e -> MusicPlayer.toggle());
        musicNextBtn.setOnAction(e -> MusicPlayer.next());

        // 进度条。拖动中的位置由拖动本身决定，松手或点轨道时才提交给播放器；
        // updatingMusicProgress 用来避免「播放推进写入位置」被当成用户拖动而回跳。
        musicProgressSlider.setMin(0);
        musicProgressSlider.setMax(1);
        musicProgressSlider.setValue(0);
        musicProgressSlider.setDisable(true);
        musicProgressSlider.valueChangingProperty().addListener((observable, oldValue, changing) -> {
            if (!changing) {
                commitMusicSeek();
            }
        });
        musicProgressSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!updatingMusicProgress && !musicProgressSlider.isValueChanging()) {
                commitMusicSeek();
            }
        });

        GuiComponents.SliderSetup<Integer> setupVolume = new GuiComponents.SimpleIntegerSliderSetup(musicVolumeSlider);
        setupVolume
                .setDisplay(musicVolumeValue, "%d%%", "音乐音量")
                .setRange(0, 100)
                .setTicks(20, 10)
                .setSliderValue(Math.round(s.volume * 100))
                .setOnChanged((observable, oldValue, newValue) -> {
                    s.volume = setupVolume.getValidatedValue() / 100f;
                    MusicPlayer.setVolume(s.volume);
                    UserDataStore.save();
                });

        musicAutoplay.setSelected(s.autoplayWithPomodoro);
        musicAutoplay.setOnAction(e -> {
            s.autoplayWithPomodoro = musicAutoplay.isSelected();
            UserDataStore.save();
        });

        refreshMusicList();
        refreshMusicTransport();
    }

    private void addMusicFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("添加音乐文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("音频文件", musicExtensionPatterns()));
        List<File> files = chooser.showOpenMultipleDialog(app.getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        reportMusicAdded(addMusicTracks(files));
    }

    private void addMusicFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择音乐文件夹");
        File dir = chooser.showDialog(app.getWindow());
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles(file -> file.isFile() && isMusicFile(file));
        if (children == null || children.length == 0) {
            app.toast.showText("这个文件夹里没有支持的音频文件", durationLong);
            return;
        }
        // 按文件名排序，否则 listFiles 的顺序取决于文件系统，每次添加都不一样。
        Arrays.sort(children, Comparator.comparing(File::getName));
        reportMusicAdded(addMusicTracks(Arrays.asList(children)));
    }

    /** 把文件加进播放列表，返回实际新增的数量（已存在的会跳过）。
     */
    private int addMusicTracks(List<File> files) {
        int added = 0;
        for (File file : files) {
            if (file == null || !file.isFile() || !isMusicFile(file)) {
                continue;
            }
            String path = canonicalPath(file);
            if (containsMusicTrack(path)) {
                continue;
            }
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            data.playlist.add(new MusicTrack(path, dot > 0 ? name.substring(0, dot) : name));
            added++;
        }
        if (added > 0) {
            UserDataStore.save();
            refreshMusicList();
        }
        return added;
    }

    private void reportMusicAdded(int added) {
        if (added == 0) {
            app.toast.showText("没有新增曲目（可能已在列表中）", durationLong);
        } else {
            app.toast.showText("已添加 " + added + " 首", durationNormal);
        }
    }

    private void clearMusic() {
        if (data.playlist.isEmpty()) {
            return;
        }
        // 先停播放器再清列表：反过来的话播放器会指着一个已经不在列表里的下标。
        MusicPlayer.stop();
        data.playlist.clear();
        UserDataStore.save();
        refreshMusicList();
    }

    private void refreshMusicList() {
        musicListBox.getChildren().clear();
        if (data.playlist.isEmpty()) {
            Label empty = new Label("还没有音乐，点「添加文件」或「添加文件夹」加入本地音频。");
            empty.setStyle("-fx-text-fill: #888;");
            musicListBox.getChildren().add(empty);
            return;
        }
        int current = MusicPlayer.getIndex();
        boolean playing = MusicPlayer.isPlaying();
        for (int i = 0; i < data.playlist.size(); i++) {
            MusicTrack track = data.playlist.get(i);
            int trackIndex = i;
            boolean isCurrent = trackIndex == current;

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            // 当前曲目加个前缀标记，并用主题色高亮。
            Label title = new Label((isCurrent ? (playing ? "▶ " : "❚❚ ") : "") + track.displayTitle());
            title.setCursor(Cursor.HAND);
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            if (isCurrent) {
                title.setStyle("-fx-text-fill: #3f8cff; -fx-font-weight: bold;");
            } else {
                title.setStyle("-fx-text-fill: #888;");
            }
            title.setOnMouseClicked(e -> MusicPlayer.playIndex(trackIndex));

            JFXButton del = new JFXButton("删除");
            del.getStyleClass().add("btn-plain-dark");
            del.setOnAction(e -> removeMusicTrack(track));

            row.getChildren().addAll(title, del);
            musicListBox.getChildren().add(row);
        }
    }

    private void removeMusicTrack(MusicTrack track) {
        int removed = data.playlist.indexOf(track);
        boolean wasCurrent = removed >= 0 && removed == MusicPlayer.getIndex();
        data.playlist.remove(track);
        UserDataStore.save();
        if (wasCurrent) {
            if (data.playlist.isEmpty()) {
                MusicPlayer.stop();
            } else {
                // 删掉的是正在播的那首：接着播落在同一个下标上的曲目。
                MusicPlayer.playIndex(removed);
            }
        }
        refreshMusicList();
    }

    private void refreshMusicTransport() {
        boolean hasTrack = MusicPlayer.getIndex() >= 0;
        musicPlayPauseBtn.setText(MusicPlayer.isPlaying() ? "暂停" : "播放");
        musicPlayPauseBtn.setDisable(!hasTrack && data.playlist.isEmpty());
        musicPrevBtn.setDisable(data.playlist.isEmpty());
        musicNextBtn.setDisable(data.playlist.isEmpty());
        refreshMusicProgress();
    }

    private void refreshMusicProgress() {
        Duration total = MusicPlayer.getTotalDuration();
        Duration current = MusicPlayer.getCurrentTime();
        boolean known = total != null && !total.isUnknown() && total.greaterThan(Duration.ZERO);
        musicProgressSlider.setDisable(!known);
        musicTimeLabel.setText(formatClock(current) + " / " + (known ? formatClock(total) : "00:00"));
        if (!known || musicProgressSlider.isValueChanging()) {
            return;
        }
        updatingMusicProgress = true;
        musicProgressSlider.setValue(current.toMillis() / total.toMillis());
        updatingMusicProgress = false;
    }

    /** 把进度条当前的位置提交给播放器。拖动中不调用，松手或点轨道时才调用。 */
    private void commitMusicSeek() {
        Duration total = MusicPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        double ratio = Math.max(0, Math.min(1, musicProgressSlider.getValue()));
        MusicPlayer.seek(total.multiply(ratio));
        refreshMusicProgress();
    }

    private String formatClock(Duration d) {
        long totalSec = d == null || d.isUnknown() ? 0 : Math.max(0, (long) d.toSeconds());
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private String[] musicExtensionPatterns() {
        return musicExtensions.stream().map(ext -> "*." + ext).toArray(String[]::new);
    }

    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot > 0 && musicExtensions.contains(name.substring(dot + 1));
    }

    private boolean containsMusicTrack(String path) {
        for (MusicTrack track : data.playlist) {
            if (track != null && path.equals(track.path)) {
                return true;
            }
        }
        return false;
    }

    /** 规范化路径，作为去重的依据。取不到规范路径时退回绝对路径。 */
    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    /** 把番茄钟的阶段迁移同步给播放器。
     * <p>
     * 只在阶段迁移点调用，{@code IDLE} 期间完全不碰播放器 —— 那时音乐是纯手动的。
     * @param play 这个阶段是否应当有音乐。为 {@code true} 时继续播放（没有选曲就什么都不做）。
     */
    private void syncMusicWithPomodoro(boolean play) {
        if (!data.music.autoplayWithPomodoro) {
            return;
        }
        if (play) {
            MusicPlayer.resume();
        } else {
            MusicPlayer.pause();
        }
    }

    // ============================================================ 定时器

    private void startTicker() {
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> onTick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    private void onTick() {
        updatePomodoroTick();
        checkScheduleDue();
        checkRemindersDue();
    }

    private void checkScheduleDue() {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (ScheduleEvent e : data.schedule) {
            if (e.alerted) {
                continue;
            }
            LocalDateTime at = eventDateTime(e);
            if (at == null) {
                continue;
            }
            LocalDateTime remindAt = at.minusMinutes(Math.max(0, e.remindBeforeMinutes));
            if (!now.isBefore(remindAt)) {
                String when = e.date + ((e.time == null || e.time.isEmpty()) ? "" : " " + e.time);
                Notifier.alert(app, "日程提醒：" + e.title + "（" + when + "）");
                e.alerted = true;
                changed = true;
            }
        }
        if (changed) {
            UserDataStore.save();
            refreshScheduleList();
        }
    }

    private void checkRemindersDue() {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (ReminderItem r : data.reminders) {
            if (!r.enabled) {
                continue;
            }
            boolean due = false;
            if (r.dailyTime != null && !r.dailyTime.isEmpty()) {
                try {
                    LocalDateTime remindAt = LocalDate.now().atTime(LocalTime.parse(r.dailyTime));
                    due = !now.isBefore(remindAt) && !isSameLocalDay(r.lastAlertAt, LocalDate.now());
                } catch (DateTimeParseException ignored) {
                }
            } else if (r.intervalMinutes > 0) {
                Instant last = parseInstantOrNull(r.lastAlertAt);
                due = last == null || !Instant.now().isBefore(last.plusSeconds(r.intervalMinutes * 60L));
            }
            if (due) {
                Notifier.alert(app, "提醒：" + r.name);
                r.lastAlertAt = Instant.now().toString();
                changed = true;
            }
        }
        if (changed) {
            UserDataStore.save();
        }
    }

    private Instant parseInstantOrNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameLocalDay(String isoInstant, LocalDate day) {
        Instant i = parseInstantOrNull(isoInstant);
        return i != null && i.atZone(ZoneId.systemDefault()).toLocalDate().equals(day);
    }
}
