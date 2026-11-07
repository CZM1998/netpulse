package com.netpulse.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OverviewScreen extends ScrollView implements MainActivity.Screen {
    private final MainActivity host;
    private final LinearLayout content;
    private final List<Float> wifiHistory = new ArrayList<>();
    private final List<Float> cellHistory = new ArrayList<>();

    private TextView heroChip, heroTitle, heroSub, heroSignal, heroQuality, heroLink, heroLinkLabel;
    private SignalBarsView heroBars;
    private TrendChartView heroSpark;
    private TextView rateDown, rateUp, rateMeta;
    private TextView ipPublic, ipLocal;
    private LinearLayout permissionCard;
    private TextView permissionText;
    private TextView permissionAction;
    private TextView healthScore, healthGrade, healthMeta, healthButton;
    private LinearLayout healthChecks;
    private TextView trafficSession, trafficBoot, trafficMobile, trafficApp;
    private TrendChartView signalChart;
    private TextView signalCurrent, signalMin, signalAvg, signalMax;
    private boolean autoCheckScheduled;
    private boolean checking;
    private long sessionRx = -1, sessionTx = -1;

    public OverviewScreen(MainActivity host) {
        super(host);
        this.host = host;
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setPadding(0, Ui.dp(host, 2), 0, Ui.dp(host, 18));
        content = Ui.column(host);
        content.setPadding(Ui.dp(host, 16), 0, Ui.dp(host, 16), Ui.dp(host, 26));
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private void build() {
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.section(host, "网络概览"));
        TextView sub = Ui.small(host, "实时链路、信号与质量，一眼看清");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(host, 4);
        titleCol.addView(sub, slp);
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView live = Ui.chip(host, "LIVE", Ui.ACCENT);
        head.addView(live);
        content.addView(head, topMargin(8));

        buildHero();
        buildPermissionCard();
        buildHealthCard();
        buildSignalCard();
        buildTrafficCard();
        buildQuickActions();
        TextView footer = Ui.small(host, "数据每秒刷新 · 部分蜂窝字段受 Android 与运营商限制");
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(host, 6);
        content.addView(footer, flp);
    }

    private void buildHero() {
        LinearLayout card = Ui.card(host);
        LinearLayout top = Ui.row(host);
        heroChip = Ui.chip(host, "连接中", Ui.ACCENT);
        top.addView(heroChip);
        View spacer = new View(host);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout live = Ui.row(host);
        View dot = new View(host);
        dot.setBackground(Ui.rounded(host, Ui.ACCENT, 99));
        live.addView(dot, new LinearLayout.LayoutParams(Ui.dp(host, 6), Ui.dp(host, 6)));
        TextView liveText = Ui.text(host, "实时监测", 10f, Ui.MUTED, false);
        LinearLayout.LayoutParams ltlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ltlp.leftMargin = Ui.dp(host, 6);
        live.addView(liveText, ltlp);
        top.addView(live);
        card.addView(top);

        heroTitle = Ui.text(host, "正在读取网络…", 27f, Ui.TEXT, true);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.topMargin = Ui.dp(host, 13);
        card.addView(heroTitle, htlp);

        heroSub = Ui.text(host, "正在获取连接信息", 11.5f, Ui.MUTED, false);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(heroSub, topMargin(5));

        LinearLayout metrics = Ui.row(host);
        metrics.setGravity(Gravity.BOTTOM);
        LinearLayout signalCol = Ui.row(host);
        signalCol.setGravity(Gravity.BOTTOM);
        heroBars = new SignalBarsView(host);
        heroBars.setBars(4);
        heroBars.set(0, Ui.DIM);
        signalCol.addView(heroBars, new LinearLayout.LayoutParams(Ui.dp(host, 33), Ui.dp(host, 22)));
        heroSignal = Ui.text(host, "—", 34f, Ui.TEXT, true);
        LinearLayout.LayoutParams hslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hslp.leftMargin = Ui.dp(host, 10);
        signalCol.addView(heroSignal, hslp);
        TextView dbm = Ui.text(host, "dBm", 11f, Ui.MUTED, false);
        LinearLayout.LayoutParams dblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dblp.leftMargin = Ui.dp(host, 5);
        dblp.bottomMargin = Ui.dp(host, 5);
        signalCol.addView(dbm, dblp);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sclp.bottomMargin = Ui.dp(host, 5);
        metrics.addView(signalCol, sclp);

        heroQuality = Ui.text(host, "—", 11f, Ui.MUTED, true);
        LinearLayout.LayoutParams hqlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hqlp.leftMargin = Ui.dp(host, 8);
        hqlp.bottomMargin = Ui.dp(host, 7);
        metrics.addView(heroQuality, hqlp);
        View stretch = new View(host);
        metrics.addView(stretch, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout linkCol = Ui.column(host);
        linkCol.setGravity(Gravity.END);
        heroLink = Ui.text(host, "—", 18f, Ui.TEXT, true);
        heroLink.setGravity(Gravity.END);
        linkCol.addView(heroLink);
        heroLinkLabel = Ui.text(host, "链路速率", 9f, Ui.DIM, false);
        heroLinkLabel.setGravity(Gravity.END);
        linkCol.addView(heroLinkLabel, topMargin(2));
        metrics.addView(linkCol);
        card.addView(metrics, topMargin(12));

        heroSpark = new TrendChartView(host);
        heroSpark.setShowLabels(false);
        heroSpark.setFixedRange(-90, -30);
        heroSpark.setColor(Ui.ACCENT);
        heroSpark.setEmptyText("正在采样信号…");
        LinearLayout.LayoutParams sparkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 48));
        sparkLp.topMargin = Ui.dp(host, 4);
        card.addView(heroSpark, sparkLp);

        card.addView(Ui.divider(host));
        LinearLayout rateRow = Ui.row(host);
        LinearLayout down = rateCell("下载", "↓", Ui.ACCENT, true);
        rateDown = (TextView) down.getChildAt(1);
        rateRow.addView(down, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout up = rateCell("上传", "↑", Ui.BLUE, true);
        rateUp = (TextView) up.getChildAt(1);
        rateRow.addView(up, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout meta = rateCell("频宽 / 网络", "◆", Ui.PURPLE, true);
        rateMeta = (TextView) meta.getChildAt(1);
        rateRow.addView(meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        card.addView(rateRow);

        LinearLayout ipRow = Ui.row(host);
        ipPublic = Ui.mono(host, "公网 —", 9.5f, Ui.DIM);
        ipPublic.setSingleLine(true);
        ipPublic.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        ipRow.addView(ipPublic, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ipLocal = Ui.mono(host, "本地 —", 9.5f, Ui.DIM);
        ipLocal.setGravity(Gravity.END);
        ipLocal.setSingleLine(true);
        ipLocal.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        ipRow.addView(ipLocal, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(ipRow, topMargin(11));
        content.addView(card);
    }

    private LinearLayout rateCell(String label, String arrow, int color, boolean top) {
        LinearLayout l = Ui.column(host);
        TextView labelView = Ui.text(host, arrow + "  " + label, 9.5f, Ui.DIM, true);
        l.addView(labelView);
        TextView value = Ui.text(host, "—", 15f, Ui.TEXT, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Ui.dp(host, 4);
        l.addView(value, vlp);
        return l;
    }

    private void buildPermissionCard() {
        permissionCard = Ui.flatCard(host, 0x173B2B1A);
        GradientDrawable bg = Ui.rounded(host, 0x173B2B1A, 14);
        bg.setStroke(Ui.dp(host, 1), 0x55FFC266);
        permissionCard.setBackground(bg);
        LinearLayout row = Ui.row(host);
        GradientDrawable iconBg = Ui.rounded(host, 0x22FFC266, 10);
        LinearLayout iconWrap = Ui.row(host);
        iconWrap.setGravity(Gravity.CENTER);
        iconWrap.setBackground(iconBg);
        IconView warning = new IconView(host);
        warning.setIcon("warning");
        warning.setColor(Ui.AMBER);
        iconWrap.addView(warning, new LinearLayout.LayoutParams(Ui.dp(host, 18), Ui.dp(host, 18)));
        row.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(host, 34), Ui.dp(host, 34)));
        LinearLayout textCol = Ui.column(host);
        TextView title = Ui.text(host, "解锁完整信号数据", 13f, Ui.TEXT, true);
        permissionText = Ui.small(host, "需要定位与电话权限");
        permissionText.setMaxLines(3);
        textCol.addView(title);
        textCol.addView(permissionText, topMargin(3));
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tclp.leftMargin = Ui.dp(host, 11);
        row.addView(textCol, tclp);
        permissionAction = Ui.quietButton(host, "去授权", Ui.AMBER);
        row.addView(permissionAction);
        permissionCard.addView(row);
        permissionCard.setVisibility(View.GONE);
        content.addView(permissionCard, topMargin(0));
        permissionAction.setOnClickListener(v -> {
            boolean needLocationSettings = NetProbe.hasFineLocation(host) && NetProbe.hasPhonePermission(host)
                    && !host.probe().isLocationEnabled();
            if (needLocationSettings) host.openLocationSettings();
            else host.requestCorePermissions();
        });
    }

    private void buildHealthCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "一键网络体检"));
        TextView sub = Ui.small(host, "外网 · DNS · 网关 · 信号 · 抖动");
        titleCol.addView(sub, topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout scoreCol = Ui.column(host);
        scoreCol.setGravity(Gravity.END);
        LinearLayout scoreRow = Ui.row(host);
        scoreRow.setGravity(Gravity.BOTTOM);
        healthScore = Ui.text(host, "—", 26f, Ui.TEXT, true);
        scoreRow.addView(healthScore);
        TextView outOf = Ui.text(host, "/100", 9f, Ui.DIM, false);
        LinearLayout.LayoutParams oolp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        oolp.leftMargin = Ui.dp(host, 2);
        oolp.bottomMargin = Ui.dp(host, 4);
        scoreRow.addView(outOf, oolp);
        scoreCol.addView(scoreRow);
        healthGrade = Ui.text(host, "待体检", 9.5f, Ui.DIM, true);
        healthGrade.setGravity(Gravity.END);
        scoreCol.addView(healthGrade);
        head.addView(scoreCol);
        card.addView(head);
        healthChecks = Ui.column(host);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hclp.topMargin = Ui.dp(host, 10);
        card.addView(healthChecks, hclp);
        healthMeta = Ui.small(host, "点一下，检查当前网络的真实可用性");
        healthMeta.setMaxLines(2);
        card.addView(healthMeta, topMargin(10));
        healthButton = Ui.outlineButton(host, "立即体检");
        LinearLayout.LayoutParams hblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hblp.topMargin = Ui.dp(host, 12);
        card.addView(healthButton, hblp);
        healthButton.setOnClickListener(v -> runHealth(true));
        content.addView(card);
    }

    private void buildSignalCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "信号趋势"));
        TextView sub = Ui.small(host, "最近 90 次采样 · 约 2 分钟");
        titleCol.addView(sub, topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        signalCurrent = Ui.text(host, "—", 15f, Ui.ACCENT, true);
        head.addView(signalCurrent);
        card.addView(head);

        signalChart = new TrendChartView(host);
        signalChart.setFixedRange(-110, -30);
        signalChart.setColor(Ui.ACCENT);
        signalChart.setEmptyText("等待信号采样…");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 92));
        clp.topMargin = Ui.dp(host, 9);
        card.addView(signalChart, clp);

        LinearLayout stats = Ui.row(host);
        LinearLayout minCol = Ui.column(host);
        minCol.addView(Ui.label(host, "最低"));
        signalMin = Ui.text(host, "—", 12f, Ui.MUTED, true);
        minCol.addView(signalMin, topMargin(3));
        stats.addView(minCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout avgCol = Ui.column(host);
        avgCol.addView(Ui.label(host, "平均"));
        signalAvg = Ui.text(host, "—", 12f, Ui.TEXT, true);
        avgCol.addView(signalAvg, topMargin(3));
        stats.addView(avgCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout maxCol = Ui.column(host);
        maxCol.addView(Ui.label(host, "最高"));
        signalMax = Ui.text(host, "—", 12f, Ui.MUTED, true);
        maxCol.addView(signalMax, topMargin(3));
        stats.addView(maxCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(stats, topMargin(10));
        content.addView(card);
    }

    private void buildTrafficCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "流量统计"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "本机", Ui.BLUE));
        card.addView(head);
        LinearLayout row1 = Ui.row(host);
        LinearLayout rxC = trafficMetric("本次运行 · 接收", Ui.ACCENT);
        trafficSession = (TextView) rxC.getChildAt(1);
        row1.addView(rxC, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout bootC = trafficMetric("开机以来 · 共接收", Ui.BLUE);
        trafficBoot = (TextView) bootC.getChildAt(1);
        row1.addView(bootC, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row1, topMargin(13));
        LinearLayout row2 = Ui.row(host);
        LinearLayout mobileC = trafficMetric("蜂窝流量 · 开机以来", Ui.PURPLE);
        trafficMobile = (TextView) mobileC.getChildAt(1);
        row2.addView(mobileC, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout appC = trafficMetric("网脉自身 · 收发", Ui.AMBER);
        trafficApp = (TextView) appC.getChildAt(1);
        row2.addView(appC, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row2, topMargin(12));
        TextView note = Ui.small(host, "统计来自 Android TrafficStats；含系统与应用全部流量。");
        card.addView(note, topMargin(11));
        content.addView(card);
    }

    private LinearLayout trafficMetric(String label, int color) {
        LinearLayout l = Ui.column(host);
        TextView labelView = Ui.text(host, label, 9.5f, Ui.DIM, false);
        l.addView(labelView);
        TextView value = Ui.text(host, "—", 15f, color, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Ui.dp(host, 4);
        l.addView(value, vlp);
        return l;
    }

    private void buildQuickActions() {
        LinearLayout card = Ui.card(host);
        card.addView(Ui.title(host, "快捷操作"));
        LinearLayout row = Ui.row(host);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(host, 12);
        card.addView(row, rowLp);

        LinearLayout speed = actionTile("speed", "立即测速", "云端并发测速", Ui.ACCENT);
        speed.setOnClickListener(v -> host.switchTab(3));
        row.addView(speed, actionLp(0));
        LinearLayout scan = actionTile("scan", "扫描 Wi-Fi", "信道与邻居", Ui.BLUE);
        scan.setOnClickListener(v -> host.switchTab(1));
        row.addView(scan, actionLp(8));
        LinearLayout report = actionTile("share", "分享报告", "完整诊断文本", Ui.PURPLE);
        report.setOnClickListener(v -> host.shareReport());
        row.addView(report, actionLp(8));
        content.addView(card);
    }

    private LinearLayout actionTile(String icon, String title, String sub, int color) {
        LinearLayout tile = Ui.column(host);
        GradientDrawable bg = Ui.rounded(host, 0x0CFFFFFF, 14);
        bg.setStroke(Ui.dp(host, 1), 0x1CFFFFFF);
        tile.setBackground(bg);
        tile.setPadding(Ui.dp(host, 11), Ui.dp(host, 11), Ui.dp(host, 8), Ui.dp(host, 11));
        tile.setClickable(true);
        IconView iv = new IconView(host);
        iv.setIcon(icon);
        iv.setColor(color);
        tile.addView(iv, new LinearLayout.LayoutParams(Ui.dp(host, 20), Ui.dp(host, 20)));
        TextView t = Ui.text(host, title, 11.5f, Ui.TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(host, 10);
        tile.addView(t, tlp);
        TextView st = Ui.text(host, sub, 8.5f, Ui.DIM, false);
        st.setSingleLine(true);
        tile.addView(st, topMargin(3));
        return tile;
    }

    private LinearLayout.LayoutParams actionLp(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(host, left);
        return lp;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (host.latestSnapshot() != null) {
            update(host.latestSnapshot(), 0, 0);
        }
        if (host.healthReport() != null && !checking) {
            applyHealth(host.healthReport());
        }
    }

    @Override
    public void onHide() {}

    @Override
    public void update(NetProbe.Snapshot snapshot, double downloadMbps, double uploadMbps) {
        if (snapshot == null) return;
        updatePermission(snapshot);
        boolean wifi = snapshot.wifi != null && snapshot.wifi.connected;
        NetProbe.CellSim cell = pickSim(snapshot);
        int signalColor;
        float signal;
        if (wifi) {
            NetProbe.WifiData w = snapshot.wifi;
            styleChip(heroChip, wifiChipText(w), Ui.ACCENT);
            heroTitle.setText(w.ssid == null || w.ssid.isEmpty() ? "未知 Wi-Fi" : w.ssid);
            String band = w.band == null ? "—" : w.band;
            String std = w.standard == null ? "" : " · " + w.standard;
            heroSub.setText(band + " · 信道 " + (w.channel <= 0 ? "—" : w.channel)
                    + (w.widthMhz > 0 ? " · " + w.widthMhz + " MHz" : "") + std);
            signal = w.rssi;
            heroSignal.setText(w.rssi > -120 ? String.valueOf(w.rssi) : "—");
            signalColor = Ui.wifiQualityColor(w.rssi);
            heroQuality.setText(w.rssi > -120 ? Ui.wifiQuality(w.rssi) : "无信号");
            heroQuality.setTextColor(signalColor);
            heroBars.set(NetProbe.wifiLevel(w.rssi), signalColor);
            heroLink.setText(w.linkSpeedMbps > 0 ? w.linkSpeedMbps + "" : "—");
            heroLinkLabel.setText("协商 Mbps");
            rateMeta.setText(w.widthMhz > 0 ? w.widthMhz + "MHz · " + compactBand(band) : "—");
            ipLocal.setText("本地 " + nz(w.ipv4 == null || w.ipv4.isEmpty() ? w.ipv6 : w.ipv4));
            appendHistory(wifiHistory, w.rssi);
            heroSpark.setData(toArray(wifiHistory));
            heroSpark.setColor(signalColor);
            signalChart.setFixedRange(-100, -30);
            signalChart.setData(toArray(wifiHistory));
            signalChart.setColor(signalColor);
            signalCurrent.setText(w.rssi + " dBm");
            signalCurrent.setTextColor(signalColor);
            updateSignalStats(wifiHistory);
            ipPublic.setText("公网 " + nz(publicIp()));
        } else if (cell != null && cell.signalDbm != Integer.MIN_VALUE) {
            styleChip(heroChip, cell.networkType == null ? "蜂窝" : cell.networkType,
                    cell.fiveG ? Ui.PURPLE : Ui.BLUE);
            heroTitle.setText(cell.carrier);
            heroSub.setText("SIM " + cell.slot + " · " + cell.voiceService
                    + (cell.dataState == null ? "" : " · " + cell.dataState));
            signal = cell.signalDbm;
            heroSignal.setText(String.valueOf(cell.signalDbm));
            signalColor = Ui.cellQualityColor(cell.signalDbm);
            heroQuality.setText(Ui.cellQuality(cell.signalDbm));
            heroQuality.setTextColor(signalColor);
            heroBars.set(cell.signalLevel, signalColor);
            heroLink.setText(cell.networkType);
            heroLinkLabel.setText("当前制式");
            rateMeta.setText(cell.fiveG ? "5G" : "蜂窝数据");
            ipLocal.setText("本地 " + nz(snapshot.ipv4));
            appendHistory(cellHistory, cell.signalDbm);
            heroSpark.setData(toArray(cellHistory));
            heroSpark.setColor(signalColor);
            signalChart.setFixedRange(-130, -60);
            signalChart.setData(toArray(cellHistory));
            signalChart.setColor(signalColor);
            signalCurrent.setText(cell.signalDbm + " dBm");
            signalCurrent.setTextColor(signalColor);
            updateSignalStats(cellHistory);
            ipPublic.setText("公网 " + nz(publicIp()));
        } else {
            styleChip(heroChip, snapshot.transport == null ? "离线" : snapshot.transport, Ui.AMBER);
            heroTitle.setText(wifi ? "Wi-Fi 信息受限" : (snapshot.sims.isEmpty() ? "没有活动网络" : "等待蜂窝信号"));
            heroSub.setText("检查 Wi-Fi、SIM 卡与系统权限");
            heroSignal.setText("—");
            heroQuality.setText("—");
            heroQuality.setTextColor(Ui.DIM);
            heroBars.set(0, Ui.DIM);
            heroLink.setText("—");
            heroLinkLabel.setText("链路速率");
            rateMeta.setText("—");
            ipLocal.setText("本地 " + nz(snapshot.ipv4));
            signalChart.setData(new float[0]);
            signalChart.setFixedRange(-130, -30);
            signalCurrent.setText("—");
            signalCurrent.setTextColor(Ui.DIM);
            signalMin.setText("—");
            signalAvg.setText("—");
            signalMax.setText("—");
            ipPublic.setText("公网 " + nz(publicIp()));
        }

        rateDown.setText(NetProbe.formatMbps(downloadMbps) + " Mbps");
        rateUp.setText(NetProbe.formatMbps(uploadMbps) + " Mbps");
        if (sessionRx < 0 || snapshot.totalRx < sessionRx) {
            sessionRx = snapshot.totalRx;
            sessionTx = snapshot.totalTx;
        }
        long srx = Math.max(0, snapshot.totalRx - sessionRx);
        long stx = Math.max(0, snapshot.totalTx - sessionTx);
        trafficSession.setText("↓ " + NetProbe.formatBytes(srx) + "  ↑ " + NetProbe.formatBytes(stx));
        trafficBoot.setText(NetProbe.formatBytes(snapshot.totalRx));
        trafficMobile.setText("↓ " + NetProbe.formatBytes(snapshot.mobileRx)
                + "  ↑ " + NetProbe.formatBytes(snapshot.mobileTx));
        trafficApp.setText("↓ " + NetProbe.formatBytes(snapshot.appRx)
                + "  ↑ " + NetProbe.formatBytes(snapshot.appTx));

        if (host.healthReport() != null && !checking && healthChecks.getChildCount() == 0) {
            applyHealth(host.healthReport());
        } else if (!checking && !autoCheckScheduled && snapshot.online) {
            autoCheckScheduled = true;
            postDelayed(() -> {
                if (!checking) runHealth(false);
            }, 1200);
        }
    }

    private void updatePermission(NetProbe.Snapshot s) {
        boolean fine = NetProbe.hasFineLocation(host);
        boolean phone = NetProbe.hasPhonePermission(host);
        boolean loc = s.locationEnabled;
        boolean need = !fine || !phone || !loc;
        permissionCard.setVisibility(need ? View.VISIBLE : View.GONE);
        if (!need) return;
        if (!fine && !phone) {
            permissionText.setText("授予定位与电话权限，才能读取 SSID、附近热点、信道和双卡基站。");
            permissionAction.setText("去授权");
        } else if (!fine) {
            permissionText.setText("缺少精确定位权限，Wi-Fi 名称和扫描列表会被系统隐藏。");
            permissionAction.setText("去授权");
        } else if (!phone) {
            permissionText.setText("缺少电话权限，无法读取 SIM 卡、运营商和蜂窝信号。");
            permissionAction.setText("去授权");
        } else {
            permissionText.setText("系统定位服务已关闭。Android 限制：关闭定位时 WiFi/基站详情会被隐藏。");
            permissionAction.setText("开启定位");
        }
    }

    private void runHealth(boolean force) {
        if (checking) return;
        checking = true;
        healthScore.setText("…");
        healthGrade.setText("检测中");
        healthGrade.setTextColor(Ui.AMBER);
        healthMeta.setText("正在测试外网、DNS、网关、信号与抖动…");
        healthButton.setText("检测中，请稍候");
        healthButton.setAlpha(0.55f);
        healthButton.setEnabled(false);
        host.requestHealth(force, this::onHealth);
    }

    private void onHealth(Diagnostics.Report report) {
        checking = false;
        healthButton.setText("重新体检");
        healthButton.setAlpha(1f);
        healthButton.setEnabled(true);
        if (report != null) applyHealth(report);
    }

    private void applyHealth(Diagnostics.Report report) {
        healthScore.setText(String.valueOf(report.score));
        healthScore.setTextColor(report.score >= 75 ? Ui.ACCENT : report.score >= 55 ? Ui.AMBER : Ui.RED);
        healthGrade.setText(report.grade);
        healthGrade.setTextColor(report.score >= 75 ? Ui.ACCENT : report.score >= 55 ? Ui.AMBER : Ui.RED);
        healthMeta.setText(report.summary);
        healthChecks.removeAllViews();
        for (Diagnostics.Check check : report.checks) {
            LinearLayout row = Ui.row(host);
            View dot = new View(host);
            int color = check.status == Diagnostics.PASS ? Ui.ACCENT
                    : check.status == Diagnostics.WARN ? Ui.AMBER : Ui.RED;
            dot.setBackground(Ui.rounded(host, color, 99));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(host, 6), Ui.dp(host, 6));
            dlp.rightMargin = Ui.dp(host, 9);
            row.addView(dot, dlp);
            LinearLayout col = Ui.column(host);
            TextView name = Ui.text(host, check.name, 11f, Ui.TEXT, true);
            TextView detail = Ui.text(host, check.detail, 9.2f, Ui.DIM, false);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name);
            col.addView(detail, topMargin(2));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pts = Ui.mono(host, check.earned + "/" + check.weight, 9f, color);
            row.addView(pts);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(host, 9);
            healthChecks.addView(row, rlp);
        }
        if (!report.suggestions.isEmpty()) {
            TextView tip = Ui.small(host, "提示：" + report.suggestions.get(0));
            tip.setMaxLines(3);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(host, 10);
            healthChecks.addView(tip, tlp);
        }
    }

    private void updateSignalStats(List<Float> list) {
        if (list.isEmpty()) return;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0;
        for (float v : list) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        signalMin.setText(Math.round(min) + " dBm");
        signalAvg.setText(Math.round(sum / list.size()) + " dBm");
        signalMax.setText(Math.round(max) + " dBm");
    }

    private NetProbe.CellSim pickSim(NetProbe.Snapshot s) {
        if (s.sims == null || s.sims.isEmpty()) return null;
        NetProbe.CellSim best = null;
        for (NetProbe.CellSim sim : s.sims) {
            if (sim.error != null) continue;
            if (best == null) best = sim;
            if (sim.defaultData) return sim;
            if (sim.signalDbm != Integer.MIN_VALUE
                    && (best.signalDbm == Integer.MIN_VALUE || sim.signalDbm > best.signalDbm)) best = sim;
        }
        return best;
    }

    private String wifiChipText(NetProbe.WifiData w) {
        String std = w.standard == null ? "WI-FI" : w.standard;
        if (std.contains("Wi-Fi 6")) return "WI-FI 6";
        if (std.contains("Wi-Fi 5")) return "WI-FI 5";
        if (std.contains("Wi-Fi 7")) return "WI-FI 7";
        return "WI-FI";
    }

    private String compactBand(String band) {
        if (band == null) return "—";
        if (band.startsWith("2.4")) return "2.4G";
        if (band.startsWith("5")) return "5G";
        if (band.startsWith("6")) return "6G";
        return band;
    }

    private String publicIp() {
        PublicInfoService.PublicInfo p = host.publicInfo();
        if (p == null) return "获取中…";
        if (p.ip == null || p.ip.isEmpty()) return "不可用";
        return p.ip;
    }

    private String nz(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private void styleChip(TextView chip, String text, int color) {
        if (TextUtils.equals(chip.getText(), text) && chip.getTag() instanceof Integer
                && (Integer) chip.getTag() == color) return;
        chip.setTag(color);
        chip.setText(text);
        chip.setTextColor(color);
        GradientDrawable bg = Ui.rounded(host, Ui.withAlpha(color, 0.12f), 99);
        bg.setStroke(Ui.dp(host, 1), Ui.withAlpha(color, 0.28f));
        chip.setBackground(bg);
    }

    private void appendHistory(List<Float> list, float value) {
        if (!Float.isFinite(value) || value <= -120) return;
        list.add(value);
        while (list.size() > 90) list.remove(0);
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.max(0, Ui.dp(host, dp));
        return lp;
    }
}
