package com.bsmi.attendancesystem.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.PerspectiveTransform;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;

class QrPhotoReaderTest {
    private static final int PHOTO_SIDE = 1000;

    @Test
    void cardPhotographedAtAnAngleIsRead() {
        BufferedImage tilted = photographedAtAnAngle(qrCode("DEMO-1001", 1), 0.3);

        assertThat(QrPhotoReader.codesIn(tilted)).containsExactly("DEMO-1001");
    }

    @Test
    void plainZxingCanNotReadThatTiltedCardWhichIsWhyTheCornerSearchExists() {
        BufferedImage tilted = photographedAtAnAngle(qrCode("DEMO-1001", 1), 0.3);

        assertThatThrownBy(() -> new QRCodeReader().decode(blackAndWhite(tilted)))
                .isInstanceOf(ReaderException.class);
    }

    @Test
    void biggerKindOfQrCodeAtAnAngleIsReadToo() {
        BufferedImage tilted = photographedAtAnAngle(qrCode("DEMO-1001", 2), 0.3);

        assertThat(QrPhotoReader.codesIn(tilted)).containsExactly("DEMO-1001");
    }

    @Test
    void everyCodeInThePhotoIsReturned() {
        BufferedImage photo = whitePhoto(900, 500);
        Graphics2D g = photo.createGraphics();
        g.drawImage(toImage(qrCode("DEMO-1001", 1)), 40, 40, 360, 360, null);
        g.drawImage(toImage(qrCode("DEMO-1002", 1)), 500, 40, 360, 360, null);
        g.dispose();

        assertThat(QrPhotoReader.codesIn(photo))
                .containsExactlyInAnyOrder("DEMO-1001", "DEMO-1002");
    }

    @Test
    void sameCodeTwiceInThePhotoIsReturnedOnce() {
        BufferedImage photo = whitePhoto(900, 500);
        Graphics2D g = photo.createGraphics();
        BufferedImage code = toImage(qrCode("DEMO-1001", 1));
        g.drawImage(code, 40, 40, 360, 360, null);
        g.drawImage(code, 500, 40, 360, 360, null);
        g.dispose();

        assertThat(QrPhotoReader.codesIn(photo)).containsExactly("DEMO-1001");
    }

    @Test
    void codeWithItsCornerSquaresButADamagedMiddleHasNoCodes() {
        BitMatrix damaged = qrCode("DEMO-1001", 1);
        // Corner squares fill the outer 8 of 21 squares; this hole is inside them
        for (int y = 320; y < 480; y++) {
            for (int x = 320; x < 480; x++) {
                damaged.flip(x, y);
            }
        }
        BufferedImage photo = whitePhoto(PHOTO_SIDE, PHOTO_SIDE);
        Graphics2D g = photo.createGraphics();
        g.drawImage(toImage(damaged), 100, 100, null);
        g.dispose();

        assertThat(QrPhotoReader.codesIn(photo)).isEmpty();
    }

    @Test
    void blankPhotoHasNoCodes() {
        assertThat(QrPhotoReader.codesIn(whitePhoto(400, 400))).isEmpty();
    }

    @Test
    void onePixelPhotoHasNoCodes() {
        assertThat(QrPhotoReader.codesIn(whitePhoto(1, 1))).isEmpty();
    }

    @Test
    void noiseHasNoCodes() {
        Random seeded = new Random(42);
        BufferedImage noise = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < noise.getHeight(); y++) {
            for (int x = 0; x < noise.getWidth(); x++) {
                noise.setRGB(x, y, seeded.nextBoolean() ? 0xFFFFFF : 0x000000);
            }
        }

        assertThat(QrPhotoReader.codesIn(noise)).isEmpty();
    }

    @Test
    void noPhotoIsABugInTheCaller() {
        assertThatThrownBy(() -> QrPhotoReader.codesIn(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("photo");
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000})
    void photoThatAlreadyFitsIsUsedAsItIs(int side) {
        BufferedImage small = new BufferedImage(side, 600, BufferedImage.TYPE_INT_RGB);

        assertThat(QrPhotoReader.shrunkTo(small, 1000)).isSameAs(small);
    }

    @Test
    void bigPhotoIsShrunkKeepingItsShape() {
        BufferedImage landscape = QrPhotoReader.shrunkTo(blankPhoto(4000, 3000), 1600);
        BufferedImage portrait = QrPhotoReader.shrunkTo(blankPhoto(3000, 4000), 1600);

        assertThat(landscape.getWidth()).isEqualTo(1600);
        assertThat(landscape.getHeight()).isEqualTo(1200);
        assertThat(portrait.getWidth()).isEqualTo(1200);
        assertThat(portrait.getHeight()).isEqualTo(1600);
    }

    @Test
    void verySkinnyPhotoKeepsAtLeastOnePixel() {
        BufferedImage line = QrPhotoReader.shrunkTo(blankPhoto(8000, 1), 1000);

        assertThat(line.getWidth()).isEqualTo(1000);
        assertThat(line.getHeight()).isEqualTo(1);
    }

    private static BitMatrix qrCode(String text, int version) {
        Map<EncodeHintType, Object> hints =
                Map.of(
                        EncodeHintType.MARGIN, 0,
                        EncodeHintType.QR_VERSION, version,
                        EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 800, 800, hints);
        } catch (WriterException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * The code as a photo taken at an angle: its bottom edge looks shorter by {@code shortening},
     * a share of the top edge's length.
     */
    private static BufferedImage photographedAtAnAngle(BitMatrix code, double shortening) {
        float inset = (float) (400 * shortening);
        int size = code.getWidth();
        PerspectiveTransform photoToCode =
                PerspectiveTransform.quadrilateralToQuadrilateral(
                        100, 100, 900, 100, 900 - inset, 900, 100 + inset, 900,
                        0, 0, size, 0, size, size, 0, size);
        BufferedImage photo = new BufferedImage(PHOTO_SIDE, PHOTO_SIDE, BufferedImage.TYPE_INT_RGB);
        float[] point = new float[2];
        for (int y = 0; y < PHOTO_SIDE; y++) {
            for (int x = 0; x < PHOTO_SIDE; x++) {
                point[0] = x;
                point[1] = y;
                photoToCode.transformPoints(point);
                int codeX = (int) point[0];
                int codeY = (int) point[1];
                boolean black =
                        codeX >= 0 && codeY >= 0 && codeX < size && codeY < size
                                && code.get(codeX, codeY);
                // Grey on off-white, as a camera sees print
                photo.setRGB(x, y, black ? 0x333333 : 0xEEEEEE);
            }
        }
        return photo;
    }

    private static BufferedImage toImage(BitMatrix code) {
        BufferedImage image =
                new BufferedImage(code.getWidth(), code.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < code.getHeight(); y++) {
            for (int x = 0; x < code.getWidth(); x++) {
                image.setRGB(x, y, code.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }

    private static BufferedImage blankPhoto(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static BufferedImage whitePhoto(int width, int height) {
        BufferedImage photo = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = photo.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return photo;
    }

    private static BinaryBitmap blackAndWhite(BufferedImage photo) {
        int width = photo.getWidth();
        int height = photo.getHeight();
        int[] pixels = photo.getRGB(0, 0, width, height, null, 0, width);
        return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    }
}
