package com.bsmi.attendancesystem;

import com.bsmi.attendancesystem.service.PendingRestore;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class AttendanceApplication extends Application {
    @Override
    public void init() {
        // A restore chosen last time is swapped in before anything opens the database
        PendingRestore.applyIfReady(AppPaths.dataDirectory());
        DatabaseMigrations.applyPending(DatabaseSettings.current());
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        AppFonts.loadBundled();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("fxml/login-view.fxml"));
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);

        // Apply transparent style to the stage
        primaryStage.initStyle(StageStyle.TRANSPARENT);

        // Set the scene and show the stage
        primaryStage.setScene(scene);
        WindowFrame.makeMovable(primaryStage);
        primaryStage.setTitle("Login | Attendance System");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}