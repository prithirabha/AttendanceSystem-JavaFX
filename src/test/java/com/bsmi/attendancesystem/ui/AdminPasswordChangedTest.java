package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.TestDatabases;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.testfx.util.WaitForAsyncUtils;

class AdminPasswordChangedTest extends AppScreenTest {
    @Override
    void changeDemoData(Database database) throws Exception {
        String hash = BCrypt.hashpw("Strong-Pass-1", BCrypt.gensalt(4));
        TestDatabases.execute(database,
                "UPDATE admin SET password_hash = '" + hash + "' WHERE username = 'admin'");
    }

    @Test
    void hintShowsOnlyTheEmployeeLogin() throws Exception {
        Label hint = lookup("#demoAccountsHint").queryAs(Label.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, hint::isVisible);

        assertThat(hint.getText()).contains("employee / employee123").doesNotContain("admin");
    }
}
