package com.bsmi.attendancesystem;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Does what the operating system's title bar and border would do for the app's windows, which
 * have neither (StageStyle.TRANSPARENT): dragging anywhere moves the window, and on a resizable
 * window dragging near an edge resizes it and {@link #toggleMaximized()} fills the screen.
 */
public final class WindowFrame {
    /** How close to an edge, in pixels, a drag resizes the window instead of moving it. */
    static final double RESIZE_BORDER = 6;

    /** Which edges of the window a resize drag moves. */
    record Edges(boolean left, boolean right, boolean top, boolean bottom) {
        static final Edges NONE = new Edges(false, false, false, false);

        boolean any() {
            return left || right || top || bottom;
        }

        Cursor cursor() {
            if (top) {
                return left ? Cursor.NW_RESIZE : right ? Cursor.NE_RESIZE : Cursor.N_RESIZE;
            }
            if (bottom) {
                return left ? Cursor.SW_RESIZE : right ? Cursor.SE_RESIZE : Cursor.S_RESIZE;
            }
            return left ? Cursor.W_RESIZE : right ? Cursor.E_RESIZE : Cursor.DEFAULT;
        }
    }

    private final Stage stage;
    private final boolean resizable;
    private final double minWidth;
    private final double minHeight;

    private double pressSceneX;
    private double pressSceneY;
    private double pressScreenX;
    private double pressScreenY;
    private Rectangle2D boundsAtPress;
    private Edges resizing = Edges.NONE;
    // Where the window was before it was maximized; null while it is not maximized
    private Rectangle2D boundsBeforeMaximize;

    private WindowFrame(Stage stage, boolean resizable, double minWidth, double minHeight) {
        this.stage = stage;
        this.resizable = resizable;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    /** Lets the user move the window by dragging it. Call it after the stage has its scene. */
    public static WindowFrame makeMovable(Stage stage) {
        return attach(new WindowFrame(stage, false, 0, 0));
    }

    /**
     * Lets the user move, resize and maximize the window. It opens at its preferred size, made
     * smaller to fit on screen, and can't be made smaller than the given size, or the screen if
     * that is smaller. Call it after the stage has its scene and before it is shown.
     */
    public static WindowFrame makeResizable(Stage stage, double minWidth, double minHeight) {
        if (minWidth <= 0 || minHeight <= 0) {
            throw new IllegalArgumentException(
                    "The smallest window size must be positive, not " + minWidth + "x" + minHeight);
        }
        Parent root = stage.getScene().getRoot();
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Rectangle2D opening = fitted(root.prefWidth(-1), root.prefHeight(-1), screen);
        setBounds(stage, opening);
        return attach(
                new WindowFrame(
                        stage,
                        true,
                        Math.min(minWidth, screen.getWidth()),
                        Math.min(minHeight, screen.getHeight())));
    }

    /** The frame a window was given by {@link #makeMovable} or {@link #makeResizable}. */
    public static WindowFrame of(Window window) {
        if (window.getProperties().get(WindowFrame.class) instanceof WindowFrame frame) {
            return frame;
        }
        throw new IllegalStateException("The window has no WindowFrame");
    }

    public boolean isMaximized() {
        return boundsBeforeMaximize != null;
    }

    /** Fills the screen the window is on, without covering the taskbar, or undoes that. */
    public void toggleMaximized() {
        if (isMaximized()) {
            setBounds(stage, boundsBeforeMaximize);
            boundsBeforeMaximize = null;
        } else {
            boundsBeforeMaximize = boundsOf(stage);
            setBounds(stage, screenOf(boundsBeforeMaximize).getVisualBounds());
        }
    }

    /** Which edges a press at (x, y) inside a window of the given size would resize. */
    static Edges edgesAt(double x, double y, double width, double height) {
        boolean left = x < RESIZE_BORDER;
        boolean right = !left && x >= width - RESIZE_BORDER;
        boolean top = y < RESIZE_BORDER;
        boolean bottom = !top && y >= height - RESIZE_BORDER;
        return new Edges(left, right, top, bottom);
    }

    /**
     * The window bounds after dragging the given edges by (dx, dy). The opposite edges stay where
     * they are, and the size never goes below the minimum.
     */
    static Rectangle2D resized(
            Rectangle2D start, Edges edges, double dx, double dy, double minWidth, double minHeight) {
        double x = start.getMinX();
        double y = start.getMinY();
        double width = start.getWidth();
        double height = start.getHeight();
        if (edges.right()) {
            width = Math.max(minWidth, start.getWidth() + dx);
        }
        if (edges.left()) {
            width = Math.max(minWidth, start.getWidth() - dx);
            x = start.getMaxX() - width;
        }
        if (edges.bottom()) {
            height = Math.max(minHeight, start.getHeight() + dy);
        }
        if (edges.top()) {
            height = Math.max(minHeight, start.getHeight() - dy);
            y = start.getMaxY() - height;
        }
        return new Rectangle2D(x, y, width, height);
    }

    /** A window of the wanted size, made smaller to fit on the screen, centred on it. */
    static Rectangle2D fitted(double width, double height, Rectangle2D screen) {
        double fittedWidth = Math.min(width, screen.getWidth());
        double fittedHeight = Math.min(height, screen.getHeight());
        return new Rectangle2D(
                screen.getMinX() + (screen.getWidth() - fittedWidth) / 2,
                screen.getMinY() + (screen.getHeight() - fittedHeight) / 2,
                fittedWidth,
                fittedHeight);
    }

    private static WindowFrame attach(WindowFrame frame) {
        Parent root = frame.stage.getScene().getRoot();
        // A filter sees the press before the controls do, so the edges win over what lies under
        // them; the handlers only see presses that no control has used, as moving did before
        root.addEventFilter(MouseEvent.MOUSE_MOVED, frame::showResizeCursor);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, frame::startResize);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, frame::resize);
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, frame::endResize);
        root.addEventHandler(MouseEvent.MOUSE_PRESSED, frame::startMove);
        root.addEventHandler(MouseEvent.MOUSE_DRAGGED, frame::move);
        root.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> frame.stage.setOpacity(1));
        frame.stage.getProperties().put(WindowFrame.class, frame);
        return frame;
    }

    private boolean canResize() {
        return resizable && !isMaximized();
    }

    private Edges edgesUnder(MouseEvent event) {
        return edgesAt(
                event.getSceneX(),
                event.getSceneY(),
                stage.getScene().getWidth(),
                stage.getScene().getHeight());
    }

    private void showResizeCursor(MouseEvent event) {
        if (canResize()) {
            stage.getScene().getRoot().setCursor(edgesUnder(event).cursor());
        }
    }

    private void startResize(MouseEvent event) {
        if (!canResize() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        Edges edges = edgesUnder(event);
        if (edges.any()) {
            resizing = edges;
            pressScreenX = event.getScreenX();
            pressScreenY = event.getScreenY();
            boundsAtPress = boundsOf(stage);
            event.consume();
        }
    }

    private void resize(MouseEvent event) {
        if (resizing.any()) {
            setBounds(
                    stage,
                    resized(
                            boundsAtPress,
                            resizing,
                            event.getScreenX() - pressScreenX,
                            event.getScreenY() - pressScreenY,
                            minWidth,
                            minHeight));
            event.consume();
        }
    }

    private void endResize(MouseEvent event) {
        if (resizing.any()) {
            // The mouse may have moved a little more since the last drag event
            resize(event);
            resizing = Edges.NONE;
            event.consume();
        }
    }

    private void startMove(MouseEvent event) {
        pressSceneX = event.getSceneX();
        pressSceneY = event.getSceneY();
    }

    private void move(MouseEvent event) {
        if (isMaximized()) {
            return;
        }
        stage.setX(event.getScreenX() - pressSceneX);
        stage.setY(event.getScreenY() - pressSceneY);
        stage.setOpacity(.6);
    }

    private static Screen screenOf(Rectangle2D bounds) {
        return Screen.getScreensForRectangle(bounds).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
    }

    private static Rectangle2D boundsOf(Stage stage) {
        return new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
    }

    private static void setBounds(Stage stage, Rectangle2D bounds) {
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }
}
