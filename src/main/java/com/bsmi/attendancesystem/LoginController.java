package com.bsmi.attendancesystem;

import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.bsmi.attendancesystem.dao.DataAccessException;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.UserDao;
import com.bsmi.attendancesystem.service.AuthService;
import com.bsmi.attendancesystem.service.AuthService.DemoLogin;
import com.bsmi.attendancesystem.service.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LoginController {
    @FXML
    public MFXTextField usernameMFXTextField;
    @FXML
    public MFXPasswordField passwordMFXPasswordField;
    @FXML
    public Button loginButton;
    @FXML
    private Button closeButton;
    @FXML
    private Label demoAccountsHint;

    private static final System.Logger LOG = System.getLogger(LoginController.class.getName());
    private final AuthService authService =
            new AuthService(new UserDao(Database.fromCurrentSettings()));

    // The dashboard screens are laid out for at least this size
    private static final double DASHBOARD_MIN_WIDTH = 1100;
    private static final double DASHBOARD_MIN_HEIGHT = 600;

    public static final String DEMO_PASSWORD_WARNING =
            "Logged in, but with a demo password that anyone can read in the README. "
                    + "Change it on Manage Users (or ask an Admin to) before real use.";

    @FXML
    private void initialize() {
        // The checks are slow on purpose (bcrypt), so they run in the background
        CompletableFuture.supplyAsync(authService::demoLoginsThatStillWork)
                .whenComplete((working, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        // The login button reports database problems; the hint just stays hidden
                        LOG.log(System.Logger.Level.DEBUG, "Demo login check failed", error);
                    } else {
                        showDemoLogins(working);
                    }
                }));
    }

    private void showDemoLogins(List<DemoLogin> working) {
        List<String> lines = working.stream()
                .map(demo -> demo.username() + " / " + demo.password())
                .toList();
        demoAccountsHint.setText("Demo logins: " + String.join("\n", lines));
        demoAccountsHint.setVisible(!working.isEmpty());
    }

    @FXML
    protected void loginButtonOnAction() {
        Optional<UserData> user;
        try {
            user = authService.login(usernameMFXTextField.getText(), passwordMFXPasswordField.getText());
        } catch (ValidationException e) {
            showAlert(Alert.AlertType.ERROR, "Error Message", e.getMessage());
            return;
        } catch (DataAccessException e) {
            LOG.log(System.Logger.Level.WARNING, e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Database Error",
                    e.getMessage() + ". Please try again; if it keeps happening, restart the app.");
            return;
        }
        if (user.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error Message", "Incorrect Username/Password");
            return;
        }
        UserSession.start(user.get());
        if (AuthService.isPublicDemoPassword(passwordMFXPasswordField.getText())) {
            showAlert(Alert.AlertType.WARNING, "Demo Password", DEMO_PASSWORD_WARNING);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Information Message", "Login Successful");
        }
        loginButton.getScene().getWindow().hide();
        try {
            openDashboard();
        } catch (IOException e) {
            // A broken FXML file is a packaging bug, not something the user can fix
            throw new UncheckedIOException("Could not load the dashboard screen", e);
        }
    }

    private void openDashboard() throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("fxml/dashboard-view.fxml"));
        Stage stage = new Stage();
        Scene scene = new Scene(root);
        stage.setTitle("Dashboard | Student Management System");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        // The window has no title bar or border, so the frame moves and resizes it
        WindowFrame.makeResizable(stage, DASHBOARD_MIN_WIDTH, DASHBOARD_MIN_HEIGHT);
        stage.show();
    }

    private static void showAlert(Alert.AlertType alertType, String title, String contentText) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    @FXML
    public void closeButtonOnAction(ActionEvent e) {
//        Stage stage = (Stage) closeButton.getScene().getWindow();
//        stage.close();
        System.exit(0);
    }
}
