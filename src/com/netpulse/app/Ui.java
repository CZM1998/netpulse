package com.netpulse.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Small, dependency-free design system for the app. */
public final class Ui {
    public static final int BG = 0xFF07111B;
    public static final int BG_2 = 0xFF0A1622;
    public static final int CARD_TOP = 0xFF111F2C;
    public static final int CARD_BOTTOM = 0xFF0C1723;
    public static final int CARD_SOFT = 0xFF0C1824;
    public static final int BORDER = 0xFF1B3344;
    public static final int TEXT = 0xFFF3F8FA;
    public static final int MUTED = 0xFF8CA5B4;
    public static final int DIM = 0xFF607787;
    public static final int ACCENT = 0xFF38F2C0;
    public static final int BLUE = 0xFF5BA8FF;
    public static final int AMBER = 0xFFFFC266;
    public static final int RED = 0xFFFF6F7E;
    public static final int PURPLE = 0xFFB79CFF;
    public static final int NAV = 0xFF091520;

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface mono;

    private Ui() {}

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static Typeface regular(Context c) {
        if (regular == null) regular = Typeface.create("sans-serif", Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium(Context c) {
        if (medium == null) medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return medium;
    }

    public static Typeface mono(Context c) {
        if (mono == null) mono = Typeface.create("monospace", Typeface.NORMAL);
        return mono;
    }

    public static TextView text(Context c, CharSequence value, float sizeSp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        v.setTextColor(color);
        v.setTypeface(bold ? medium(c) : regular(c));
        v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(c, 3), 1f);
        return v;
    }

    public static TextView mono(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = text(c, value, sizeSp, color, false);
        v.setTypeface(mono(c));
        return v;
    }

    public static TextView label(Context c, String value) {
        TextView v = text(c, value.toUpperCase(), 9.5f, DIM, true);
        v.setLetterSpacing(0.15f);
        return v;
    }

    public static TextView title(Context c, String value) {
        TextView v = text(c, value, 17f, TEXT, true);
        return v;
    }

    public static TextView section(Context c, String value) {
        TextView v = text(c, value, 20f, TEXT, true);
        return v;
    }

    public static TextView small(Context c, String value) {
        TextView v = text(c, value, 11.5f, MUTED, false);
        return v;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static Space gap(Context c, float dp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return s;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 12);
        v.setLayoutParams(lp);
        return v;
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable cardBackground(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM});
        d.setCornerRadius(dp(c, 20));
        d.setStroke(dp(c, 1), BORDER);
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, 17), dp(c, 16), dp(c, 17), dp(c, 16));
        l.setBackground(cardBackground(c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout flatCard(Context c, int color) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        GradientDrawable d = rounded(c, color, 14);
        d.setStroke(dp(c, 1), BORDER);
        l.setBackground(d);
        return l;
    }

    public static TextView chip(Context c, String value, int color) {
        TextView t = text(c, value, 10.5f, color, true);
        GradientDrawable bg = rounded(c, withAlpha(color, 0.12f), 99);
        bg.setStroke(dp(c, 1), withAlpha(color, 0.28f));
        t.setBackground(bg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 9), dp(c, 5), dp(c, 9), dp(c, 5));
        return t;
    }

    public static TextView primaryButton(Context c, String value) {
        TextView b = text(c, value, 14f, 0xFF061611, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        GradientDrawable bg = rounded(c, ACCENT, 13);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), bg, null));
        b.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        return b;
    }

    public static TextView outlineButton(Context c, String value) {
        TextView b = text(c, value, 13.5f, TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        GradientDrawable bg = rounded(c, 0x0FFFFFFF, 13);
        bg.setStroke(dp(c, 1), 0xFF2A4557);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), bg, null));
        b.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return b;
    }

    public static TextView quietButton(Context c, String value, int color) {
        TextView b = text(c, value, 12f, color, true);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(0, dp(c, 4), 0, dp(c, 4));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static FrameLayout iconButton(Context c, String icon, int iconColor) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = rounded(c, 0x12FFFFFF, 14);
        bg.setStroke(dp(c, 1), 0x18FFFFFF);
        f.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), bg, null));
        f.setClickable(true);
        f.setFocusable(true);
        IconView iv = new IconView(c);
        iv.setIcon(icon);
        iv.setColor(iconColor);
        int s = dp(c, 21);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        f.addView(iv, ilp);
        return f;
    }

    public static LinearLayout.LayoutParams lpWidth(int widthDp, Context c, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                widthDp == ViewGroup.LayoutParams.MATCH_PARENT ? ViewGroup.LayoutParams.MATCH_PARENT : dp(c, widthDp),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = weight;
        return lp;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.leftMargin = l;
            lp.topMargin = t;
            lp.rightMargin = r;
            lp.bottomMargin = b;
            v.setLayoutParams(lp);
        }
    }

    public static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255 * Math.min(1f, Math.max(0f, alpha))) << 24);
    }

    public static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int wifiQualityColor(int rssi) {
        if (rssi >= -55) return ACCENT;
        if (rssi >= -67) return BLUE;
        if (rssi >= -75) return AMBER;
        return RED;
    }

    public static String wifiQuality(int rssi) {
        if (rssi >= -50) return "极佳";
        if (rssi >= -60) return "优秀";
        if (rssi >= -67) return "良好";
        if (rssi >= -75) return "一般";
        if (rssi >= -85) return "较弱";
        return "很差";
    }

    public static int cellQualityColor(int dbm) {
        if (dbm >= -85) return ACCENT;
        if (dbm >= -95) return BLUE;
        if (dbm >= -105) return AMBER;
        return RED;
    }

    public static String cellQuality(int dbm) {
        if (dbm >= -80) return "极佳";
        if (dbm >= -90) return "良好";
        if (dbm >= -100) return "一般";
        if (dbm >= -110) return "较弱";
        return "很差";
    }

    public static TextView statValue(Context c, String value) {
        return text(c, value, 18f, TEXT, true);
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static LinearLayout detailRow(Context c, String key, String value) {
        LinearLayout r = row(c);
        TextView k = text(c, key, 11.5f, MUTED, false);
        TextView v = text(c, value, 11.5f, TEXT, true);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        r.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        r.addView(v, vlp);
        r.setPadding(0, dp(c, 5), 0, dp(c, 5));
        return r;
    }

    /** Minimal bottom sheet-style dialog. Returns the content column for callers to extend. */
    public static Dialog sheet(Activity activity, String title, CharSequence message,
                               String primaryText, Runnable primaryAction) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(activity);
        wrap.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout card = card(activity);
        card.setBackground(cardBackground(activity));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(clp);
        if (title != null) {
            LinearLayout head = row(activity);
            View bar = new View(activity);
            bar.setBackground(rounded(activity, ACCENT, 99));
            head.addView(bar, new LinearLayout.LayoutParams(dp(activity, 3), dp(activity, 18)));
            TextView t = text(activity, title, 17f, TEXT, true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(activity, 9);
            head.addView(t, tlp);
            card.addView(head);
        }
        if (message != null && message.length() > 0) {
            TextView m = text(activity, message, 13f, MUTED, false);
            LinearLayout.LayoutParams mlp = matchWrap(activity);
            mlp.topMargin = dp(activity, 12);
            card.addView(m, mlp);
        }
        LinearLayout body = column(activity);
        LinearLayout.LayoutParams blp = matchWrap(activity);
        blp.topMargin = dp(activity, 10);
        card.addView(body, blp);
        if (primaryText != null) {
            LinearLayout buttons = row(activity);
            LinearLayout.LayoutParams bp = matchWrap(activity);
            bp.topMargin = dp(activity, 16);
            TextView cancel = outlineButton(activity, "关闭");
            cancel.setOnClickListener(v -> dialog.dismiss());
            buttons.addView(cancel, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView ok = primaryButton(activity, primaryText);
            ok.setOnClickListener(v -> {
                if (primaryAction != null) primaryAction.run();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
            okp.leftMargin = dp(activity, 10);
            buttons.addView(ok, okp);
            card.addView(buttons, bp);
        } else {
            TextView close = outlineButton(activity, "关闭");
            close.setOnClickListener(v -> dialog.dismiss());
            LinearLayout.LayoutParams cp = matchWrap(activity);
            cp.topMargin = dp(activity, 16);
            card.addView(close, cp);
        }
        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(false);
        scroller.addView(card);
        wrap.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrap);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.gravity = Gravity.BOTTOM;
            attrs.dimAmount = 0.64f;
            w.setAttributes(attrs);
        }
        dialog.setOnShowListener(d -> {
            Window win = dialog.getWindow();
            if (win != null) {
                win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        return dialog;
    }

    public static void info(Activity a, String title, CharSequence msg) {
        sheet(a, title, msg, null, null).show();
    }

    public static void confirm(Activity a, String title, CharSequence msg, String positive, Runnable onOk) {
        sheet(a, title, msg, positive, onOk).show();
    }

    public static void choices(Activity a, String title, String[] options, int selected,
                               java.util.function.IntConsumer action) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        LinearLayout card = card(a);
        LinearLayout head = row(a);
        View bar = new View(a);
        bar.setBackground(rounded(a, ACCENT, 99));
        head.addView(bar, new LinearLayout.LayoutParams(dp(a, 3), dp(a, 18)));
        TextView t = text(a, title, 17f, TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(a, 9);
        head.addView(t, tlp);
        card.addView(head);
        List<TextView> optionsViews = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout r = row(a);
            r.setPadding(dp(a, 11), dp(a, 12), dp(a, 11), dp(a, 12));
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),
                    rounded(a, index == selected ? 0x1438F2C0 : 0x00000000, 11), null));
            TextView label = text(a, options[i], 13.5f, index == selected ? ACCENT : TEXT, index == selected);
            r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (index == selected) {
                IconView check = new IconView(a);
                check.setIcon("check");
                check.setColor(ACCENT);
                r.addView(check, new LinearLayout.LayoutParams(dp(a, 17), dp(a, 17)));
            }
            r.setOnClickListener(v -> {
                d.dismiss();
                action.accept(index);
            });
            card.addView(r);
            optionsViews.add(label);
        }
        TextView close = outlineButton(a, "取消");
        close.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams cp = matchWrap(a);
        cp.topMargin = dp(a, 14);
        card.addView(close, cp);
        wrap.addView(card);
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = w.getAttributes();
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.gravity = Gravity.BOTTOM;
            attrs.dimAmount = 0.64f;
            w.setAttributes(attrs);
        }
        d.show();
    }

    public static Drawable tinted(Drawable d, int color) {
        Drawable copy = d.mutate();
        copy.setTint(color);
        return copy;
    }
}
