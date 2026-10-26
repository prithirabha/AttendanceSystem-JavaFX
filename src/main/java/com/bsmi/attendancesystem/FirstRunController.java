package com.bsmi.attendancesystem;

import com.bsmi.attendancesystem.dao.DataAccessException;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.UserDao;
import com.bsmi.attendancesystem.service.UserForm;
import com.bsmi.attendancesystem.service.UserService;
import com.bsmi.attendancesystem.service.ValidationException;

import io.github.palexdev.materialfx.controls.MFXPasswordField;
import io.github.palexdev.materialfx.controls.MFXTextField;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The first-time setup screen. A fresh install has no accounts, so this screen creates the one
 * administrator the app needs before anyone can log in, then hands over to the login screen.
 */
public class FirstRunController {
    @FXML private MFXTextField usernameMFXTextField;
    @FXML private MFXPasswordField passwordMFXPasswordField;
    @FXML private MFXPasswordField confirmPasswordMFXPasswordField;
    @FXML private Button createButton;
    @FXML private Button closeButton;

    private final UserService userService =
            new UserService(new UserDao(Database.fromCurrentSettings()));

    @FXML
    protected void createButtonOnAction() {
        UserForm form =
                new UserForm(
                        null,
                        usernameMFXTextField.getText(),
                        passwordMFXPasswordField.getText(),
                        confirmPasswordMFXPasswordField.getText(),
                        UserService.ADMIN);
        try {
            userService.createFirstAdmin(form);
        } catch (ValidationException e) {
            showAlert(Alert.AlertType.ERROR, "Error Message", e.getMessage());
            return;
        } catch (DataAccessException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Database Error",
                    e.getMessage() + ". Please try again; if it keeps happening, restart the app.");
            return;
        }
        showAlert(
                Alert.AlertType.INFORMATION,
                "Administrator created",
                "Your administrator account is ready. Please log in.");
        goToLogin();
    }

    private void goToLogin() {
        try {
            Parent login = FXMLLoader.load(getClass().getResource("fxml/login-view.fxml"));
            Stage stage = (Stage) createButton.getScene().getWindow();
            stage.getScene().setRoot(login);
            stage.setTitle("Login | Attendance System");
        } catch (IOException e) {
            // A broken FXML file is a packaging bug, not something the user can fix
            throw new UncheckedIOException("Could not load the login screen", e);
        }
    }

    @FXML
    public void closeButtonOnAction(ActionEvent event) {
        System.exit(0);
    }

    private static void showAlert(Alert.AlertType alertType, String title, String contentText) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(contentText);
        alert.showAndWait();
    }
}
