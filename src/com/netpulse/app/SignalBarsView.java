package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SignalBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int bars = 4;
    private int level = 0;
    private int activeColor = Ui.ACCENT;
    private int inactiveColor = 0x2EFFFFFF;

    public SignalBarsView(Context c) { super(c); }
    public SignalBarsView(Context c, AttributeSet a) { super(c, a); }

    public void set(int level, int activeColor) {
        this.level = Math.max(0, Math.min(bars, level));
        this.activeColor = activeColor;
        invalidate();
    }

    public void setBars(int n) {
        bars = Math.max(2, Math.min(6, n));
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize(Ui.dp(getContext(), 34), wSpec);
        int h = resolveSize(Ui.dp(getContext(), 22), hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float gap = Math.max(Ui.dp(getContext(), 1.6f), w * 0.055f);
        float barW = (w - gap * (bars - 1)) / bars;
        float radius = barW / 2f;
        for (int i = 0; i < bars; i++) {
            float frac = 0.36f + (0.64f * i / (bars - 1f));
            float bh = h * frac;
            float left = i * (barW + gap);
            paint.setColor(i < level ? activeColor : inactiveColor);
            canvas.drawRoundRect(new RectF(left, h - bh, left + barW, h), radius, radius, paint);
        }
    }
}
