package com.example.therapyai.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class AudioVisualizerView extends View {

    private static final int MAX_POINTS = 200;

    private float[] amplitudes;
    private int index = 0;
    private float lastAmplitude = 0;

    private Paint paint;
    private Paint bgPaint;
    private boolean isRecording = false;

    public AudioVisualizerView(Context context) {
        super(context);
        init();
    }

    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        amplitudes = new float[MAX_POINTS];

        paint = new Paint();
        paint.setStrokeWidth(3f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#F0F0F0"));
    }

    /**
     * Update wave with a new amplitude value
     * @param amplitude the new amplitude (0 - 32767 range typical)
     */
    public void updateAmplitude(float amplitude) {
        float scaledAmplitude = Math.min(amplitude / 200.0f, 100f);

        if (Math.abs(scaledAmplitude - lastAmplitude) > 40) {
            lastAmplitude = lastAmplitude + (scaledAmplitude - lastAmplitude) * 0.7f;
        } else {
            lastAmplitude = lastAmplitude + (scaledAmplitude - lastAmplitude) * 0.3f;
        }

        amplitudes[index] = lastAmplitude;
        index++;
        if (index >= MAX_POINTS) {
            index = 0;
        }

        isRecording = true;
        invalidate(); // redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;

        canvas.drawRect(0, 0, width, height, bgPaint);

        LinearGradient gradient = new LinearGradient(
                0, centerY - 100, 0, centerY + 100,
                Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"),
                Shader.TileMode.MIRROR);
        paint.setShader(gradient);

        if (!isRecording) {
            canvas.drawLine(0, centerY, width, centerY, paint);
            return;
        }

        float spacing = width / (float)MAX_POINTS;
        float x = 0;

        int currentIndex = index;
        for (int i = 0; i < MAX_POINTS; i++) {
            int drawIndex = (currentIndex + i) % MAX_POINTS;
            float y = amplitudes[drawIndex];

            float posY1 = centerY - y;
            float posY2 = centerY + y;

            if (i > 0) {
                float prevX = x - spacing;
                float prevY1 = centerY - amplitudes[(drawIndex - 1 + MAX_POINTS) % MAX_POINTS];
                float prevY2 = centerY + amplitudes[(drawIndex - 1 + MAX_POINTS) % MAX_POINTS];

                canvas.drawLine(prevX, prevY1, x, posY1, paint);
                canvas.drawLine(prevX, prevY2, x, posY2, paint);
            }
            x += spacing;
        }
    }

    public void resetWaveform() {
        isRecording = false;
        for (int i = 0; i < MAX_POINTS; i++) {
            amplitudes[i] = 0;
        }
        invalidate();
    }
}