package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.dao.Database;

import javafx.scene.control.Label;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

class FirstRunScreenTest extends AppScreenTest {
    /** Empties the admin table so the app starts as a fresh install would, with no accounts. */
    @Override
    void changeDemoData(Database database) throws Exception {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM admin");
        }
    }

    @Test
    void anEmptyInstallOpensTheSetupScreenNotLogin() {
        assertThat(lookup("#createButton").tryQuery()).isPresent();
        assertThat(lookup("#loginButton").tryQuery()).isEmpty();
    }

    @Test
    void aWeakPasswordIsRejected() {
        createAdmin("boss", "weak", "weak");

        assertThat(alertText()).contains("Password");
    }

    @Test
    void aMistypedConfirmationIsRejected() {
        createAdmin("boss", "Secret123", "Secret124");

        assertThat(alertText()).contains("Confirm Password");
    }

    @Test
    void creatingTheAdminThenLoggingInReachesTheDashboard() {
        createAdmin("boss", "Secret123", "Secret123");
        assertThat(alertText()).contains("Please log in");
        pressAlertButton("OK");

        logInAs("boss", "Secret123");
        assertThat(alertText()).isEqualTo("Login Successful");
        pressAlertButton("OK");

        assertThat(lookup("#username").queryAs(Label.class).getText()).isEqualTo("boss");
    }

    private void createAdmin(String username, String password, String confirm) {
        clickOn("#usernameMFXTextField").write(username);
        clickOn("#passwordMFXPasswordField").write(password);
        clickOn("#confirmPasswordMFXPasswordField").write(confirm);
        clickOn("#createButton");
    }
}
