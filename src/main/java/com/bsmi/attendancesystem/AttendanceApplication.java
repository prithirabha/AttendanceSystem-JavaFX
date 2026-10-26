package com.bsmi.attendancesystem;

import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.UserDao;
import com.bsmi.attendancesystem.service.PendingRestore;
import com.bsmi.attendancesystem.service.UserService;
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
        // A fresh install has no accounts, so it opens the setup screen instead of login
        boolean firstRun = needsFirstAdmin();
        String view = firstRun ? "fxml/first-run-view.fxml" : "fxml/login-view.fxml";
        Parent root = FXMLLoader.load(getClass().getResource(view));
        Scene scene = new Scene(root);

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setScene(scene);
        WindowFrame.makeMovable(primaryStage);
        primaryStage.setTitle((firstRun ? "Set up" : "Login") + " | Attendance System");
        primaryStage.show();
    }

    private static boolean needsFirstAdmin() {
        return new UserService(new UserDao(Database.fromCurrentSettings())).needsFirstAdmin();
    }

    public static void main(String[] args) {
        launch();
    }
}