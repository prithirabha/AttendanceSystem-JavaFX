package com.bsmi.attendancesystem;

import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.bsmi.attendancesystem.dao.DataAccessException;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.UserDao;
import com.bsmi.attendancesystem.service.AuthService;
import com.bsmi.attendancesystem.service.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class LoginController {
    @FXML
    public MFXTextField usernameMFXTextField;
    @FXML
    public MFXPasswordField passwordMFXPasswordField;
    @FXML
    public Button loginButton;
    @FXML
    private Button closeButton;

    private static final System.Logger LOG = System.getLogger(LoginController.class.getName());
    private final AuthService authService =
            new AuthService(new UserDao(Database.fromCurrentSettings()));

    // The dashboard screens are laid out for at least this size
    private static final double DASHBOARD_MIN_WIDTH = 1100;
    private static final double DASHBOARD_MIN_HEIGHT = 600;

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
        showAlert(Alert.AlertType.INFORMATION, "Information Message", "Login Successful");
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
