package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class GaugeView extends View {
    private float value;
    private float max = 120f;
    private int accent = Ui.ACCENT;
    private String phase = "准备就绪";
    private boolean active;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint phasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GaugeView(Context c) { super(c); init(); }
    public GaugeView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF183040);
        track.setStrokeWidth(Ui.dp(getContext(), 11));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(Ui.dp(getContext(), 11));
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(Ui.dp(getContext(), 1));
        tick.setColor(0x22FFFFFF);
        number.setColor(Ui.TEXT);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(Ui.MUTED);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Ui.mono(getContext()));
        phasePaint.setColor(Ui.MUTED);
        phasePaint.setTextAlign(Paint.Align.CENTER);
        phasePaint.setTypeface(Ui.medium(getContext()));
        maxPaint.setColor(Ui.DIM);
        maxPaint.setTextAlign(Paint.Align.CENTER);
        maxPaint.setTypeface(Ui.mono(getContext()));
    }

    public void reset(float max) {
        this.max = Math.max(1f, max);
        this.value = 0;
        this.phase = "准备就绪";
        this.active = false;
        this.accent = Ui.ACCENT;
        invalidate();
    }

    public void setValue(float value) {
        this.value = Math.max(0, value);
        if (this.value > max * 0.92f) {
            max = niceMax(this.value * 1.25f);
        }
        invalidate();
    }

    public void setPhase(String phase, int color) {
        this.phase = phase;
        this.accent = color;
        this.active = true;
        invalidate();
    }

    public void setIdlePhase(String phase) {
        this.phase = phase;
        this.active = false;
        invalidate();
    }

    private float niceMax(float raw) {
        if (raw <= 10) return 10;
        if (raw <= 20) return 20;
        if (raw <= 50) return 50;
        if (raw <= 100) return 100;
        if (raw <= 200) return 200;
        if (raw <= 500) return 500;
        if (raw <= 1000) return 1000;
        if (raw <= 2000) return 2000;
        if (raw <= 5000) return 5000;
        return 10000;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize(Ui.dp(getContext(), 300), wSpec);
        int h = resolveSize(Ui.dp(getContext(), 230), hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.49f;
        float radius = Math.min(getWidth() * 0.34f, getHeight() * 0.37f);
        if (radius < Ui.dp(getContext(), 50)) radius = Ui.dp(getContext(), 50);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        float start = 135f;
        float sweep = 270f;

        canvas.drawArc(oval, start, sweep, false, track);

        float fraction = Math.min(1f, value / max);
        if (fraction > 0.001f) {
            int segments = 64;
            float activeSweep = sweep * fraction;
            Paint seg = new Paint(arc);
            for (int i = 0; i < segments; i++) {
                float f = i / (float) Math.max(1, segments - 1);
                float a = start + activeSweep * i / segments;
                float b = start + activeSweep * (i + 1) / segments + 0.35f;
                float t = fraction > 0 ? f / fraction : 0;
                seg.setColor(Ui.blend(accent, Ui.BLUE, Math.min(1f, t * 0.9f)));
                seg.setAlpha(230);
                canvas.drawArc(oval, a, Math.max(0.5f, b - a), false, seg);
            }
            // Endpoint glow.
            double endAngle = Math.toRadians(start + activeSweep);
            float ex = (float) (cx + Math.cos(endAngle) * radius);
            float ey = (float) (cy + Math.sin(endAngle) * radius);
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(Ui.withAlpha(accent, 0.20f));
            canvas.drawCircle(ex, ey, Ui.dp(getContext(), 10), glow);
            glow.setColor(accent);
            canvas.drawCircle(ex, ey, Ui.dp(getContext(), 3.4f), glow);
        }

        for (int i = 0; i <= 24; i++) {
            double angle = Math.toRadians(start - 5 + (sweep + 10) * i / 24f);
            float outer = radius + Ui.dp(getContext(), 14);
            float inner = outer - (i % 6 == 0 ? Ui.dp(getContext(), 7) : Ui.dp(getContext(), 3));
            float x1 = (float) (cx + Math.cos(angle) * inner);
            float y1 = (float) (cy + Math.sin(angle) * inner);
            float x2 = (float) (cx + Math.cos(angle) * outer);
            float y2 = (float) (cy + Math.sin(angle) * outer);
            tick.setColor(i % 6 == 0 ? 0x44FFFFFF : 0x20FFFFFF);
            canvas.drawLine(x1, y1, x2, y2, tick);
        }

        number.setTextSize(Ui.sp(getContext(), 48));
        String shown = value >= 100 ? String.format(Locale.US, "%.0f", value)
                : value >= 10 ? String.format(Locale.US, "%.1f", value)
                : String.format(Locale.US, "%.2f", value);
        float baseY = cy + Ui.dp(getContext(), 7);
        canvas.drawText(shown, cx, baseY, number);

        unit.setTextSize(Ui.sp(getContext(), 10.5f));
        canvas.drawText("Mbps", cx, baseY + Ui.dp(getContext(), 20), unit);

        phasePaint.setTextSize(Ui.sp(getContext(), 12f));
        phasePaint.setColor(active ? Ui.blend(accent, Ui.TEXT, 0.25f) : Ui.MUTED);
        canvas.drawText(phase, cx, cy + radius * 0.62f, phasePaint);

        maxPaint.setTextSize(Ui.sp(getContext(), 8.5f));
        canvas.drawText("量程 0–" + String.format(Locale.US, "%.0f", max) + " Mbps",
                cx, cy + radius + Ui.dp(getContext(), 27), maxPaint);
    }
}
