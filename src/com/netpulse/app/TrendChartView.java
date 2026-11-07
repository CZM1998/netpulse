package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class TrendChartView extends View {
    private float[] data = new float[0];
    private int lineColor = Ui.ACCENT;
    private float fixedMin = Float.NaN;
    private float fixedMax = Float.NaN;
    private boolean showLabels = true;
    private String emptyText = "等待采样…";

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TrendChartView(Context c) { super(c); init(); }
    public TrendChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(Ui.dp(getContext(), 2.0f));
        fill.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Ui.dp(getContext(), 1));
        text.setColor(Ui.DIM);
        text.setTextSize(Ui.sp(getContext(), 8));
        text.setTypeface(Ui.mono(getContext()));
    }

    public void setData(float[] values) {
        data = values == null ? new float[0] : values.clone();
        invalidate();
    }

    public void setColor(int color) {
        lineColor = color;
        invalidate();
    }

    public void setFixedRange(float min, float max) {
        fixedMin = min;
        fixedMax = max;
        invalidate();
    }

    public void setShowLabels(boolean show) {
        showLabels = show;
        invalidate();
    }

    public void setEmptyText(String text) {
        emptyText = text;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize(Ui.dp(getContext(), 300), wSpec);
        int h = resolveSize(Ui.dp(getContext(), 82), hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = showLabels ? Ui.dp(getContext(), 27) : Ui.dp(getContext(), 3);
        float right = Ui.dp(getContext(), 6);
        float top = Ui.dp(getContext(), 7);
        float bottom = Ui.dp(getContext(), 9);
        float chartW = getWidth() - left - right;
        float chartH = getHeight() - top - bottom;
        if (chartW <= 4 || chartH <= 4) return;

        float min;
        float max;
        if (!Float.isNaN(fixedMin) && !Float.isNaN(fixedMax)) {
            min = fixedMin;
            max = fixedMax;
        } else {
            min = Float.MAX_VALUE;
            max = -Float.MAX_VALUE;
            for (float v : data) {
                if (Float.isFinite(v)) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            if (min == Float.MAX_VALUE) {
                min = 0;
                max = 1;
            }
            if (max - min < 1f) {
                max += 1f;
                min -= 1f;
            }
            float pad = (max - min) * 0.14f;
            min -= pad;
            max += pad;
        }
        if (max <= min) max = min + 1f;

        grid.setColor(0x15FFFFFF);
        for (int i = 0; i < 3; i++) {
            float y = top + chartH * i / 2f;
            canvas.drawLine(left, y, getWidth() - right, y, grid);
        }
        if (showLabels) {
            canvas.drawText(fmt(max), 0, top + Ui.dp(getContext(), 3), text);
            canvas.drawText(fmt(min), 0, top + chartH, text);
        }

        if (data.length == 0) {
            text.setColor(Ui.DIM);
            text.setTextSize(Ui.sp(getContext(), 10));
            text.setTypeface(Ui.regular(getContext()));
            float tw = text.measureText(emptyText);
            canvas.drawText(emptyText, left + (chartW - tw) / 2f, top + chartH / 2f + Ui.dp(getContext(), 3), text);
            text.setTextSize(Ui.sp(getContext(), 8));
            text.setTypeface(Ui.mono(getContext()));
            return;
        }

        int count = data.length;
        float[] xs = new float[count];
        float[] ys = new float[count];
        int valid = 0;
        for (int i = 0; i < count; i++) {
            xs[i] = left + (count == 1 ? chartW / 2f : chartW * i / (count - 1f));
            float v = data[i];
            if (!Float.isFinite(v)) v = min;
            ys[i] = top + chartH - (v - min) / (max - min) * chartH;
            valid++;
        }
        if (valid == 0) return;

        Path linePath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            float midY = (ys[i - 1] + ys[i]) / 2f;
            linePath.quadTo(xs[i - 1], ys[i - 1], midX, midY);
            if (i == count - 1) linePath.lineTo(xs[i], ys[i]);
        }

        Path area = new Path(linePath);
        area.lineTo(xs[count - 1], top + chartH);
        area.lineTo(xs[0], top + chartH);
        area.close();
        fill.setShader(new LinearGradient(0, top, 0, top + chartH,
                Ui.withAlpha(lineColor, 0.28f), Ui.withAlpha(lineColor, 0.0f), Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        fill.setShader(null);

        line.setColor(Ui.withAlpha(lineColor, 0.18f));
        line.setStrokeWidth(Ui.dp(getContext(), 6f));
        canvas.drawPath(linePath, line);
        line.setColor(lineColor);
        line.setStrokeWidth(Ui.dp(getContext(), 2f));
        canvas.drawPath(linePath, line);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(lineColor);
        canvas.drawCircle(xs[count - 1], ys[count - 1], Ui.dp(getContext(), 3f), dot);
        dot.setColor(0xCC07111B);
        canvas.drawCircle(xs[count - 1], ys[count - 1], Ui.dp(getContext(), 1.2f), dot);
    }

    private String fmt(float value) {
        if (Math.abs(value) >= 100) return String.format(Locale.US, "%.0f", value);
        if (Math.abs(value) >= 10) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.1f", value);
    }
}
