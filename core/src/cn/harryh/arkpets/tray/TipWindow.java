/** Copyright (c) 2022-2026, Harry Huang
 * At GPL-3.0 License
 */
package cn.harryh.arkpets.tray;

import cn.harryh.arkpets.platform.HWndCtrl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/** A small always-on-top bubble that shows a short piece of text next to an ArkPets window.
 * <p>
 * It is an undecorated {@link JWindow} so that it never steals focus and never shows up in the
 * taskbar. While visible it follows the ArkPets window. It stays there until the user presses its
 * confirm button, so the reminder is never missed by looking elsewhere for a moment;
 * {@link #setOnDismiss} reports that confirmation to whoever queued the text.
 * All of its methods may be called from any thread.
 * @since ArkPets 3.13
 */
public class TipWindow {
    /** The gap between the bubble and the ArkPets window (logical px). */
    private static final int GAP = 8;
    /** The maximum width of the text (logical px); longer text is wrapped. */
    private static final int MAX_TEXT_WIDTH = 240;
    /** How often the bubble re-reads the ArkPets window position while visible (ms). */
    private static final int FOLLOW_INTERVAL_MS = 60;

    private static final Color COLOR_FILL   = Color.WHITE;
    private static final Color COLOR_STROKE = new Color(0x2A528C);
    private static final Color COLOR_TEXT   = new Color(0x1F2D3D);
    private static final Color COLOR_SHADOW = new Color(0, 0, 0, 0x60);
    private static final int   STROKE_WIDTH = 2;

    /** Corner radius of the bubble body. */
    private static final int ARC = 10;
    /** Half of the tail's width, and the tail's height. */
    private static final int TAIL_HALF = 7;
    private static final int TAIL_H = 6;
    /** Room kept around the body for the drop shadow, and how far the shadow is offset. */
    private static final int MARGIN = 3;
    private static final int SHADOW_OFFSET = 2;
    /** Padding between the body's edge and the text. */
    private static final int PAD_H = 12;
    private static final int PAD_V = 9;

    private final Supplier<HWndCtrl.WindowRect> rectSupplier;
    private final JWindow window;
    private final BubblePanel panel;
    private final JLabel label;
    /** The only way out of the bubble. Everything else in it ignores the mouse. */
    private final JButton confirmButton;
    private final Timer followTimer;
    /** Run after the user confirms the bubble, so a queue can bring up the next reminder. */
    private Runnable onDismiss;
    /** Whether the bubble's tail points down (i.e. the bubble sits above the ArkPets window). */
    private boolean tailDown = true;
    /** Whether per-pixel translucency is available; without it the rounded shape is given up. */
    private boolean translucent = false;
    private boolean disposed = false;

    /** Initializes a tip bubble.
     * <p>
     * Must be constructed on the event dispatch thread.
     * @param rectSupplier Supplies the ArkPets window's rectangle in physical pixels,
     *                     which is {@link cn.harryh.arkpets.ArkPets#getWindowScreenRect()}.
     */
    public TipWindow(Supplier<HWndCtrl.WindowRect> rectSupplier) {
        this.rectSupplier = rectSupplier;

        window = new JWindow();
        try {
            window.setType(Window.Type.POPUP);
        } catch (UnsupportedOperationException ignored) {
            // Unknown window type on this platform; the window still works as a normal one.
        }
        try {
            window.setBackground(new Color(0, 0, 0, 0));
            translucent = true;
        } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
            // No per-pixel translucency: fall back to a plain opaque rectangle.
        }
        window.setFocusableWindowState(false);
        window.setAlwaysOnTop(true);

        label = new JLabel();
        label.setForeground(COLOR_TEXT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        Font baseFont = UIManager.getFont("Label.font");
        if (baseFont == null)
            baseFont = new Font("Microsoft YaHei", Font.PLAIN, 13);
        label.setFont(baseFont.deriveFont(Font.PLAIN, 13f));

        confirmButton = new JButton("确定");
        // The window is not focusable on purpose (otherwise it would steal the foreground window's
        // focus), so the button can never hold keyboard focus either; marking it non-focusable just
        // stops Swing from painting a focus ring in a window that never has focus.
        confirmButton.setFocusable(false);
        confirmButton.setForeground(COLOR_STROKE);
        confirmButton.setFont(baseFont.deriveFont(Font.BOLD, 13f));
        // Drawn as an outline rather than a filled block: Windows' LookAndFeel paints its own
        // background for buttons and would fight a custom fill.
        confirmButton.setContentAreaFilled(false);
        confirmButton.setOpaque(false);
        confirmButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        confirmButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_STROKE),
                BorderFactory.createEmptyBorder(2, 18, 2, 18)));
        // Merely hovering must not arm the button; there is no hover feedback to give it anyway.
        confirmButton.setRolloverEnabled(false);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0)); // Gap under the text.
        buttonRow.add(confirmButton);

        panel = new BubblePanel();
        panel.add(label, BorderLayout.CENTER);
        panel.add(buttonRow, BorderLayout.SOUTH);
        // Only this button dismisses the bubble, and only when it is actually pressed; the rest of
        // the bubble ignores the mouse entirely. A reminder is the kind of thing you must not miss,
        // and a stray click on the text is far easier to do by accident than pressing a button.
        //
        // Note this listens for the press instead of using the button's action. Windows delivers a
        // mouse *release* to whatever window sits under the pointer at that moment, and this bubble
        // is always on top, so a user who presses the mouse anywhere else and lets go over the
        // bubble hands it a bare MOUSE_RELEASED. Swing treats that as a click and would fire the
        // action -- silently dismissing a reminder nobody acknowledged, which is the one thing this
        // button exists to prevent. A press can only come from the pointer being on the button.
        confirmButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1)
                    return;
                // Swing hands this window's mouse events to whichever component it is tracking,
                // which is not always the one under the pointer: it holds on to the component that
                // got an earlier press until that sequence ends. Checking the coordinates keeps
                // "only the button dismisses" true even then -- a press that is not within the
                // button is not a press on the button.
                if (!confirmButton.contains(e.getPoint()))
                    return;
                hide();
                if (onDismiss != null)
                    onDismiss.run();
            }
        });
        window.setContentPane(panel);

        followTimer = new Timer(FOLLOW_INTERVAL_MS, e -> updateLocation());
        updateInsets();
    }

    /** Shows the bubble with the given text. It stays until the user presses its confirm button.
     * @param text The text to display; multi-line text is supported.
     */
    public void show(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(text));
            return;
        }
        if (disposed)
            return;
        label.setText(wrapToHtml(text == null ? "" : text));
        panel.setTailDown(true);
        updateInsets();
        window.pack();
        updateLocation();
        if (!window.isVisible())
            window.setVisible(true);
        window.setAlwaysOnTop(true);
        window.toFront();
        followTimer.restart();
    }

    /** Sets what to run once the user has confirmed the bubble. May be called from any thread.
     * <p>
     * It is deliberately not run by {@link #hide()} or {@link #dispose()}: those are also how the
     * owner tears the bubble down, and that must not be mistaken for the user acknowledging it.
     * @param onDismiss The action to run, or {@code null} for none.
     */
    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    /** Hides the bubble immediately. */
    public void hide() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::hide);
            return;
        }
        followTimer.stop();
        window.setVisible(false);
    }

    /** Hides the bubble and releases its native resources. */
    public void dispose() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::dispose);
            return;
        }
        disposed = true;
        followTimer.stop();
        window.dispose();
    }

    /* LAYOUT */

    private void updateInsets() {
        // The outer margin leaves room for the drop shadow; the tail side needs extra room.
        int v = MARGIN + PAD_V;
        panel.setBorder(BorderFactory.createEmptyBorder(
                v + (tailDown ? 0 : TAIL_H),
                MARGIN + PAD_H,
                v + SHADOW_OFFSET + (tailDown ? TAIL_H : 0),
                MARGIN + PAD_H + SHADOW_OFFSET));
    }

    /** Moves the bubble to sit above the ArkPets window, flipping below it when there is no room,
     * and clamping it into the screen's work area.
     */
    private void updateLocation() {
        if (disposed || !window.isDisplayable())
            return;
        HWndCtrl.WindowRect pet = rectSupplier.get();
        if (pet == null || pet.width() <= 0 || pet.height() <= 0)
            return; // The ArkPets window has not been positioned yet.

        // Win32 reports physical pixels while Swing lays out in logical units. This mirrors
        // MemberTrayImpl#showDialog, which faces the very same discrepancy.
        AffineTransform transform = window.getGraphicsConfiguration().getDefaultTransform();
        Rectangle petRect = new Rectangle(
                (int) (pet.left() / transform.getScaleX()),
                (int) (pet.top() / transform.getScaleY()),
                (int) (pet.width() / transform.getScaleX()),
                (int) (pet.height() / transform.getScaleY()));

        Rectangle area = workAreaFor(petRect);
        int winWidth = window.getWidth();
        int winHeight = window.getHeight();

        // Prefer the space above the ArkPets window; flip below it when that would go off-screen.
        boolean down = petRect.y - winHeight - GAP < area.y;
        if (panel.setTailDown(down)) {
            updateInsets();
            window.pack();
            winWidth = window.getWidth();
            winHeight = window.getHeight();
        }

        int x = petRect.x + petRect.width / 2 - winWidth / 2;
        int y = down ? petRect.y + petRect.height + GAP : petRect.y - winHeight - GAP;
        x = clamp(x, area.x, area.x + area.width - winWidth);
        y = clamp(y, area.y, area.y + area.height - winHeight);
        window.setLocation(x, y);
    }

    private static Rectangle workAreaFor(Rectangle petRect) {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Point center = new Point(petRect.x + petRect.width / 2, petRect.y + petRect.height / 2);
        for (GraphicsDevice device : env.getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            if (bounds.contains(center))
                return bounds;
        }
        return env.getMaximumWindowBounds();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }

    /* TEXT */

    /** Converts the text into the HTML dialect {@link JLabel} understands, wrapping it by hand so
     * that the bubble sizes itself to the actual text instead of a fixed width.
     */
    private String wrapToHtml(String text) {
        FontMetrics metrics = label.getFontMetrics(label.getFont());
        StringBuilder html = new StringBuilder("<html>");
        boolean first = true;
        for (String paragraph : text.split("\r?\n", -1)) {
            for (String line : wrapLine(paragraph, metrics)) {
                if (!first)
                    html.append("<br>");
                html.append(escapeHtml(line));
                first = false;
            }
        }
        return html.append("</html>").toString();
    }

    /** Greedily wraps one paragraph by code point, which suits CJK text well. */
    private static List<String> wrapLine(String line, FontMetrics metrics) {
        List<String> lines = new ArrayList<>();
        if (line.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String ch = line.substring(i, i + charCount);
            int chWidth = metrics.stringWidth(ch);
            if (currentWidth + chWidth > MAX_TEXT_WIDTH && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }
            current.append(ch);
            currentWidth += chWidth;
            i += charCount;
        }
        lines.add(current.toString());
        return lines;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }


    /** The bubble's rounded body, optionally carrying a tail on its top or bottom edge.
     */
    private class BubblePanel extends JPanel {
        BubblePanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
        }

        boolean setTailDown(boolean down) {
            if (tailDown == down)
                return false;
            tailDown = down;
            return true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = MARGIN;
            int y = MARGIN + (tailDown ? 0 : TAIL_H);
            int w = getWidth() - MARGIN * 2 - SHADOW_OFFSET;
            int h = getHeight() - MARGIN * 2 - SHADOW_OFFSET - TAIL_H;
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }

            if (!translucent) {
                // Without per-pixel translucency there is no way to draw a rounded shape,
                // so the bubble becomes a plain rectangle filling the whole window.
                g2.setColor(COLOR_FILL);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(COLOR_STROKE);
                g2.setStroke(new BasicStroke(STROKE_WIDTH));
                g2.drawRect(STROKE_WIDTH / 2, STROKE_WIDTH / 2,
                        getWidth() - STROKE_WIDTH, getHeight() - STROKE_WIDTH);
                g2.dispose();
                return;
            }

            Path2D shape = bubblePath(x, y, w, h);
            g2.setColor(COLOR_SHADOW);
            g2.translate(SHADOW_OFFSET, SHADOW_OFFSET);
            g2.fill(shape);
            g2.translate(-SHADOW_OFFSET, -SHADOW_OFFSET);

            g2.setColor(COLOR_FILL);
            g2.fill(shape);
            g2.setColor(COLOR_STROKE);
            g2.setStroke(new BasicStroke(STROKE_WIDTH));
            g2.draw(shape);
            g2.dispose();
        }

        private Path2D bubblePath(float x, float y, float w, float h) {
            float cx = x + w / 2f;
            Path2D.Float path = new Path2D.Float();
            path.moveTo(x + ARC, y);
            if (!tailDown) {
                path.lineTo(cx - TAIL_HALF, y);
                path.lineTo(cx, y - TAIL_H);
                path.lineTo(cx + TAIL_HALF, y);
            }
            path.lineTo(x + w - ARC, y);
            path.quadTo(x + w, y, x + w, y + ARC);
            path.lineTo(x + w, y + h - ARC);
            path.quadTo(x + w, y + h, x + w - ARC, y + h);
            if (tailDown) {
                path.lineTo(cx + TAIL_HALF, y + h);
                path.lineTo(cx, y + h + TAIL_H);
                path.lineTo(cx - TAIL_HALF, y + h);
            }
            path.lineTo(x + ARC, y + h);
            path.quadTo(x, y + h, x, y + h - ARC);
            path.lineTo(x, y + ARC);
            path.quadTo(x, y, x + ARC, y);
            path.closePath();
            return path;
        }
    }
}
