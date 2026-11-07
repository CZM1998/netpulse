package com.netpulse.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** WiFi channel occupancy spectrum. 2.4G uses curves; 5G draws bars. */
public class ChannelSpectrumView extends View {
    public static class Ap {
        public final String ssid;
        public final int rssi;
        public final int channel;
        public final int widthMhz;
        public final String bssid;
        public final int index;
        public final int color;

        public Ap(String ssid, int rssi, int channel, int widthMhz, String bssid,
                  int index, int color) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.channel = channel;
            this.widthMhz = widthMhz;
            this.bssid = bssid;
            this.index = index;
            this.color = color;
        }
    }

    private static final int[] PALETTE = {
            0xFF35F2C0, 0xFF5BA8FF, 0xFFB79CFF, 0xFFFFC266,
            0xFFFF7A9C, 0xFF7EE787, 0xFF61E3F2, 0xFFFF9F66,
            0xFFC8F26B, 0xFFF28CFF, 0xFFFFD166, 0xFF9BB8FF
    };

    private List<Ap> aps = new ArrayList<>();
    private boolean fiveGhz;
    private String highlightBssid;
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChannelSpectrumView(Context c) { super(c); init(); }
    public ChannelSpectrumView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Ui.dp(getContext(), 1));
        text.setColor(Ui.DIM);
        text.setTextSize(Ui.sp(getContext(), 8));
        text.setTypeface(Ui.mono(getContext()));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(getContext(), 1.2f));
        fill.setStyle(Paint.Style.FILL);
        badgeFill.setStyle(Paint.Style.FILL);
        badgeStroke.setStyle(Paint.Style.STROKE);
        badgeStroke.setStrokeWidth(Ui.dp(getContext(), 1));
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setTypeface(Ui.mono(getContext()));
        badgeText.setTextSize(Ui.sp(getContext(), 7.5f));
    }

    public static int colorForIndex(int index) {
        if (index >= 0 && index < PALETTE.length) return PALETTE[index];
        float hue = (index * 137.508f + 17f) % 360f;
        int c = Color.HSVToColor(new float[]{hue, 0.66f, 0.96f});
        return 0xFF000000 | c;
    }

    public void setAccessPoints(List<Ap> points, boolean fiveGhz) {
        aps = points == null ? new ArrayList<>() : new ArrayList<>(points);
        this.fiveGhz = fiveGhz;
        invalidate();
    }

    public void setHighlightBssid(String bssid) {
        highlightBssid = bssid;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize(Ui.dp(getContext(), 320), wSpec);
        int h = resolveSize(Ui.dp(getContext(), 215), hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = Ui.dp(getContext(), 25);
        float right = Ui.dp(getContext(), 4);
        float top = Ui.dp(getContext(), 15);
        float bottom = Ui.dp(getContext(), 26);
        float w = getWidth() - left - right;
        float h = getHeight() - top - bottom;
        if (w < 20 || h < 20) return;

        int[] levels = {-30, -50, -70, -90};
        grid.setColor(0x16FFFFFF);
        for (int dbm : levels) {
            float y = top + dbmToY(dbm, top, h);
            canvas.drawLine(left, y, left + w, y, grid);
            text.setColor(Ui.DIM);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(Ui.sp(getContext(), 8));
            text.setTypeface(Ui.mono(getContext()));
            canvas.drawText(String.valueOf(dbm), 0, y + Ui.dp(getContext(), 3), text);
        }

        List<Ap> visible = new ArrayList<>();
        for (Ap ap : aps) {
            if (fiveGhz ? ap.channel > 14 : ap.channel > 0 && ap.channel <= 14) visible.add(ap);
        }

        if (visible.isEmpty()) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Ui.sp(getContext(), 10));
            text.setTypeface(Ui.regular(getContext()));
            text.setColor(Ui.DIM);
            canvas.drawText(fiveGhz ? "附近没有扫描到 5 GHz 网络" : "附近没有扫描到 2.4 GHz 网络",
                    left + w / 2, top + h / 2, text);
            text.setTextSize(Ui.sp(getContext(), 8));
            text.setTypeface(Ui.mono(getContext()));
            return;
        }

        if (fiveGhz) drawFive(canvas, left, top, w, h, visible);
        else drawTwoFour(canvas, left, top, w, h, visible);

        grid.setColor(0x25FFFFFF);
        canvas.drawLine(left, top + h, left + w, top + h, grid);
    }

    private void drawTwoFour(Canvas canvas, float left, float top, float w, float h, List<Ap> visible) {
        float unit = w / 13f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x0DFFFFFF);
        canvas.drawRoundRect(new RectF(left, top, left + w, top + h), Ui.dp(getContext(), 5),
                Ui.dp(getContext(), 5), p);

        for (Ap ap : visible) {
            int color = ap.color;
            float center = left + (ap.channel - 0.5f) * unit;
            float sigma = Math.max(0.8f, ap.widthMhz / 5f) * unit * 0.9f;
            float peakY = top + dbmToY(ap.rssi, top, h);
            Path curve = new Path();
            Path area = new Path();
            boolean started = false;
            for (int i = 0; i <= 60; i++) {
                float ch = 1f + 12f * i / 60f;
                float x = left + (ch - 0.5f) * unit;
                float d = (ch - ap.channel) / Math.max(0.5f, ap.widthMhz / 5f);
                float envelope = (float) Math.exp(-0.5f * d * d);
                float y = top + h - (top + h - peakY) * envelope;
                if (!started) {
                    curve.moveTo(x, y);
                    area.moveTo(x, top + h);
                    area.lineTo(x, y);
                    started = true;
                } else {
                    curve.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(left + 12.5f * unit, top + h);
            area.close();
            fill.setShader(new LinearGradient(0, peakY, 0, top + h,
                    Ui.withAlpha(color, 0.20f), Ui.withAlpha(color, 0.01f), Shader.TileMode.CLAMP));
            canvas.drawPath(area, fill);
            fill.setShader(null);
            boolean highlight = ap.bssid != null && ap.bssid.equals(highlightBssid);
            stroke.setColor(Ui.withAlpha(color, highlight ? 1f : 0.80f));
            stroke.setStrokeWidth(Ui.dp(getContext(), highlight ? 2.4f : 1.25f));
            canvas.drawPath(curve, stroke);
            if (highlight) {
                fill.setColor(color);
                canvas.drawCircle(center, peakY, Ui.dp(getContext(), 2.6f), fill);
            }
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Ui.DIM);
        text.setTextSize(Ui.sp(getContext(), 8));
        text.setTypeface(Ui.mono(getContext()));
        for (int ch = 1; ch <= 13; ch++) {
            if (ch % 2 == 1 || ch == 6) {
                float x = left + (ch - 0.5f) * unit;
                canvas.drawText(String.valueOf(ch), x, getHeight() - Ui.dp(getContext(), 6), text);
            }
        }

        drawBadges(canvas, visible, left, top, w, h, false, unit);
    }

    private void drawFive(Canvas canvas, float left, float top, float w, float h, List<Ap> visible) {
        float minFreq = 5150;
        float maxFreq = 5925;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x0DFFFFFF);
        canvas.drawRoundRect(new RectF(left, top, left + w, top + h), Ui.dp(getContext(), 5),
                Ui.dp(getContext(), 5), p);

        Map<Integer, Integer> counts = new HashMap<>();
        for (Ap ap : visible) counts.merge(ap.channel, 1, Integer::sum);
        Map<Integer, Integer> ordinals = new HashMap<>();

        for (Ap ap : visible) {
            float freq = freqForChannel(ap.channel, 5);
            if (freq <= 0) continue;
            int total = Math.max(1, counts.getOrDefault(ap.channel, 1));
            int ordinal = ordinals.getOrDefault(ap.channel, 0);
            ordinals.put(ap.channel, ordinal + 1);
            float baseX = left + (freq - minFreq) / (maxFreq - minFreq) * w;
            float peakY = top + dbmToY(ap.rssi, top, h);
            int color = ap.color;
            boolean highlight = ap.bssid != null && ap.bssid.equals(highlightBssid);
            float barW = Math.max(Ui.dp(getContext(), 2.4f), w * (ap.widthMhz / (maxFreq - minFreq)));
            barW = Math.min(barW, Ui.dp(getContext(), 8));
            float gap = Ui.dp(getContext(), 1.4f);
            float group = total * barW + (total - 1) * gap;
            float x = baseX - group / 2f + ordinal * (barW + gap) + barW / 2f;
            fill.setShader(new LinearGradient(0, peakY, 0, top + h,
                    Ui.withAlpha(color, highlight ? 0.95f : 0.72f),
                    Ui.withAlpha(color, 0.12f), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(x - barW / 2, peakY, x + barW / 2, top + h),
                    Ui.dp(getContext(), 2), Ui.dp(getContext(), 2), fill);
            fill.setShader(null);
            if (highlight) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(Ui.dp(getContext(), 1.3f));
                canvas.drawRoundRect(new RectF(x - barW / 2, peakY, x + barW / 2, top + h),
                        Ui.dp(getContext(), 2), Ui.dp(getContext(), 2), stroke);
            }
        }

        int[][] marks = {{5180, 36}, {5240, 48}, {5500, 100}, {5745, 149}, {5825, 165}};
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Ui.DIM);
        text.setTextSize(Ui.sp(getContext(), 8));
        text.setTypeface(Ui.mono(getContext()));
        for (int[] m : marks) {
            float x = left + (m[0] - minFreq) / (maxFreq - minFreq) * w;
            canvas.drawLine(x, top + h, x, top + h + Ui.dp(getContext(), 3), grid);
            canvas.drawText(String.valueOf(m[1]), x, getHeight() - Ui.dp(getContext(), 13), text);
        }

        drawBadges(canvas, visible, left, top, w, h, true, 0);
    }

    /** Draws numbered tags at each peak/bar. Numbers match the legend in the WiFi screen. */
    private void drawBadges(Canvas canvas, List<Ap> visible, float left, float top, float w, float h,
                            boolean five, float unit) {
        List<RectF> occupied = new ArrayList<>();
        badgeFill.setColor(0xE607111B);
        badgeText.setStyle(Paint.Style.FILL);
        for (Ap ap : visible) {
            int color = ap.color;
            float cx;
            float peakY;
            if (five) {
                float freq = freqForChannel(ap.channel, 5);
                if (freq <= 0) continue;
                cx = left + (freq - 5150) / (5825 - 5150) * w;
                peakY = top + dbmToY(ap.rssi, top, h);
                // Small horizontal spread for same-channel labels.
                Map<Integer, Integer> counts = new HashMap<>();
                for (Ap a : visible) counts.merge(a.channel, 1, Integer::sum);
                // Offset is already applied to bars; labels follow the bar's approximate x.
                int total = Math.max(1, counts.getOrDefault(ap.channel, 1));
                int ordinal = 0;
                for (Ap a : visible) {
                    if (a.channel == ap.channel) {
                        if (a == ap) break;
                        ordinal++;
                    }
                }
                float group = total * Ui.dp(getContext(), 6f);
                cx += (ordinal - (total - 1) / 2f) * (group / Math.max(1, total));
            } else {
                cx = left + (ap.channel - 0.5f) * unit;
                peakY = top + dbmToY(ap.rssi, top, h);
            }
            String label = String.valueOf(ap.index + 1);
            float tw = badgeText.measureText(label);
            float bw = Math.max(Ui.dp(getContext(), 15), tw + Ui.dp(getContext(), 8));
            float bh = Ui.dp(getContext(), 13);
            float bx = cx - bw / 2;
            float by = peakY - bh - Ui.dp(getContext(), 3);
            if (by < top + 1) by = peakY + Ui.dp(getContext(), 3);
            RectF box = new RectF(bx, by, bx + bw, by + bh);
            int guard = 0;
            while (collides(box, occupied) && guard < 7) {
                box.offset(0, -Ui.dp(getContext(), 14));
                if (box.top < top + 1) {
                    box.offset(0, Ui.dp(getContext(), 28));
                    break;
                }
                guard++;
            }
            occupied.add(new RectF(box));
            boolean current = ap.bssid != null && ap.bssid.equals(highlightBssid);
            badgeFill.setColor(current ? color : 0xE607111B);
            canvas.drawRoundRect(box, Ui.dp(getContext(), 4), Ui.dp(getContext(), 4), badgeFill);
            badgeStroke.setColor(current ? 0xFFFFFFFF : Ui.withAlpha(color, 0.9f));
            canvas.drawRoundRect(box, Ui.dp(getContext(), 4), Ui.dp(getContext(), 4), badgeStroke);
            badgeText.setColor(current ? 0xFF061611 : color);
            float baseline = box.centerY() + Ui.dp(getContext(), 2.7f);
            canvas.drawText(label, box.centerX(), baseline, badgeText);
        }
    }

    private boolean collides(RectF box, List<RectF> boxes) {
        for (RectF b : boxes) {
            if (RectF.intersects(box, b)) return true;
        }
        return false;
    }

    private float dbmToY(int dbm, float top, float h) {
        float clamped = Math.max(-95f, Math.min(-30f, dbm));
        return (-30f - clamped) / 65f * h;
    }

    public static int channelForFrequency(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        if (freq >= 5955 && freq <= 7115) return (freq - 5950) / 5;
        if (freq >= 5000 && freq < 5925) {
            int[] fiveChannels = {36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116,
                    120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165};
            int best = 0;
            int bestDiff = Integer.MAX_VALUE;
            for (int ch : fiveChannels) {
                int f = freqForChannel(ch, 5);
                int diff = Math.abs(f - freq);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = ch;
                }
            }
            return best;
        }
        return 0;
    }

    public static int freqForChannel(int channel, int band) {
        if (band == 5) {
            if (channel <= 0) return 0;
            if (channel >= 36 && channel <= 64) return 5000 + channel * 5;
            if (channel >= 100 && channel <= 144) return 5000 + channel * 5;
            if (channel >= 149 && channel <= 165) return 5000 + channel * 5;
            if (channel >= 1 && channel <= 233) return 5950 + channel * 5;
            return 0;
        }
        if (channel == 14) return 2484;
        if (channel >= 1 && channel <= 13) return 2407 + channel * 5;
        return 0;
    }
}
