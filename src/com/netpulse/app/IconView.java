package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Tiny vector icon renderer so the app has no external UI dependencies. */
public class IconView extends View {
    private String icon = "wifi";
    private int color = Ui.TEXT;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public IconView(Context context) {
        super(context);
        init();
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setIcon(String icon) {
        this.icon = icon == null ? "" : icon;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(Ui.dp(getContext(), 24), widthMeasureSpec);
        int h = resolveSize(Ui.dp(getContext(), 24), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float scale = size / 24f;
        canvas.save();
        float pad = 1.9f * scale;
        canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
        paint.setColor(color);
        paint.setStrokeWidth(1.75f * scale);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        switch (icon) {
            case "wifi": drawWifi(canvas, scale); break;
            case "signal": drawSignal(canvas, scale); break;
            case "overview": drawOverview(canvas, scale); break;
            case "speed": drawSpeed(canvas, scale); break;
            case "tools": drawTools(canvas, scale); break;
            case "refresh": drawRefresh(canvas, scale); break;
            case "scan": drawScan(canvas, scale); break;
            case "chevron": drawChevron(canvas, scale); break;
            case "lock": drawLock(canvas, scale); break;
            case "globe": drawGlobe(canvas, scale); break;
            case "share": drawShare(canvas, scale); break;
            case "settings": drawSettings(canvas, scale); break;
            case "down": drawArrow(canvas, scale, false); break;
            case "up": drawArrow(canvas, scale, true); break;
            case "check": drawCheck(canvas, scale); break;
            case "warning": drawWarning(canvas, scale); break;
            case "sim": drawSim(canvas, scale); break;
            case "search": drawSearch(canvas, scale); break;
            case "close": drawClose(canvas, scale); break;
            case "external": drawExternal(canvas, scale); break;
            case "route": drawRoute(canvas, scale); break;
            case "layers": drawLayers(canvas, scale); break;
            default: drawDot(canvas, scale); break;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(255);
        canvas.restore();
    }

    private RectF rect(float cx, float cy, float r) {
        return new RectF(cx - r, cy - r, cx + r, cy + r);
    }

    private void drawWifi(Canvas c, float s) {
        float cx = 12 * s, cy = 19.2f * s;
        paint.setStrokeWidth(1.75f * s);
        c.drawArc(rect(cx, cy, 3.4f * s), 225, 90, false, paint);
        c.drawArc(rect(cx, cy, 7.2f * s), 225, 90, false, paint);
        c.drawArc(rect(cx, cy, 11f * s), 225, 90, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - 0.1f * s, 1.45f * s, paint);
    }

    private void drawSignal(Canvas c, float s) {
        paint.setStyle(Paint.Style.FILL);
        float[] h = {4.5f, 7.5f, 10.5f, 13.5f};
        for (int i = 0; i < 4; i++) {
            float bw = 3f * s;
            float x = (3.2f + i * 4.6f) * s;
            float top = (18f - h[i]) * s;
            c.drawRoundRect(new RectF(x, top, x + bw, 18.5f * s), 0.9f * s, 0.9f * s, paint);
        }
    }

    private void drawOverview(Canvas c, float s) {
        paint.setStrokeWidth(1.6f * s);
        float r = 3f * s;
        c.drawRoundRect(new RectF(3.5f * s, 3.5f * s, 10.5f * s, 10.5f * s), r, r, paint);
        c.drawRoundRect(new RectF(13.5f * s, 3.5f * s, 20.5f * s, 10.5f * s), r, r, paint);
        c.drawRoundRect(new RectF(3.5f * s, 13.5f * s, 10.5f * s, 20.5f * s), r, r, paint);
        c.drawRoundRect(new RectF(13.5f * s, 13.5f * s, 20.5f * s, 20.5f * s), r, r, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(7 * s, 7 * s, 1.1f * s, paint);
    }

    private void drawSpeed(Canvas c, float s) {
        paint.setStrokeWidth(1.8f * s);
        float cx = 12 * s, cy = 14.2f * s;
        float r = 8.7f * s;
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 165, 210, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 1.5f * s, paint);
        Path needle = new Path();
        needle.moveTo(cx, cy);
        needle.lineTo(17.4f * s, 8.2f * s);
        needle.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * s);
        c.drawPath(needle, paint);
        c.drawLine(4.8f * s, 19.4f * s, 7.5f * s, 19.4f * s, paint);
        c.drawLine(16.5f * s, 19.4f * s, 19.2f * s, 19.4f * s, paint);
    }

    private void drawTools(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        c.drawLine(3.5f * s, 7f * s, 20.5f * s, 7f * s, paint);
        c.drawLine(3.5f * s, 17f * s, 20.5f * s, 17f * s, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(9f * s, 7f * s, 2.3f * s, paint);
        c.drawCircle(15.5f * s, 17f * s, 2.3f * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF07111B);
        paint.setStrokeWidth(1.2f * s);
        c.drawCircle(9f * s, 7f * s, 0.85f * s, paint);
        c.drawCircle(15.5f * s, 17f * s, 0.85f * s, paint);
        paint.setColor(color);
    }

    private void drawRefresh(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        RectF r = new RectF(4.2f * s, 4.2f * s, 19.8f * s, 19.8f * s);
        c.drawArc(r, 48, 270, false, paint);
        Path p = new Path();
        p.moveTo(18.9f * s, 4.2f * s);
        p.lineTo(19.9f * s, 9.4f * s);
        p.lineTo(14.8f * s, 8.2f * s);
        c.drawPath(p, paint);
    }

    private void drawScan(Canvas c, float s) {
        paint.setStrokeWidth(1.6f * s);
        float cx = 12 * s, cy = 12 * s;
        c.drawCircle(cx, cy, 8.9f * s, paint);
        c.drawCircle(cx, cy, 5.6f * s, paint);
        c.drawCircle(cx, cy, 2.3f * s, paint);
        c.drawLine(cx, cy, 18.2f * s, 6.2f * s, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 1.2f * s, paint);
    }

    private void drawChevron(Canvas c, float s) {
        paint.setStrokeWidth(1.9f * s);
        Path p = new Path();
        p.moveTo(9f * s, 5.5f * s);
        p.lineTo(15.5f * s, 12f * s);
        p.lineTo(9f * s, 18.5f * s);
        c.drawPath(p, paint);
    }

    private void drawLock(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        c.drawArc(new RectF(7.2f * s, 3.5f * s, 16.8f * s, 13.4f * s), 180, 180, false, paint);
        c.drawLine(7.2f * s, 8.2f * s, 7.2f * s, 10.6f * s, paint);
        c.drawLine(16.8f * s, 8.2f * s, 16.8f * s, 10.6f * s, paint);
        c.drawRoundRect(new RectF(4.8f * s, 9.5f * s, 19.2f * s, 20.2f * s), 2.2f * s, 2.2f * s, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(12 * s, 14.6f * s, 1.2f * s, paint);
    }

    private void drawGlobe(Canvas c, float s) {
        paint.setStrokeWidth(1.65f * s);
        c.drawCircle(12 * s, 12 * s, 8.8f * s, paint);
        c.drawOval(new RectF(7.6f * s, 3.2f * s, 16.4f * s, 20.8f * s), paint);
        c.drawLine(3.4f * s, 12 * s, 20.6f * s, 12 * s, paint);
        c.drawArc(new RectF(4.3f * s, 6.8f * s, 19.7f * s, 17.2f * s), 20, 140, false, paint);
    }

    private void drawShare(Canvas c, float s) {
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(18.2f * s, 5.7f * s, 2.5f * s, paint);
        c.drawCircle(5.8f * s, 12f * s, 2.5f * s, paint);
        c.drawCircle(18.2f * s, 18.3f * s, 2.5f * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * s);
        c.drawLine(8.1f * s, 10.9f * s, 15.9f * s, 6.8f * s, paint);
        c.drawLine(8.1f * s, 13.1f * s, 15.9f * s, 17.2f * s, paint);
    }

    private void drawSettings(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        c.drawCircle(12 * s, 12 * s, 3.1f * s, paint);
        c.drawCircle(12 * s, 12 * s, 7.2f * s, paint);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            float x1 = (float) (12 + Math.cos(angle) * 8.5) * s;
            float y1 = (float) (12 + Math.sin(angle) * 8.5) * s;
            float x2 = (float) (12 + Math.cos(angle) * 10.4) * s;
            float y2 = (float) (12 + Math.sin(angle) * 10.4) * s;
            c.drawLine(x1, y1, x2, y2, paint);
        }
    }

    private void drawArrow(Canvas c, float s, boolean up) {
        paint.setStrokeWidth(1.8f * s);
        float sign = up ? -1 : 1;
        c.drawLine(12 * s, (up ? 4.5f : 19.5f) * s, 12 * s, (up ? 19.5f : 4.5f) * s, paint);
        Path p = new Path();
        if (up) {
            p.moveTo(6.8f * s, 9.8f * s);
            p.lineTo(12 * s, 4.4f * s);
            p.lineTo(17.2f * s, 9.8f * s);
        } else {
            p.moveTo(6.8f * s, 14.2f * s);
            p.lineTo(12 * s, 19.6f * s);
            p.lineTo(17.2f * s, 14.2f * s);
        }
        c.drawPath(p, paint);
    }

    private void drawCheck(Canvas c, float s) {
        paint.setStrokeWidth(2.1f * s);
        Path p = new Path();
        p.moveTo(4.8f * s, 12.4f * s);
        p.lineTo(9.7f * s, 17.4f * s);
        p.lineTo(19.4f * s, 6.6f * s);
        c.drawPath(p, paint);
    }

    private void drawWarning(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        Path p = new Path();
        p.moveTo(12 * s, 3.5f * s);
        p.lineTo(21.2f * s, 20f * s);
        p.lineTo(2.8f * s, 20f * s);
        p.close();
        c.drawPath(p, paint);
        c.drawLine(12 * s, 8.8f * s, 12 * s, 13.8f * s, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(12 * s, 16.8f * s, 1f * s, paint);
    }

    private void drawSim(Canvas c, float s) {
        paint.setStrokeWidth(1.6f * s);
        Path p = new Path();
        p.moveTo(4.3f * s, 3.5f * s);
        p.lineTo(14.5f * s, 3.5f * s);
        p.lineTo(20f * s, 9f * s);
        p.lineTo(20f * s, 20.5f * s);
        p.lineTo(4.3f * s, 20.5f * s);
        p.close();
        c.drawPath(p, paint);
        c.drawRoundRect(new RectF(8f * s, 10.4f * s, 16.2f * s, 17.6f * s), 1.4f * s, 1.4f * s, paint);
        c.drawLine(10.7f * s, 12.7f * s, 10.7f * s, 15.3f * s, paint);
        c.drawLine(13.5f * s, 12.7f * s, 13.5f * s, 15.3f * s, paint);
    }

    private void drawSearch(Canvas c, float s) {
        paint.setStrokeWidth(1.8f * s);
        c.drawCircle(10.5f * s, 10.5f * s, 6.2f * s, paint);
        c.drawLine(15.1f * s, 15.2f * s, 20.5f * s, 20.5f * s, paint);
    }

    private void drawClose(Canvas c, float s) {
        paint.setStrokeWidth(1.9f * s);
        c.drawLine(6f * s, 6f * s, 18f * s, 18f * s, paint);
        c.drawLine(18f * s, 6f * s, 6f * s, 18f * s, paint);
    }

    private void drawExternal(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        Path p = new Path();
        p.moveTo(13f * s, 4.5f * s);
        p.lineTo(19.5f * s, 4.5f * s);
        p.lineTo(19.5f * s, 11f * s);
        c.drawPath(p, paint);
        c.drawLine(19f * s, 5f * s, 11.2f * s, 12.8f * s, paint);
        c.drawRoundRect(new RectF(4.5f * s, 7.5f * s, 16.5f * s, 19.5f * s), 2f * s, 2f * s, paint);
    }

    private void drawRoute(Canvas c, float s) {
        paint.setStrokeWidth(1.7f * s);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(5.5f * s, 6f * s, 2.1f * s, paint);
        c.drawCircle(18.5f * s, 18f * s, 2.1f * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        Path p = new Path();
        p.moveTo(7.2f * s, 7.6f * s);
        p.cubicTo(15f * s, 6f * s, 6f * s, 15f * s, 16.7f * s, 16.7f * s);
        c.drawPath(p, paint);
    }

    private void drawLayers(Canvas c, float s) {
        paint.setStrokeWidth(1.6f * s);
        Path a = new Path();
        a.moveTo(12 * s, 3.6f * s);
        a.lineTo(21f * s, 8.2f * s);
        a.lineTo(12 * s, 12.8f * s);
        a.lineTo(3f * s, 8.2f * s);
        a.close();
        c.drawPath(a, paint);
        c.drawLine(3f * s, 13f * s, 12 * s, 17.6f * s, paint);
        c.drawLine(21f * s, 13f * s, 12 * s, 17.6f * s, paint);
        c.drawLine(3f * s, 17.5f * s, 12 * s, 22.1f * s, paint);
        c.drawLine(21f * s, 17.5f * s, 12 * s, 22.1f * s, paint);
    }

    private void drawDot(Canvas c, float s) {
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(12 * s, 12 * s, 2.3f * s, paint);
    }
}
