package com.bsmi.attendancesystem.scanner;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GridSampler;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.PerspectiveTransform;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.detector.Detector;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the QR codes in a photo, also when the card was photographed at an angle.
 *
 * <p>ZXing finds a code by its three big corner squares. The smallest codes, the kind a card ID
 * gets, have no other mark, so ZXing places the fourth corner as if the card lay flat, and a
 * tilted photo fails. When ZXing reads nothing, this reader tries fourth corners around that
 * guess, nearest first. The code's error correction turns down every wrong corner.
 */
public final class QrPhotoReader {
    // Phone photos are 12 megapixels or more; these sizes are plenty for a card and decode fast.
    // Corner squares that one size misses, another often finds.
    static final List<Integer> DECODE_SIZES_IN_PIXELS = List.of(1600, 1000);
    private static final Map<DecodeHintType, Object> LOOK_HARDER =
            Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    // The middle of a corner square is 3.5 squares in from the edges of the code
    private static final float CORNER_SQUARE_MIDDLE_IN_MODULES = 3.5f;
    private static final float CORNER_SEARCH_REACH_IN_MODULES = 6;
    private static final float CORNER_SEARCH_STEP_IN_MODULES = 0.5f;
    private static final List<Shift> CORNER_SHIFTS_NEAREST_FIRST = cornerShiftsNearestFirst();

    private QrPhotoReader() {}

    /** @return the text of each QR code in the photo, each text once; empty if none is readable */
    public static List<String> codesIn(BufferedImage photo) {
        Objects.requireNonNull(photo, "photo");
        for (int longestSide : decodeSizesFor(photo)) {
            BinaryBitmap blackAndWhite = blackAndWhite(shrunkTo(photo, longestSide));
            List<String> codes = codesZxingReads(blackAndWhite);
            if (!codes.isEmpty()) {
                return codes;
            }
            Optional<String> tiltedCode = codeFoundBySearchingTheFourthCorner(blackAndWhite);
            if (tiltedCode.isPresent()) {
                return List.of(tiltedCode.get());
            }
        }
        return List.of();
    }

    /** A photo smaller than a decode size is read at its own size, and only once. */
    private static Set<Integer> decodeSizesFor(BufferedImage photo) {
        int longest = Math.max(photo.getWidth(), photo.getHeight());
        Set<Integer> sizes = new LinkedHashSet<>();
        for (int size : DECODE_SIZES_IN_PIXELS) {
            sizes.add(Math.min(size, longest));
        }
        return sizes;
    }

    /** @return the photo shrunk to fit {@code longestSide}, keeping its shape; small ones as is */
    static BufferedImage shrunkTo(BufferedImage photo, int longestSide) {
        int longest = Math.max(photo.getWidth(), photo.getHeight());
        if (longest <= longestSide) {
            return photo;
        }
        double scale = (double) longestSide / longest;
        int width = Math.max(1, (int) Math.round(photo.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(photo.getHeight() * scale));
        BufferedImage smaller = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smaller.createGraphics();
        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(photo, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return smaller;
    }

    private static BinaryBitmap blackAndWhite(BufferedImage photo) {
        int width = photo.getWidth();
        int height = photo.getHeight();
        int[] pixels = photo.getRGB(0, 0, width, height, null, 0, width);
        return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
    }

    private static List<String> codesZxingReads(BinaryBitmap blackAndWhite) {
        try {
            return Arrays.stream(new QRCodeMultiReader().decodeMultiple(blackAndWhite, LOOK_HARDER))
                    .map(Result::getText)
                    .distinct()
                    .toList();
        } catch (NotFoundException noReadableCode) {
            return List.of();
        }
    }

    private static Optional<String> codeFoundBySearchingTheFourthCorner(
            BinaryBitmap blackAndWhite) {
        BitMatrix pixels;
        DetectorResult flatGuess;
        try {
            pixels = blackAndWhite.getBlackMatrix();
            flatGuess = new Detector(pixels).detect(LOOK_HARDER);
        } catch (ReaderException noCornerSquares) {
            return Optional.empty();
        }
        CornerSquares found = CornerSquares.of(flatGuess);
        for (Shift shift : CORNER_SHIFTS_NEAREST_FIRST) {
            ResultPoint fourthCorner = found.flatFourthCorner(shift);
            Optional<String> text = decodeWithFourthCornerAt(fourthCorner, found, pixels);
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> decodeWithFourthCornerAt(
            ResultPoint fourthCorner, CornerSquares found, BitMatrix pixels) {
        float near = CORNER_SQUARE_MIDDLE_IN_MODULES;
        float far = found.modulesPerSide() - CORNER_SQUARE_MIDDLE_IN_MODULES;
        PerspectiveTransform codeToPhoto =
                PerspectiveTransform.quadrilateralToQuadrilateral(
                        near, near, far, near, far, far, near, far,
                        found.topLeft().getX(), found.topLeft().getY(),
                        found.topRight().getX(), found.topRight().getY(),
                        fourthCorner.getX(), fourthCorner.getY(),
                        found.bottomLeft().getX(), found.bottomLeft().getY());
        try {
            int side = found.modulesPerSide();
            BitMatrix modules =
                    GridSampler.getInstance().sampleGrid(pixels, side, side, codeToPhoto);
            return Optional.of(new Decoder().decode(modules).getText());
        } catch (ReaderException wrongCorner) {
            return Optional.empty();
        }
    }

    private static List<Shift> cornerShiftsNearestFirst() {
        List<Shift> shifts = new ArrayList<>();
        float reach = CORNER_SEARCH_REACH_IN_MODULES;
        for (float across = -reach; across <= reach; across += CORNER_SEARCH_STEP_IN_MODULES) {
            for (float down = -reach; down <= reach; down += CORNER_SEARCH_STEP_IN_MODULES) {
                shifts.add(new Shift(across, down));
            }
        }
        shifts.sort(Comparator.comparingDouble(Shift::distance));
        return List.copyOf(shifts);
    }

    /** How far to move the fourth corner from the flat-card guess, in squares of the code. */
    private record Shift(float across, float down) {
        double distance() {
            return Math.hypot(across, down);
        }
    }

    /** The middles of the three corner squares ZXing found, and the size of the code. */
    private record CornerSquares(
            ResultPoint bottomLeft, ResultPoint topLeft, ResultPoint topRight, int modulesPerSide) {
        static CornerSquares of(DetectorResult flatGuess) {
            // ZXing lists them bottom left, top left, top right
            ResultPoint[] middles = flatGuess.getPoints();
            return new CornerSquares(
                    middles[0], middles[1], middles[2], flatGuess.getBits().getHeight());
        }

        /** Where the fourth corner would be on a flat card, moved by {@code shift}. */
        ResultPoint flatFourthCorner(Shift shift) {
            float moduleInPixels =
                    ResultPoint.distance(topLeft, topRight)
                            / (modulesPerSide - 2 * CORNER_SQUARE_MIDDLE_IN_MODULES);
            return new ResultPoint(
                    topRight.getX() - topLeft.getX() + bottomLeft.getX()
                            + shift.across() * moduleInPixels,
                    topRight.getY() - topLeft.getY() + bottomLeft.getY()
                            + shift.down() * moduleInPixels);
        }
    }
}
