package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.AppPaths;
import com.bsmi.attendancesystem.AttendanceApplication;
import com.bsmi.attendancesystem.UserSession;
import com.bsmi.attendancesystem.dao.Database;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;

/** Starts the real app on a fresh demo database for every test, then closes all its windows. */
@UiTest
abstract class AppScreenTest extends FxRobot {
    private static final int MAX_CLOSE_ROUNDS = 5;
    private static final long FX_TIMEOUT_MILLIS = 5_000;

    @TempDir Path dataFolder;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @BeforeEach
    void startApp() throws Exception {
        System.setProperty(AppPaths.HOME_OVERRIDE_PROPERTY, dataFolder.toString());
        FxToolkit.setupFixture(
                () -> {
                    AttendanceApplication app = new AttendanceApplication();
                    app.init();
                    changeDemoData(Database.fromCurrentSettings());
                    // The app styles its window, which only works on a stage never shown before
                    app.start(new Stage());
                    return null;
                });
    }

    /** Runs after the database is created and before the login screen opens. */
    void changeDemoData(Database database) throws Exception {}

    @AfterEach
    void closeApp() {
        // Hiding an alert lets the code that waited on it go on, and that can open a new window,
        // e.g. the dashboard after the login alert. Left open, it would catch the next test's
        // clicks, so hide again until nothing is showing.
        for (int round = 0; round < MAX_CLOSE_ROUNDS && anyWindowShowing(); round++) {
            interact(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertThat(anyWindowShowing()).as("windows left open after the test").isFalse();
        UserSession.end();
        System.clearProperty(AppPaths.HOME_OVERRIDE_PROPERTY);
    }

    private boolean anyWindowShowing() {
        return WaitForAsyncUtils.waitForAsyncFx(
                FX_TIMEOUT_MILLIS, () -> !Window.getWindows().isEmpty());
    }

    void logInAs(String username, String password) {
        clickOn("#usernameMFXTextField").write(username);
        clickOn("#passwordMFXPasswordField").write(password);
        clickOn("#loginButton");
    }

    void logInAndDismissWelcome(String username, String password) {
        logInAs(username, password);
        pressAlertButton("OK");
    }

    String alertText() {
        return lookup(".dialog-pane").queryAs(DialogPane.class).getContentText();
    }

    void pressAlertButton(String text) {
        Button button =
                from(lookup(".dialog-pane").queryAs(DialogPane.class))
                        .lookup((Button candidate) -> text.equals(candidate.getText()))
                        .query();
        clickOn(button);
    }
}
