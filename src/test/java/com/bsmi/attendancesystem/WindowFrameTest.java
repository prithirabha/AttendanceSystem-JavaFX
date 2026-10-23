package com.bsmi.attendancesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.WindowFrame.Edges;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import org.junit.jupiter.api.Test;

class WindowFrameTest {
    private static final Rectangle2D WINDOW = new Rectangle2D(100, 50, 1100, 600);
    private static final Edges RIGHT = new Edges(false, true, false, false);
    private static final Edges LEFT = new Edges(true, false, false, false);
    private static final Edges TOP_LEFT = new Edges(true, false, true, false);
    private static final Edges BOTTOM_RIGHT = new Edges(false, true, false, true);

    @Test
    void pressInTheMiddleTouchesNoEdge() {
        Edges edges = WindowFrame.edgesAt(500, 300, 1100, 600);

        assertThat(edges).isEqualTo(Edges.NONE);
        assertThat(edges.any()).isFalse();
        assertThat(edges.cursor()).isEqualTo(Cursor.DEFAULT);
    }

    @Test
    void pressNearACornerTouchesBothEdges() {
        assertThat(WindowFrame.edgesAt(2, 3, 1100, 600)).isEqualTo(TOP_LEFT);
        assertThat(WindowFrame.edgesAt(1098, 597, 1100, 600)).isEqualTo(BOTTOM_RIGHT);
    }

    @Test
    void edgeBandIsResizeBorderWide() {
        double inside = WindowFrame.RESIZE_BORDER;

        assertThat(WindowFrame.edgesAt(inside, 300, 1100, 600).any()).isFalse();
        assertThat(WindowFrame.edgesAt(inside - 1, 300, 1100, 600)).isEqualTo(LEFT);
        assertThat(WindowFrame.edgesAt(1100 - inside, 300, 1100, 600)).isEqualTo(RIGHT);
    }

    @Test
    void windowNarrowerThanTwoBordersResizesFromTheLeftAndTop() {
        assertThat(WindowFrame.edgesAt(4, 4, 8, 8)).isEqualTo(TOP_LEFT);
    }

    @Test
    void eachEdgeCombinationHasItsOwnCursor() {
        assertThat(TOP_LEFT.cursor()).isEqualTo(Cursor.NW_RESIZE);
        assertThat(new Edges(false, true, true, false).cursor()).isEqualTo(Cursor.NE_RESIZE);
        assertThat(new Edges(false, false, true, false).cursor()).isEqualTo(Cursor.N_RESIZE);
        assertThat(new Edges(true, false, false, true).cursor()).isEqualTo(Cursor.SW_RESIZE);
        assertThat(BOTTOM_RIGHT.cursor()).isEqualTo(Cursor.SE_RESIZE);
        assertThat(new Edges(false, false, false, true).cursor()).isEqualTo(Cursor.S_RESIZE);
        assertThat(LEFT.cursor()).isEqualTo(Cursor.W_RESIZE);
        assertThat(RIGHT.cursor()).isEqualTo(Cursor.E_RESIZE);
    }

    @Test
    void draggingTheBottomRightCornerGrowsTheWindowInPlace() {
        assertThat(WindowFrame.resized(WINDOW, BOTTOM_RIGHT, 100, 40, 1100, 600))
                .isEqualTo(new Rectangle2D(100, 50, 1200, 640));
    }

    @Test
    void draggingTheTopLeftCornerKeepsTheBottomRightCornerInPlace() {
        assertThat(WindowFrame.resized(WINDOW, TOP_LEFT, -100, -40, 1100, 600))
                .isEqualTo(new Rectangle2D(0, 10, 1200, 640));
    }

    @Test
    void windowNeverGetsSmallerThanTheMinimum() {
        assertThat(WindowFrame.resized(WINDOW, BOTTOM_RIGHT, -500, -500, 1000, 550))
                .isEqualTo(new Rectangle2D(100, 50, 1000, 550));
    }

    @Test
    void shrinkingFromTheLeftStopsAtTheMinimumWithoutMovingTheRightEdge() {
        assertThat(WindowFrame.resized(WINDOW, LEFT, 500, 0, 1000, 550))
                .isEqualTo(new Rectangle2D(200, 50, 1000, 600));
    }

    @Test
    void draggingNoEdgeChangesNothing() {
        assertThat(WindowFrame.resized(WINDOW, Edges.NONE, 30, 30, 1, 1)).isEqualTo(WINDOW);
    }

    @Test
    void windowSmallerThanTheScreenOpensCentredAtItsSize() {
        Rectangle2D screen = new Rectangle2D(0, 0, 1920, 1080);

        assertThat(WindowFrame.fitted(1100, 600, screen))
                .isEqualTo(new Rectangle2D(410, 240, 1100, 600));
    }

    @Test
    void windowLargerThanTheScreenIsShrunkToFit() {
        Rectangle2D screen = new Rectangle2D(0, 0, 1024, 576);

        assertThat(WindowFrame.fitted(1100, 600, screen)).isEqualTo(screen);
    }

    @Test
    void windowIsCentredOnAScreenThatDoesNotStartAtZero() {
        // A second monitor to the right, below a top panel
        Rectangle2D screen = new Rectangle2D(1920, 30, 1280, 770);

        assertThat(WindowFrame.fitted(1100, 600, screen))
                .isEqualTo(new Rectangle2D(2010, 115, 1100, 600));
    }

    @Test
    void minimumSizeMustBePositive() {
        assertThatThrownBy(() -> WindowFrame.makeResizable(null, 0, 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.0x600.0");
        assertThatThrownBy(() -> WindowFrame.makeResizable(null, 1100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
