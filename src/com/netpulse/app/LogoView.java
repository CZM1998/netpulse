package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Compact in-app version of the NetPulse signal-pulse brand mark. */
public class LogoView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pulse = new Path();
    private LinearGradient gradient;

    public LogoView(Context c) { super(c); init(); }
    public LogoView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(getContext(), 0.8f));
        ring.setColor(0x42FFFFFF);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(Ui.dp(getContext(), 40), widthMeasureSpec);
        int h = resolveSize(Ui.dp(getContext(), 40), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size * 0.44f;
        if (gradient == null) {
            gradient = new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                    new int[]{0xFF4CF7CB, 0xFF29D8E9, 0xFF4C8DFF},
                    new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(gradient);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        canvas.drawCircle(cx, cy, radius * 0.86f, ring);

        float s = size / 24f;
        pulse.reset();
        pulse.moveTo(cx - 5.2f * s, cy + 1.7f * s);
        pulse.lineTo(cx - 2.1f * s, cy + 1.7f * s);
        pulse.lineTo(cx - 0.5f * s, cy - 2.9f * s);
        pulse.lineTo(cx + 2.1f * s, cy + 3.7f * s);
        pulse.lineTo(cx + 3.7f * s, cy + 0.1f * s);
        pulse.lineTo(cx + 6.1f * s, cy + 0.1f * s);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.55f * s);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFF07141F);
        canvas.drawPath(pulse, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - 5.2f * s, cy + 1.7f * s, 0.72f * s, paint);
        canvas.drawCircle(cx + 6.1f * s, cy + 0.1f * s, 0.72f * s, paint);
    }
}
