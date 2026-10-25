package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.LoginController;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.TestDatabases;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.testfx.util.WaitForAsyncUtils;

/** A real install: admin has its own password and employee was renamed, keeping its password. */
class ChangedDemoLoginsTest extends AppScreenTest {
    private static final String STRONG_PASSWORD = "Strong-Pass-1";

    @Override
    void changeDemoData(Database database) throws Exception {
        String hash = BCrypt.hashpw(STRONG_PASSWORD, BCrypt.gensalt(4));
        TestDatabases.execute(database,
                "UPDATE admin SET password_hash = '" + hash + "' WHERE username = 'admin'");
        TestDatabases.execute(database,
                "UPDATE admin SET username = 'reception' WHERE username = 'employee'");
    }

    @Test
    void hintStaysHiddenWhenNoDemoLoginWorks() throws Exception {
        Label hint = lookup("#demoAccountsHint").queryAs(Label.class);
        // The text is set once the background check is done, even when there is nothing to show
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !hint.getText().isEmpty());

        assertThat(hint.isVisible()).isFalse();
        assertThat(hint.getText()).doesNotContain("reception");
    }

    @Test
    void ownPasswordLogsInWithoutTheWarning() {
        logInAs("admin", STRONG_PASSWORD);

        assertThat(alertText()).isEqualTo("Login Successful");
    }

    @Test
    void renamedAccountStillOnADemoPasswordIsWarnedAfterLogin() {
        logInAs("reception", "employee123");

        assertThat(alertText()).isEqualTo(LoginController.DEMO_PASSWORD_WARNING);
    }
}
