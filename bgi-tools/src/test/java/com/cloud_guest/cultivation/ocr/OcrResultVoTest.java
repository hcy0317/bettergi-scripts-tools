package com.cloud_guest.cultivation.ocr;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.cloud_guest.vo.OcrResultVo;
import com.cloud_guest.vo.TextBlockVo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcrResultVoTest {

    @Test
    void preservesAllDetectedBlocksAndTheirBounds() {
        TextBlock first = textBlock("摩拉", 0.98f,
                point(120, 40), point(220, 42), point(218, 72), point(118, 70));
        TextBlock second = textBlock("31964305", 0.99f,
                point(700, 40), point(810, 40), point(810, 72), point(700, 72));

        OcrResult source = mock(OcrResult.class);
        when(source.getTextBlocks()).thenReturn(new ArrayList<>(List.of(first, second)));
        when(source.getStrRes()).thenReturn("摩拉\n31964305");

        OcrResultVo result = new OcrResultVo(source);

        assertThat(result.getTextBlocks()).hasSize(2);
        assertThat(result.getResList()).containsExactly("摩拉", "31964305");
        assertThat(result.getTextBlocks().getFirst())
                .extracting(TextBlockVo::getX, TextBlockVo::getY,
                        TextBlockVo::getWidth, TextBlockVo::getHeight)
                .containsExactly(118, 40, 102, 32);
    }

    private static TextBlock textBlock(String text, float score, Point... points) {
        TextBlock block = mock(TextBlock.class);
        when(block.getText()).thenReturn(text);
        when(block.getBoxScore()).thenReturn(score);
        when(block.getBoxPoint()).thenReturn(new ArrayList<>(List.of(points)));
        return block;
    }

    private static Point point(int x, int y) {
        Point point = mock(Point.class);
        when(point.getX()).thenReturn(x);
        when(point.getY()).thenReturn(y);
        return point;
    }
}
