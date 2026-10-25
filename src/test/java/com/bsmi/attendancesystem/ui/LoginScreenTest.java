package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.LoginController;
import io.github.palexdev.materialfx.controls.MFXTextField;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

class LoginScreenTest extends AppScreenTest {
    @Test
    void formStartsEmptyAndShowsTheDemoLogins() throws Exception {
        assertThat(lookup("#usernameMFXTextField").queryAs(MFXTextField.class).getText()).isEmpty();
        Label hint = lookup("#demoAccountsHint").queryAs(Label.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, hint::isVisible);
        assertThat(hint.getText()).contains("admin / admin123", "employee / employee123");
    }

    @Test
    void blankFormAsksForTheUsername() {
        clickOn("#loginButton");

        assertThat(alertText()).isEqualTo("Please fill in the Username field");
    }

    @Test
    void missingPasswordIsAskedFor() {
        clickOn("#usernameMFXTextField").write("admin");
        clickOn("#loginButton");

        assertThat(alertText()).isEqualTo("Please fill in the Password field");
    }

    @Test
    void wrongPasswordIsRejected() {
        logInAs("admin", "not-the-password");

        assertThat(alertText()).isEqualTo("Incorrect Username/Password");
    }

    @Test
    void unknownUserGetsTheSameMessageAsAWrongPassword() {
        logInAs("nobody", "admin123");

        assertThat(alertText()).isEqualTo("Incorrect Username/Password");
    }

    @Test
    void adminLandsOnTheDashboardWithUserManagementAndBackups() {
        logInAs("admin", "admin123");
        assertThat(alertText()).isEqualTo(LoginController.DEMO_PASSWORD_WARNING);
        pressAlertButton("OK");

        assertThat(lookup("#username").queryAs(Label.class).getText()).isEqualTo("admin");
        assertThat(lookup("#userManagement_btn").queryAs(Button.class).isVisible()).isTrue();
        assertThat(lookup("#userManagement_backupBtn").queryAs(Button.class).isVisible()).isTrue();
        assertThat(lookup("#userManagement_restoreBtn").queryAs(Button.class).isVisible())
                .isTrue();
    }

    @Test
    void employeeDoesNotSeeUserManagementOrBackups() {
        logInAndDismissWelcome("employee", "employee123");

        assertThat(lookup("#userManagement_btn").queryAs(Button.class).isVisible()).isFalse();
        assertThat(lookup("#userManagement_backupBtn").queryAs(Button.class).isVisible())
                .isFalse();
        assertThat(lookup("#userManagement_restoreBtn").queryAs(Button.class).isVisible())
                .isFalse();
    }

    @Test
    void enterKeyLogsIn() {
        clickOn("#usernameMFXTextField").write("admin");
        clickOn("#passwordMFXPasswordField").write("admin123").type(KeyCode.ENTER);

        assertThat(alertText()).isEqualTo(LoginController.DEMO_PASSWORD_WARNING);
    }
}
