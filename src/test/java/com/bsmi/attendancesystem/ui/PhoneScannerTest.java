package com.bsmi.attendancesystem.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bsmi.attendancesystem.scanner.NetworkAddresses;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

/** Plays the phone: turns the scanner on, then sends card IDs the way the phone page does. */
class PhoneScannerTest extends AppScreenTest {
    private static final String SCANNER_PORT_PROPERTY = "attendance.scanner.port";
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void openAttendance() {
        logInAndDismissWelcome("admin", "admin123");
        clickOn("#studentAttendance_btn");
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void closeClient() {
        http.close();
    }

    @Test
    void scannerIsOffUntilTurnedOn() {
        assertThat(phoneScanner().isSelected()).isFalse();
        assertThat(lookup("#studentAttendance_phoneQr").queryAs(ImageView.class).isVisible())
                .isFalse();
        assertThat(pageAddressField().isVisible()).isFalse();
    }

    @Test
    void turningItOnShowsThePairingCodeAndAddress() {
        clickOn(phoneScanner());

        ImageView pairingCode = lookup("#studentAttendance_phoneQr").queryAs(ImageView.class);
        assertThat(pairingCode.isVisible()).isTrue();
        assertThat(pairingCode.getImage()).isNotNull();
        assertThat(pageAddressField().getText()).matches("http://[0-9.]+:\\d+/[0-9a-f]{32}/");
    }

    @Test
    void hintNamesThePortTheFirewallMustAllow() {
        assumeTrue(!NetworkAddresses.forPhones().isEmpty(), "needs a network a phone could use");
        clickOn(phoneScanner());

        int port = URI.create(pageAddressField().getText()).getPort();
        assertThat(phoneHint()).contains("allow TCP port " + port + " ");
    }

    @Test
    void hintSaysWhenTheWantedPortIsBusy() throws IOException {
        assumeTrue(!NetworkAddresses.forPhones().isEmpty(), "needs a network a phone could use");
        try (ServerSocket otherProgram = new ServerSocket(0)) {
            int busyPort = otherProgram.getLocalPort();
            System.setProperty(SCANNER_PORT_PROPERTY, String.valueOf(busyPort));
            clickOn(phoneScanner());

            int port = URI.create(pageAddressField().getText()).getPort();
            assertThat(port).isNotEqualTo(busyPort);
            assertThat(phoneHint())
                    .startsWith("Port " + busyPort + " is busy, so this uses " + port + ".")
                    .contains("allow TCP port " + port + " ");
        } finally {
            System.clearProperty(SCANNER_PORT_PROPERTY);
        }
    }

    @Test
    void hintDoesNotMentionABusyPortWhenTheWantedOneIsFree() throws IOException {
        assumeTrue(!NetworkAddresses.forPhones().isEmpty(), "needs a network a phone could use");
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        System.setProperty(SCANNER_PORT_PROPERTY, String.valueOf(freePort));
        try {
            clickOn(phoneScanner());

            assertThat(URI.create(pageAddressField().getText()).getPort()).isEqualTo(freePort);
            assertThat(phoneHint()).doesNotContain("busy");
        } finally {
            System.clearProperty(SCANNER_PORT_PROPERTY);
        }
    }

    @Test
    void cardSentFromThePhoneChecksTheStudentInOnTheScreenToo() throws Exception {
        clickOn(phoneScanner());

        HttpResponse<String> response = sendFromPhone("DEMO-1001");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("Asha Verma checked in ");
        assertThat(scanResult()).isEqualTo(response.body());
    }

    @Test
    void unknownCardFromThePhoneShowsWhyOnBoth() throws Exception {
        clickOn(phoneScanner());

        HttpResponse<String> response = sendFromPhone("NOPE-1");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).isEqualTo("No student has card NOPE-1");
        assertThat(scanResult()).isEqualTo("No student has card NOPE-1");
    }

    @Test
    void turningItOffStopsThePhonePage() {
        clickOn(phoneScanner());
        URI page = reachablePage();

        clickOn(phoneScanner());

        assertThat(pageAddressField().isVisible()).isFalse();
        assertThatThrownBy(() -> get(page)).isInstanceOf(ConnectException.class);
    }

    @Test
    void loggingOutStopsThePhonePage() {
        clickOn(phoneScanner());
        URI page = reachablePage();

        clickOn("#logout_btn");
        pressAlertButton("OK");

        assertThatThrownBy(() -> get(page)).isInstanceOf(ConnectException.class);
    }

    @Test
    void turningItOnAgainMakesANewAddress() {
        clickOn(phoneScanner());
        String first = pageAddressField().getText();
        clickOn(phoneScanner());

        clickOn(phoneScanner());

        assertThat(pageAddressField().getText()).isNotEqualTo(first);
    }

    private HttpResponse<String> sendFromPhone(String cardId)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(reachablePage().resolve("scan"))
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(cardId))
                        .build();
        return http.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(URI page) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(page).GET().build(), BodyHandlers.ofString());
    }

    // The server listens on every network, so this computer's own address works on any test machine
    private URI reachablePage() {
        URI shown = URI.create(pageAddressField().getText());
        return URI.create("http://127.0.0.1:" + shown.getPort() + shown.getPath());
    }

    private CheckBox phoneScanner() {
        return lookup("#studentAttendance_phoneScanner").queryAs(CheckBox.class);
    }

    private TextField pageAddressField() {
        return lookup("#studentAttendance_phoneAddress").queryAs(TextField.class);
    }

    private String phoneHint() {
        return lookup("#studentAttendance_phoneHint").queryAs(Label.class).getText();
    }

    private String scanResult() {
        return lookup("#studentAttendance_scanResult").queryAs(Label.class).getText();
    }
}
