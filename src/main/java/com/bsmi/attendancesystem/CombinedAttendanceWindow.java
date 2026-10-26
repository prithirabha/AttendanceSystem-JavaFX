package com.bsmi.attendancesystem;

import com.bsmi.attendancesystem.service.CombinedAttendance;
import java.nio.file.Path;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A read-only window showing the attendance several sites publish into a shared folder, one tab
 * per site (its latest snapshot). It only reads CSV rows through {@link CombinedAttendance}, never
 * another computer's database. Built in code rather than FXML, because the number of tabs is not
 * known ahead of time.
 */
public final class CombinedAttendanceWindow {
    private static final String TABS_ID = "combinedTabs";
    private static final String EMPTY_MESSAGE = "No shared attendance files in this folder yet.";
    private static final String[] COLUMN_TITLES = {
        "Student#", "Semester", "Course", "Entry Time", "Exit Time", "Status"
    };
    private static final String[] ROW_PROPERTIES = {
        "studentNum", "semester", "course", "entryTime", "exitTime", "status"
    };

    private CombinedAttendanceWindow() {}

    /** Opens the window for {@code folder}, owned by {@code owner}. */
    public static void open(Window owner, Path folder) {
        Stage stage = new Stage();
        stage.setTitle("Combined attendance");
        stage.initOwner(owner);
        Scene scene = new Scene(buildContent(folder), 900, 600);
        var theme = CombinedAttendanceWindow.class.getResource("css/theme.css");
        if (theme != null) {
            scene.getStylesheets().add(theme.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }

    /** The window content: a folder bar with Refresh, and a tab per site. Exposed for tests. */
    public static Parent buildContent(Path folder) {
        TabPane tabs = new TabPane();
        tabs.setId(TABS_ID);

        Label folderLabel = new Label(folder.toString());
        Button refresh = new Button("Refresh");
        refresh.setId("combinedRefresh");
        refresh.setOnAction(event -> populate(tabs, folder));

        HBox bar = new HBox(10, new Label("Shared folder:"), folderLabel, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));

        populate(tabs, folder);

        BorderPane root = new BorderPane();
        root.setTop(bar);
        root.setCenter(tabs);
        return root;
    }

    private static void populate(TabPane tabs, Path folder) {
        List<CombinedAttendance.Site> sites = CombinedAttendance.readFolder(folder);
        tabs.getTabs().clear();
        if (sites.isEmpty()) {
            Tab empty = new Tab("No sites yet", new StackPane(new Label(EMPTY_MESSAGE)));
            empty.setClosable(false);
            tabs.getTabs().add(empty);
            return;
        }
        for (CombinedAttendance.Site site : sites) {
            Tab tab = new Tab(site.name() + "  (" + site.exportedAt() + ")", tableOf(site));
            tab.setClosable(false);
            tabs.getTabs().add(tab);
        }
    }

    private static TableView<StudentAttendanceData> tableOf(CombinedAttendance.Site site) {
        TableView<StudentAttendanceData> table = new TableView<>();
        for (int column = 0; column < COLUMN_TITLES.length; column++) {
            TableColumn<StudentAttendanceData, String> cell =
                    new TableColumn<>(COLUMN_TITLES[column]);
            cell.setCellValueFactory(new PropertyValueFactory<>(ROW_PROPERTIES[column]));
            table.getColumns().add(cell);
        }
        table.setItems(FXCollections.observableArrayList(site.rows()));
        return table;
    }
}
