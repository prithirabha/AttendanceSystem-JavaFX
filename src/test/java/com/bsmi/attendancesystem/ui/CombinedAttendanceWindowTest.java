package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.CombinedAttendanceWindow;
import com.bsmi.attendancesystem.StudentAttendanceData;
import com.bsmi.attendancesystem.service.AttendanceCsv;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

@UiTest
class CombinedAttendanceWindowTest {
    @TempDir Path folder;

    @BeforeAll
    static void startJavaFx() throws Exception {
        FxToolkit.registerPrimaryStage();
    }

    @Test
    void showsOneReadOnlyTablePerSiteSortedByName() throws Exception {
        writeShared("library", "20260101-120000", "1001", "1002");
        writeShared("lab", "20260102-090000", "2001");

        TabPane tabs = tabsFor(folder);

        assertThat(tabs.getTabs()).hasSize(2);
        Tab lab = tabs.getTabs().get(0);
        Tab library = tabs.getTabs().get(1);
        assertThat(lab.getText()).startsWith("lab");
        assertThat(library.getText()).startsWith("library");
        assertThat(((TableView<?>) lab.getContent()).getItems()).hasSize(1);
        assertThat(((TableView<?>) library.getContent()).getItems()).hasSize(2);
        assertThat(((TableView<?>) library.getContent()).isEditable()).isFalse();
    }

    @Test
    void showsAMessageWhenTheFolderHasNoSites() throws Exception {
        TabPane tabs = tabsFor(folder);

        assertThat(tabs.getTabs()).hasSize(1);
        assertThat(tabs.getTabs().get(0).getText()).isEqualTo("No sites yet");
    }

    private static TabPane tabsFor(Path folder) throws Exception {
        AtomicReference<Parent> content = new AtomicReference<>();
        FxToolkit.setupFixture(() -> content.set(CombinedAttendanceWindow.buildContent(folder)));
        return (TabPane) ((BorderPane) content.get()).getCenter();
    }

    private void writeShared(String site, String timestamp, String... studentNums) {
        List<StudentAttendanceData> rows =
                Arrays.stream(studentNums)
                        .map(n -> new StudentAttendanceData(n, "S", "C", "09:00", "15:00", "Present"))
                        .toList();
        AttendanceCsv.save(rows, folder.resolve("attendance-" + site + "-" + timestamp + ".csv"));
    }
}
