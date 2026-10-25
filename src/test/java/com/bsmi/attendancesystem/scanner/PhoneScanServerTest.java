package com.bsmi.attendancesystem.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.StudentData;
import com.bsmi.attendancesystem.service.StudentCards;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/** Talks to the server the way the phone page does, over real HTTP on this computer. */
class PhoneScanServerTest {
    private static final String CHECKED_IN = "Asha Verma checked in 09:12";
    private static final BuggyReaderPlugin BUGGY_READER = new BuggyReaderPlugin();
    // Held here, as the logging system forgets loggers nobody holds
    private static final Logger SERVER_LOG = Logger.getLogger(PhoneScanServer.class.getName());

    private final HttpClient http = HttpClient.newHttpClient();
    // Card IDs the server passed on to the app
    private final List<String> received = new CopyOnWriteArrayList<>();
    private PhoneScanServer server;

    @AfterEach
    void stopServer() {
        IIORegistry.getDefaultInstance().deregisterServiceProvider(BUGGY_READER);
        if (server != null) {
            server.close();
        }
        http.close();
    }

    @Test
    void pageForThePhoneIsServedAtTheTokenAddress() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = get(page());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .contains("text/html; charset=utf-8");
        assertThat(response.body()).contains("<title>Card scanner", "Take photo", "fetch(\"scan\"");
    }

    @Test
    void pageShrinksPhotosToTheLargestSizeTheAppReads() throws Exception {
        start(checksEveryoneIn());

        Matcher longestSide =
                Pattern.compile("const LONGEST_SIDE = (\\d+);").matcher(get(page()).body());

        assertThat(longestSide.find()).isTrue();
        assertThat(Integer.parseInt(longestSide.group(1)))
                .isEqualTo(Collections.max(QrPhotoReader.DECODE_SIZES_IN_PIXELS));
    }

    @Test
    void answersAreNotCachedNorSniffedAndKeepTheTokenToThemselves() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = get(page());

        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(policy -> assertThat(policy).contains("default-src 'none'"));
    }

    @Test
    void photoOfACardChecksTheStudentIn() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postPhoto(png(StudentCards.draw(asha())));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(CHECKED_IN);
        assertThat(received).containsExactly("DEMO-1001");
    }

    @Test
    void bigPhonePhotoWithTheCardInACornerStillDecodes() throws Exception {
        start(checksEveryoneIn());
        BufferedImage photo = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, photo.getWidth(), photo.getHeight());
        BufferedImage card = StudentCards.draw(asha());
        g.drawImage(card, 2600, 1500, card.getWidth() * 3, card.getHeight() * 3, null);
        g.dispose();

        HttpResponse<String> response = postPhoto(png(photo));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received).containsExactly("DEMO-1001");
    }

    @Test
    void typedCardIdIsPassedOnWithoutSurroundingSpaces() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postTyped("  DEMO-1001 \n");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received).containsExactly("DEMO-1001");
    }

    @Test
    void typedCardIdOfTheLongestAllowedLengthIsPassedOn() throws Exception {
        start(checksEveryoneIn());
        String longest = "C".repeat(PhoneScanServer.MAX_CARD_ID_LENGTH);

        assertThat(postTyped(longest).statusCode()).isEqualTo(200);
        assertThat(received).containsExactly(longest);
    }

    @Test
    void typedCardIdLongerThanAnyCardIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response =
                postTyped("C".repeat(PhoneScanServer.MAX_CARD_ID_LENGTH + 1));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("A card ID is at most 64 characters");
        assertThat(received).isEmpty();
    }

    @Test
    void reasonTheAppRefusedTheCardIsShownOnThePhone() throws Exception {
        start(cardId -> ScanReply.failed("No student has card " + cardId));

        HttpResponse<String> response = postTyped("NOPE-1");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).isEqualTo("No student has card NOPE-1");
    }

    @Test
    void crashInTheAppGivesAServerErrorWithoutDetails() throws Exception {
        start(
                cardId -> {
                    throw new IllegalStateException("secret stack details");
                });

        HttpResponse<String> response = postTyped("DEMO-1001");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .isEqualTo("Something went wrong in the app. Try again")
                .doesNotContain("secret");
    }

    @Test
    void fileThatIsNotAPictureIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postPhoto("this is not a picture".getBytes());

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).startsWith("That file is not a photo");
        assertThat(received).isEmpty();
    }

    @Test
    void photoCutOffPartWayIsRefused() throws Exception {
        start(checksEveryoneIn());
        byte[] photo = png(StudentCards.draw(asha()));

        HttpResponse<String> response = postPhoto(Arrays.copyOf(photo, 40));

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).startsWith("That file is not a photo");
        assertThat(received).isEmpty();
    }

    @Test
    void photoCutOffInsideItsHeaderIsRefused() throws Exception {
        start(checksEveryoneIn());
        byte[] photo = png(StudentCards.draw(asha()));

        // 20 bytes: the PNG mark is there, the picture size is not
        HttpResponse<String> response = postPhoto(Arrays.copyOf(photo, 20));

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).startsWith("That file is not a photo");
        assertThat(received).isEmpty();
    }

    @Test
    void pictureWithoutAQrCodeAsksForAnotherTry() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response =
                postPhoto(png(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).startsWith("No QR code found in the photo");
        assertThat(received).isEmpty();
    }

    @Test
    void photoOverTheSizeLimitIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postPhoto(new byte[PhoneScanServer.MAX_PHOTO_BYTES + 1]);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).startsWith("The photo is over 15 MB");
        assertThat(received).isEmpty();
    }

    @Test
    void addressesWithoutTheRightTokenAreNotFound() throws Exception {
        start(checksEveryoneIn());
        String root = "http://127.0.0.1:" + server.port();
        String token = page().getPath().replace("/", "");
        String wrongToken = "0".repeat(token.length());

        for (String path :
                List.of(
                        "/",
                        "/scan",
                        "/" + wrongToken + "/",
                        "/" + wrongToken + "/scan",
                        "/" + token,
                        "/" + token + "/scan/more",
                        "/" + token.substring(1) + "/")) {
            HttpResponse<String> response = get(URI.create(root + path));
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.body()).as(path).isEqualTo("Not found");
        }
    }

    @Test
    void wrongMethodOnARealAddressIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> postToPage =
                send(HttpRequest.newBuilder(page()).POST(BodyPublishers.ofString("DEMO-1001")));
        HttpResponse<String> getScan = get(page().resolve("scan"));

        assertThat(postToPage.statusCode()).isEqualTo(405);
        assertThat(getScan.statusCode()).isEqualTo(405);
        assertThat(received).isEmpty();
    }

    @Test
    void everyStartMakesANewToken() throws Exception {
        start(checksEveryoneIn());
        URI first = page();
        server.close();

        start(checksEveryoneIn());

        assertThat(page().getPath()).isNotEqualTo(first.getPath()).matches("/[0-9a-f]{32}/");
    }

    @Test
    void pageAddressUsesTheGivenHostAndThePortTheServerGot() throws Exception {
        start(checksEveryoneIn());

        assertThat(server.port()).isPositive();
        assertThat(page().toString()).startsWith("http://127.0.0.1:" + server.port() + "/");
    }

    @Test
    void stoppedServerNoLongerAnswers() throws Exception {
        start(checksEveryoneIn());
        URI page = page();

        server.close();

        assertThatThrownBy(() -> get(page)).isInstanceOf(ConnectException.class);
    }

    @Test
    void portAlreadyInUseFailsToStart() throws Exception {
        start(checksEveryoneIn());
        InetSocketAddress taken =
                new InetSocketAddress(NetworkAddresses.thisComputerOnly(), server.port());

        assertThatThrownBy(() -> PhoneScanServer.start(taken, checksEveryoneIn()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void usualPortIsUsedWhenItIsFree() throws Exception {
        int freePort = freePort();

        server = PhoneScanServer.startPreferring(freePort, checksEveryoneIn());

        assertThat(server.port()).isEqualTo(freePort);
        assertThat(get(page()).statusCode()).isEqualTo(200);
    }

    @Test
    void anotherFreePortIsUsedWhenTheUsualOneIsTaken() throws Exception {
        try (PhoneScanServer first = PhoneScanServer.startPreferring(freePort(), checksEveryoneIn())) {
            server = PhoneScanServer.startPreferring(first.port(), checksEveryoneIn());

            assertThat(server.port()).isPositive().isNotEqualTo(first.port());
            assertThat(get(page()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void photoClaimingMorePixelsThanTheLimitIsRefusedWithoutDecodingIt() throws Exception {
        start(checksEveryoneIn());
        // 30000 by 30000 would need 3.6 GB once decoded; the file itself is tiny
        byte[] claimsToBeHuge = pngClaimingToBe(30_000, 30_000);

        HttpResponse<String> response = postPhoto(claimsToBeHuge);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).startsWith("The photo is over 64 megapixels");
        assertThat(received).isEmpty();
    }

    @Test
    void qrCodeTextOfTheLongestAllowedLengthIsPassedOn() throws Exception {
        start(checksEveryoneIn());
        String longest = "C".repeat(PhoneScanServer.MAX_CARD_ID_LENGTH);

        assertThat(postPhoto(png(photoOfCodes(longest))).statusCode()).isEqualTo(200);
        assertThat(received).containsExactly(longest);
    }

    @Test
    void qrCodeTextLongerThanAnyCardIsRefused() throws Exception {
        start(checksEveryoneIn());
        String tooLong = "C".repeat(PhoneScanServer.MAX_CARD_ID_LENGTH + 1);

        HttpResponse<String> response = postPhoto(png(photoOfCodes(tooLong)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("A card ID is at most 64 characters");
        assertThat(received).isEmpty();
    }

    @Test
    void qrCodeTextWithAControlCharacterIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postPhoto(png(photoOfCodes("DEMO-1001\u0007\r\nDROP")));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("A card ID can't contain control characters");
        assertThat(received).isEmpty();
    }

    @Test
    void typedCardIdWithAControlCharacterIsRefused() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postTyped("DEMO\u00001001");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("A card ID can't contain control characters");
        assertThat(received).isEmpty();
    }

    @Test
    void photoOfTwoCardsAsksForOneAtATime() throws Exception {
        start(checksEveryoneIn());

        HttpResponse<String> response = postPhoto(png(photoOfCodes("DEMO-1001", "DEMO-1002")));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body())
                .isEqualTo("The photo shows 2 QR codes. Photograph one card at a time");
        assertThat(received).isEmpty();
    }

    @Test
    void imageReaderThatCrashesCountsAsAFileTheAppCanNotRead() throws Exception {
        start(checksEveryoneIn());
        IIORegistry.getDefaultInstance().registerServiceProvider(BUGGY_READER);

        HttpResponse<String> response = postPhoto(BuggyReaderPlugin.READING_FAILS);

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).startsWith("That file is not a photo");
        assertThat(received).isEmpty();
    }

    @Test
    void crashWhileLookingForQrCodesAsksForAnotherTryAndIsLogged() throws Exception {
        start(checksEveryoneIn());
        IIORegistry.getDefaultInstance().registerServiceProvider(BUGGY_READER);
        List<LogRecord> logged = new CopyOnWriteArrayList<>();
        Handler keep = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        SERVER_LOG.addHandler(keep);
        try {
            HttpResponse<String> response = postPhoto(BuggyReaderPlugin.PIXELS_FAIL);

            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(response.body()).startsWith("No QR code found in the photo");
            assertThat(received).isEmpty();
            assertThat(logged)
                    .singleElement()
                    .satisfies(
                            record -> {
                                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                                assertThat(record.getThrown()).hasMessage("bug reading pixels");
                            });
        } finally {
            SERVER_LOG.removeHandler(keep);
        }
    }

    private void start(Function<String, ScanReply> onCardId) throws IOException {
        server =
                PhoneScanServer.start(
                        new InetSocketAddress(NetworkAddresses.thisComputerOnly(), 0), onCardId);
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private Function<String, ScanReply> checksEveryoneIn() {
        return cardId -> {
            received.add(cardId);
            return ScanReply.ok(CHECKED_IN);
        };
    }

    private URI page() {
        return server.pageAddress(NetworkAddresses.thisComputerOnly());
    }

    private HttpResponse<String> get(URI address) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(address).GET());
    }

    private HttpResponse<String> postPhoto(byte[] photo) throws IOException, InterruptedException {
        return post(BodyPublishers.ofByteArray(photo), "image/png");
    }

    private HttpResponse<String> postTyped(String cardId) throws IOException, InterruptedException {
        return post(BodyPublishers.ofString(cardId), "text/plain; charset=utf-8");
    }

    private HttpResponse<String> post(BodyPublisher body, String contentType)
            throws IOException, InterruptedException {
        return send(
                HttpRequest.newBuilder(page().resolve("scan"))
                        .header("Content-Type", contentType)
                        .POST(body));
    }

    private HttpResponse<String> send(HttpRequest.Builder request)
            throws IOException, InterruptedException {
        return http.send(request.build(), BodyHandlers.ofString());
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** A white photo with a QR code of each text side by side. */
    private static BufferedImage photoOfCodes(String... texts) {
        int codeSize = 300;
        int gap = 100;
        BufferedImage photo =
                new BufferedImage(
                        gap + texts.length * (codeSize + gap),
                        codeSize + 2 * gap,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, photo.getWidth(), photo.getHeight());
        for (int i = 0; i < texts.length; i++) {
            int left = gap + i * (codeSize + gap);
            g.drawImage(StudentCards.qrCode(texts[i], codeSize), left, gap, null);
        }
        g.dispose();
        return photo;
    }

    /** A real 10 by 10 PNG whose header says it is {@code width} by {@code height}. */
    private static byte[] pngClaimingToBe(int width, int height) {
        byte[] png = png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        // After the 8-byte signature comes the header chunk: length, "IHDR", width, height, ...
        ByteBuffer bytes = ByteBuffer.wrap(png);
        bytes.putInt(16, width);
        bytes.putInt(20, height);
        CRC32 checksum = new CRC32();
        checksum.update(png, 12, 4 + 13);
        bytes.putInt(29, (int) checksum.getValue());
        return png;
    }

    private static StudentData asha() {
        return new StudentData(
                1001,
                "1st Sem",
                "BSc",
                "Asha",
                "Verma",
                "Female",
                Date.valueOf("2005-03-14"),
                "Enrolled",
                "",
                "DEMO-1001");
    }

    /** An image reader plugin with a bug, for files that start with its own made-up mark. */
    private static final class BuggyReaderPlugin extends ImageReaderSpi {
        static final byte[] READING_FAILS = mark("BUGGY-READING-FAILS");
        static final byte[] PIXELS_FAIL = mark("BUGGY-PIXELS-FAIL");

        BuggyReaderPlugin() {
            vendorName = "tests";
            version = "1";
            names = new String[] {"buggy"};
            inputTypes = new Class<?>[] {ImageInputStream.class};
        }

        @Override
        public boolean canDecodeInput(Object input) throws IOException {
            return startsWith(input, READING_FAILS) || startsWith(input, PIXELS_FAIL);
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new BuggyReader(this);
        }

        @Override
        public String getDescription(Locale locale) {
            return "Image reader with a bug, for tests";
        }

        private static byte[] mark(String text) {
            return text.getBytes(StandardCharsets.US_ASCII);
        }

        static boolean startsWith(Object input, byte[] mark) throws IOException {
            ImageInputStream in = (ImageInputStream) input;
            byte[] start = new byte[mark.length];
            in.mark();
            try {
                in.readFully(start);
                return Arrays.equals(start, mark);
            } catch (EOFException shorterThanTheMark) {
                return false;
            } finally {
                in.reset();
            }
        }
    }

    private static final class BuggyReader extends ImageReader {
        BuggyReader(ImageReaderSpi plugin) {
            super(plugin);
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
            if (BuggyReaderPlugin.startsWith(getInput(), BuggyReaderPlugin.PIXELS_FAIL)) {
                return new PhotoWhosePixelsCanNotBeRead();
            }
            throw new IllegalStateException("bug in the image reader");
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return PhotoWhosePixelsCanNotBeRead.SIDE;
        }

        @Override
        public int getHeight(int imageIndex) {
            return PhotoWhosePixelsCanNotBeRead.SIDE;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            int rgb = BufferedImage.TYPE_INT_RGB;
            return List.of(ImageTypeSpecifier.createFromBufferedImageType(rgb)).iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }
    }

    private static final class PhotoWhosePixelsCanNotBeRead extends BufferedImage {
        static final int SIDE = 100;

        PhotoWhosePixelsCanNotBeRead() {
            super(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        }

        @Override
        public int[] getRGB(
                int x, int y, int width, int height, int[] pixels, int offset, int scanWidth) {
            throw new IllegalStateException("bug reading pixels");
        }
    }
}
