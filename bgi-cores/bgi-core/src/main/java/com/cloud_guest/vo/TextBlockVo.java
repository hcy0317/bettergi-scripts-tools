package com.cloud_guest.vo;

import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

/**
 * @Author yan
 * @Date 2025/9/22 14:41:51
 * @Description
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextBlockVo {
    private ArrayList<Point> boxPoint;
    private float boxScore;
    private int angleIndex;
    private float angleScore;
    private double angleTime;
    private String text;
    private float[] charScores;
    private double crnnTime;
    private double blockTime;
    private int x;
    private int y;
    private int width;
    private int height;

    public TextBlockVo(TextBlock textBlock) {
        this.boxPoint = textBlock.getBoxPoint();
        this.boxScore = textBlock.getBoxScore();
        this.angleIndex = textBlock.getAngleIndex();
        this.angleScore = textBlock.getAngleScore();
        this.angleTime = textBlock.getAngleTime();
        this.text = textBlock.getText();
        this.charScores = textBlock.getCharScores();
        this.crnnTime = textBlock.getCrnnTime();
        this.blockTime = textBlock.getBlockTime();

        if (this.boxPoint == null || this.boxPoint.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point point : this.boxPoint) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        this.x = minX;
        this.y = minY;
        this.width = maxX - minX;
        this.height = maxY - minY;
    }
}
