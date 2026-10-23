package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.WindowFrame;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The dashboard has no title bar or border from the system; the app draws its own. */
class WindowResizeTest extends AppScreenTest {
    private static final double DASHBOARD_MIN_WIDTH = 1100;
    private static final double DASHBOARD_MIN_HEIGHT = 600;

    private Stage dashboard;

    @BeforeEach
    void openDashboard() {
        logInAndDismissWelcome("admin", "admin123");
        dashboard = (Stage) lookup("#main_form").query().getScene().getWindow();
    }

    @Test
    void dashboardOpensAtItsSmallestSizeOnScreen() {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        assertThat(dashboard.getWidth())
                .isEqualTo(Math.min(DASHBOARD_MIN_WIDTH, screen.getWidth()));
        assertThat(dashboard.getHeight())
                .isEqualTo(Math.min(DASHBOARD_MIN_HEIGHT, screen.getHeight()));
        assertThat(screen.contains(bounds())).isTrue();
    }

    @Test
    void draggingTheBottomRightCornerMakesTheWindowAndTableBigger() {
        clickOn("#addStudent_btn");
        double tableWidth = studentTable().getWidth();
        double tableHeight = studentTable().getHeight();
        Rectangle2D before = bounds();
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        // The mouse can't leave the screen, and on a small one the window already fills its width
        double dx = roomToGrow(before.getMaxX(), screen.getMaxX(), 80);
        double dy = roomToGrow(before.getMaxY(), screen.getMaxY(), 40);

        drag(new Point2D(before.getMaxX() - 2, before.getMaxY() - 2)).dropBy(dx, dy);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(dy).isPositive();
        assertThat(dashboard.getWidth()).isEqualTo(before.getWidth() + dx);
        assertThat(dashboard.getHeight()).isEqualTo(before.getHeight() + dy);
        assertThat(studentTable().getWidth()).isEqualTo(tableWidth + dx);
        assertThat(studentTable().getHeight()).isEqualTo(tableHeight + dy);
    }

    @Test
    void windowCannotBeDraggedSmallerThanTheDashboardNeeds() {
        Rectangle2D before = bounds();

        drag(new Point2D(before.getMaxX() - 2, before.getMaxY() - 2)).dropBy(-300, -200);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(bounds()).isEqualTo(before);
    }

    @Test
    void maximizeButtonFillsTheScreenAndRestoresTheOldSize() {
        Rectangle2D before = bounds();

        clickOn("#maximize_btn");
        assertThat(bounds()).isEqualTo(Screen.getPrimary().getVisualBounds());
        assertThat(WindowFrame.of(dashboard).isMaximized()).isTrue();

        clickOn("#maximize_btn");
        assertThat(bounds()).isEqualTo(before);
        assertThat(WindowFrame.of(dashboard).isMaximized()).isFalse();
    }

    @Test
    void doubleClickOnTheTitleBarMaximizes() {
        doubleClickOn(".top-form", MouseButton.PRIMARY);

        assertThat(WindowFrame.of(dashboard).isMaximized()).isTrue();
    }

    @Test
    void maximizedWindowCannotBeMovedOrResized() {
        clickOn("#maximize_btn");
        Rectangle2D maximized = bounds();

        drag(new Point2D(maximized.getMaxX() - 2, maximized.getMaxY() - 2)).dropBy(-100, -50);
        drag(".top-form").dropBy(50, 50);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(bounds()).isEqualTo(maximized);
    }

    @Test
    void windowWithoutAFrameIsAnError() throws Exception {
        Window plain = WaitForAsyncUtils.asyncFx(() -> new Stage()).get();

        assertThatThrownBy(() -> WindowFrame.of(plain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The window has no WindowFrame");
    }

    private static double roomToGrow(double windowEdge, double screenEdge, double wanted) {
        return Math.max(0, Math.min(wanted, screenEdge - windowEdge - 2));
    }

    private TableView<?> studentTable() {
        return lookup("#addStudent_tableView").queryAs(TableView.class);
    }

    private Rectangle2D bounds() {
        return new Rectangle2D(
                dashboard.getX(), dashboard.getY(), dashboard.getWidth(), dashboard.getHeight());
    }
}
