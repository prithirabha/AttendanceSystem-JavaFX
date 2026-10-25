package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.UserData;
import com.bsmi.attendancesystem.dao.DataAccessException;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.TestDatabases;
import com.bsmi.attendancesystem.dao.UserDao;
import com.bsmi.attendancesystem.service.AuthService.DemoLogin;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthServiceTest {
    private Database database;
    private AuthService auth;

    @BeforeEach
    void useFreshDatabase() {
        database = TestDatabases.migratedInMemory();
        auth = new AuthService(new UserDao(database), new Passwords(4));
    }

    @Test
    void demoAdminLogsInWithTheirRole() {
        assertThat(auth.login("admin", "admin123"))
                .get()
                .extracting(UserData::getUserName, UserData::getUserRole)
                .containsExactly("admin", "Admin");
    }

    @Test
    void demoEmployeeLogsInWithTheirRole() {
        assertThat(auth.login("employee", "employee123"))
                .get()
                .extracting(UserData::getUserRole)
                .isEqualTo("Employee");
    }

    @Test
    void usernameIsTrimmedButPasswordIsNot() {
        assertThat(auth.login("  admin ", "admin123")).isPresent();
        assertThat(auth.login("admin", " admin123")).isEmpty();
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThat(auth.login("admin", "Admin123")).isEmpty();
    }

    @Test
    void unknownUserIsRejected() {
        assertThat(auth.login("nobody", "admin123")).isEmpty();
    }

    @Test
    void injectionInUsernameIsRejected() {
        assertThat(auth.login("admin' OR '1'='1", "anything")).isEmpty();
    }

    @Test
    void corruptStoredHashIsRejectedInsteadOfCrashing() throws SQLException {
        TestDatabases.execute(database, "UPDATE admin SET password_hash = 'not-a-hash'");

        assertThat(auth.login("admin", "admin123")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankUsernameNamesTheUsernameField(String blank) {
        assertThatThrownBy(() -> auth.login(blank, "admin123"))
                .isInstanceOf(MissingFieldException.class)
                .hasMessage("Please fill in the Username field");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankPasswordNamesThePasswordField(String blank) {
        assertThatThrownBy(() -> auth.login("admin", blank))
                .isInstanceOf(MissingFieldException.class)
                .hasMessage("Please fill in the Password field");
    }

    @Test
    void freshDemoDataHasBothDemoLoginsWorking() {
        assertThat(auth.demoLoginsThatStillWork())
                .extracting(DemoLogin::username)
                .containsExactly("admin", "employee");
    }

    @Test
    void demoLoginWithAChangedPasswordIsNoLongerListed() throws SQLException {
        changePassword("admin", "Principal-Pass-2026");

        assertThat(auth.demoLoginsThatStillWork())
                .extracting(DemoLogin::username)
                .containsExactly("employee");
    }

    @Test
    void deletedDemoUserIsNoLongerListed() throws SQLException {
        TestDatabases.execute(database, "DELETE FROM admin WHERE username = 'employee'");

        assertThat(auth.demoLoginsThatStillWork())
                .extracting(DemoLogin::username)
                .containsExactly("admin");
    }

    @Test
    void renamedDemoUserIsNotListedUnderItsNewName() throws SQLException {
        TestDatabases.execute(
                database, "UPDATE admin SET username = 'reception' WHERE username = 'employee'");

        assertThat(auth.demoLoginsThatStillWork())
                .extracting(DemoLogin::username)
                .containsExactly("admin");
    }

    @Test
    void realInstallWithBothPasswordsChangedListsNoDemoLogins() throws SQLException {
        changePassword("admin", "Principal-Pass-2026");
        changePassword("employee", "Reception-Pass-2026");

        assertThat(auth.demoLoginsThatStillWork()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin123", "employee123"})
    void demoPasswordsAreRecognisedForAnyAccount(String demoPassword) {
        assertThat(AuthService.isPublicDemoPassword(demoPassword)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Admin123", "admin123 ", " employee123", "admin", "Strong-Pass-1"})
    void onlyTheExactDemoPasswordsCount(String other) {
        assertThat(AuthService.isPublicDemoPassword(other)).isFalse();
    }

    @Test
    void unreachableDatabaseIsReportedWhenCheckingDemoLogins() {
        AuthService broken = new AuthService(new UserDao(TestDatabases.unreachable()));

        assertThatThrownBy(broken::demoLoginsThatStillWork)
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void unreachableDatabaseIsReported() {
        AuthService broken = new AuthService(new UserDao(TestDatabases.unreachable()));

        assertThatThrownBy(() -> broken.login("admin", "admin123"))
                .isInstanceOf(DataAccessException.class);
    }

    private void changePassword(String username, String newPassword) throws SQLException {
        String hash = new Passwords(4).hash(newPassword);
        TestDatabases.execute(
                database,
                "UPDATE admin SET password_hash = '" + hash + "'"
                        + " WHERE username = '" + username + "'");
    }
}
