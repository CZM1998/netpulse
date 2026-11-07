package com.netpulse.app;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WifiScreen extends ScrollView implements MainActivity.Screen {
    private final MainActivity host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout content;
    private final List<Float> rssiHistory = new ArrayList<>();
    private final List<NetProbe.WifiAp> scanResults = new ArrayList<>();

    private TextView stateChip, heroTitle, heroBssid, statusChip, heroSignal, heroQuality, heroLink, heroLinkLabel;
    private SignalBarsView bars;
    private TrendChartView trend;
    private TextView specChannel, specBand, specWidth, specStandard, specSecurity, specLink;
    private TextView detailIp, detailGateway, detailDns, detailBssid, detailMac, detailIface, detailLinkRates, detailIpv6;
    private TextView wifiNote;
    private LinearLayout permissionCard;
    private TextView permissionText, permissionAction;

    private TextView apCount, scanButton, nearbyMeta;
    private LinearLayout nearbyList;
    private ChannelSpectrumView spectrum;
    private LinearLayout channelLegend;
    private String lastLegendSignature = "";
    private TextView band24, band5, channelSummary, channelAdvice;

    private boolean fiveGhz;
    private boolean bandInitialized;
    private boolean scanning;
    private boolean scanAttempted;
    private boolean lastPermissionOk;
    private boolean expanded;
    private long lastScanAt;
    private int lastScanCount = -1;

    public WifiScreen(MainActivity host) {
        super(host);
        this.host = host;
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setPadding(0, Ui.dp(host, 2), 0, Ui.dp(host, 16));
        content = Ui.column(host);
        content.setPadding(Ui.dp(host, 16), 0, Ui.dp(host, 16), Ui.dp(host, 26));
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private void build() {
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.section(host, "Wi-Fi 分析"));
        titleCol.addView(Ui.small(host, "链路参数、邻居热点与信道占用"), topMargin(4));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = Ui.quietButton(host, "系统设置", Ui.MUTED);
        settings.setOnClickListener(v -> host.openWifiSettings());
        head.addView(settings);
        content.addView(head, topMargin(8));

        buildPermissionCard();
        buildCurrentCard();
        buildChannelCard();
        buildNearbyCard();
        TextView footer = Ui.small(host, "扫描需要精确定位权限 + 系统定位开启 · Android 限制扫描频率");
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dp(host, 6);
        content.addView(footer, flp);
    }

    private void buildPermissionCard() {
        permissionCard = Ui.flatCard(host, 0x173B2B1A);
        GradientDrawable bg = Ui.rounded(host, 0x173B2B1A, 14);
        bg.setStroke(Ui.dp(host, 1), 0x55FFC266);
        permissionCard.setBackground(bg);
        LinearLayout row = Ui.row(host);
        LinearLayout iconWrap = Ui.row(host);
        iconWrap.setGravity(Gravity.CENTER);
        iconWrap.setBackground(Ui.rounded(host, 0x22FFC266, 10));
        IconView icon = new IconView(host);
        icon.setIcon("scan");
        icon.setColor(Ui.AMBER);
        iconWrap.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 18), Ui.dp(host, 18)));
        row.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(host, 34), Ui.dp(host, 34)));
        LinearLayout textCol = Ui.column(host);
        textCol.addView(Ui.text(host, "扫描需要定位权限", 12.5f, Ui.TEXT, true));
        permissionText = Ui.small(host, "Android 要求定位权限才能返回 SSID 与热点列表");
        permissionText.setMaxLines(3);
        textCol.addView(permissionText, topMargin(3));
        LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tclp.leftMargin = Ui.dp(host, 10);
        row.addView(textCol, tclp);
        permissionAction = Ui.quietButton(host, "去授权", Ui.AMBER);
        row.addView(permissionAction);
        permissionAction.setOnClickListener(v -> host.requestCorePermissions());
        permissionCard.addView(row);
        permissionCard.setVisibility(View.GONE);
        content.addView(permissionCard);
    }

    private void buildCurrentCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout top = Ui.row(host);
        stateChip = Ui.chip(host, "WI-FI", Ui.ACCENT);
        top.addView(stateChip);
        statusChip = Ui.chip(host, "连接中", Ui.MUTED);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sclp.leftMargin = Ui.dp(host, 7);
        top.addView(statusChip, sclp);
        View spacer = new View(host);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView open = Ui.quietButton(host, "打开 Wi-Fi 设置 ↗", Ui.BLUE);
        open.setOnClickListener(v -> host.openWifiSettings());
        top.addView(open);
        card.addView(top);

        heroTitle = Ui.text(host, "正在读取…", 26f, Ui.TEXT, true);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.topMargin = Ui.dp(host, 13);
        card.addView(heroTitle, htlp);
        heroBssid = Ui.mono(host, "BSSID —", 9.5f, Ui.DIM);
        heroBssid.setSingleLine(true);
        heroBssid.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        card.addView(heroBssid, topMargin(5));

        LinearLayout metrics = Ui.row(host);
        metrics.setGravity(Gravity.BOTTOM);
        bars = new SignalBarsView(host);
        bars.setBars(4);
        bars.set(0, Ui.DIM);
        metrics.addView(bars, new LinearLayout.LayoutParams(Ui.dp(host, 32), Ui.dp(host, 21)));
        heroSignal = Ui.text(host, "—", 33f, Ui.TEXT, true);
        LinearLayout.LayoutParams hslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hslp.leftMargin = Ui.dp(host, 10);
        metrics.addView(heroSignal, hslp);
        TextView dbm = Ui.text(host, "dBm", 10.5f, Ui.MUTED, false);
        LinearLayout.LayoutParams dblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dblp.leftMargin = Ui.dp(host, 5);
        dblp.bottomMargin = Ui.dp(host, 4);
        metrics.addView(dbm, dblp);
        heroQuality = Ui.text(host, "—", 11f, Ui.MUTED, true);
        LinearLayout.LayoutParams hqlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hqlp.leftMargin = Ui.dp(host, 8);
        hqlp.bottomMargin = Ui.dp(host, 6);
        metrics.addView(heroQuality, hqlp);
        View stretch = new View(host);
        metrics.addView(stretch, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout linkCol = Ui.column(host);
        linkCol.setGravity(Gravity.END);
        heroLink = Ui.text(host, "—", 18f, Ui.TEXT, true);
        heroLink.setGravity(Gravity.END);
        linkCol.addView(heroLink);
        heroLinkLabel = Ui.text(host, "协商速率", 9f, Ui.DIM, false);
        heroLinkLabel.setGravity(Gravity.END);
        linkCol.addView(heroLinkLabel, topMargin(2));
        metrics.addView(linkCol);
        card.addView(metrics, topMargin(11));

        trend = new TrendChartView(host);
        trend.setShowLabels(true);
        trend.setFixedRange(-90, -30);
        trend.setColor(Ui.ACCENT);
        trend.setEmptyText("连接 Wi-Fi 后开始记录 RSSI");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 74));
        tlp.topMargin = Ui.dp(host, 3);
        card.addView(trend, tlp);

        LinearLayout triad1 = Ui.row(host);
        LinearLayout c1 = compactMetric(triad1, "信道", true);
        specChannel = (TextView) c1.getChildAt(1);
        LinearLayout c2 = compactMetric(triad1, "频段", false);
        specBand = (TextView) c2.getChildAt(1);
        LinearLayout c3 = compactMetric(triad1, "带宽", false);
        specWidth = (TextView) c3.getChildAt(1);
        card.addView(triad1, topMargin(11));

        LinearLayout triad2 = Ui.row(host);
        LinearLayout c4 = compactMetric(triad2, "协议", false);
        specStandard = (TextView) c4.getChildAt(1);
        LinearLayout c5 = compactMetric(triad2, "加密", false);
        specSecurity = (TextView) c5.getChildAt(1);
        LinearLayout c6 = compactMetric(triad2, "链路速率", false);
        specLink = (TextView) c6.getChildAt(1);
        card.addView(triad2, topMargin(9));

        detailIp = detailValue(card, "IP 地址");
        detailGateway = detailValue(card, "网关");
        detailDns = detailValue(card, "DNS");
        detailBssid = detailValue(card, "BSSID");
        detailMac = detailValue(card, "本机 MAC");
        detailIface = detailValue(card, "接口 / MTU");
        detailLinkRates = detailValue(card, "TX / RX 链路");
        detailIpv6 = detailValue(card, "IPv6");
        wifiNote = Ui.text(host, "", 10f, Ui.AMBER, false);
        wifiNote.setMaxLines(3);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = Ui.dp(host, 8);
        card.addView(wifiNote, noteLp);
        content.addView(card);
    }

    private LinearLayout compactMetric(LinearLayout parent, String label, boolean first) {
        LinearLayout cell = Ui.column(host);
        if (!first) {
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cellLp.leftMargin = Ui.dp(host, 8);
            parent.addView(cell, cellLp);
        } else {
            parent.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        cell.addView(Ui.text(host, label, 9f, Ui.DIM, false));
        TextView value = Ui.text(host, "—", 12.5f, Ui.TEXT, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(value, topMargin(4));
        return cell;
    }

    private TextView detailValue(LinearLayout parent, String label) {
        LinearLayout row = Ui.row(host);
        TextView key = Ui.text(host, label, 10f, Ui.DIM, false);
        row.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        TextView value = Ui.text(host, "—", 10.5f, Ui.MUTED, false);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(host, 8);
        parent.addView(row, rlp);
        return value;
    }

    private void buildChannelCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "信道分析"));
        titleCol.addView(Ui.small(host, "编号/颜色对应图例 · 峰/柱高度=强度"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout toggle = Ui.row(host);
        GradientDrawable tbg = Ui.rounded(host, 0x0FFFFFFF, 10);
        tbg.setStroke(Ui.dp(host, 1), Ui.BORDER);
        toggle.setBackground(tbg);
        toggle.setPadding(Ui.dp(host, 3), Ui.dp(host, 3), Ui.dp(host, 3), Ui.dp(host, 3));
        band24 = Ui.text(host, "2.4G", 9.5f, Ui.TEXT, true);
        band24.setGravity(Gravity.CENTER);
        band24.setPadding(Ui.dp(host, 9), Ui.dp(host, 5), Ui.dp(host, 9), Ui.dp(host, 5));
        band24.setClickable(true);
        band5 = Ui.text(host, "5G", 9.5f, Ui.MUTED, true);
        band5.setGravity(Gravity.CENTER);
        band5.setPadding(Ui.dp(host, 9), Ui.dp(host, 5), Ui.dp(host, 9), Ui.dp(host, 5));
        band5.setClickable(true);
        toggle.addView(band24);
        toggle.addView(band5);
        head.addView(toggle);
        card.addView(head);
        band24.setOnClickListener(v -> setBand(false));
        band5.setOnClickListener(v -> setBand(true));

        spectrum = new ChannelSpectrumView(host);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 215));
        slp.topMargin = Ui.dp(host, 8);
        card.addView(spectrum, slp);

        channelLegend = Ui.column(host);
        card.addView(channelLegend, topMargin(5));

        channelSummary = Ui.text(host, "等待扫描结果…", 11.5f, Ui.TEXT, true);
        card.addView(channelSummary, topMargin(12));
        channelAdvice = Ui.small(host, "扫描附近 Wi-Fi 后给出信道建议");
        channelAdvice.setMaxLines(3);
        card.addView(channelAdvice, topMargin(4));
        content.addView(card);
        styleBandToggle();
    }

    private void buildNearbyCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "附近网络"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        apCount = Ui.chip(host, "0", Ui.MUTED);
        head.addView(apCount);
        scanButton = Ui.quietButton(host, "扫描", Ui.ACCENT);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.leftMargin = Ui.dp(host, 12);
        head.addView(scanButton, sblp);
        scanButton.setOnClickListener(v -> scanWifi(true));
        card.addView(head);
        nearbyMeta = Ui.small(host, "尚未扫描。点击「扫描」查看邻居热点。");
        nearbyMeta.setMaxLines(3);
        card.addView(nearbyMeta, topMargin(8));
        nearbyList = Ui.column(host);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(host, 4);
        card.addView(nearbyList, nlp);
        content.addView(card);
    }

    private void setBand(boolean five) {
        fiveGhz = five;
        styleBandToggle();
        applyChart();
    }

    private void styleBandToggle() {
        if (band24 == null) return;
        styleSegment(band24, !fiveGhz);
        styleSegment(band5, fiveGhz);
    }

    private void styleSegment(TextView v, boolean selected) {
        v.setTextColor(selected ? 0xFF061611 : Ui.MUTED);
        v.setBackground(selected ? Ui.rounded(host, Ui.ACCENT, 8) : Ui.rounded(host, 0x00000000, 8));
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (host.latestSnapshot() != null) update(host.latestSnapshot(), 0, 0);
        if (canScan() && scanResults.isEmpty() && !scanAttempted) {
            scanAttempted = true;
            postDelayed(() -> scanWifi(false), 250);
        }
    }

    @Override
    public void onHide() {}

    @Override
    public void update(NetProbe.Snapshot snapshot, double downloadMbps, double uploadMbps) {
        if (snapshot == null) return;
        boolean permOk = canScan();
        if (permOk && !lastPermissionOk) scanAttempted = false;
        lastPermissionOk = permOk;
        updatePermission(permOk, snapshot);

        NetProbe.WifiData w = snapshot.wifi;
        boolean connected = w != null && w.connected;
        if (w == null) return;
        if (!bandInitialized && connected && w.frequency > 0) {
            bandInitialized = true;
            fiveGhz = w.frequency >= 4900;
            styleBandToggle();
            applyChart();
        }
        styleChip(stateChip, chipText(w), connected ? Ui.ACCENT : Ui.MUTED);
        styleChip(statusChip, !w.enabled ? "已关闭" : connected ? "已连接" : "未连接",
                !w.enabled ? Ui.AMBER : connected ? Ui.ACCENT : Ui.MUTED);
        if (!w.enabled) {
            heroTitle.setText("Wi-Fi 已关闭");
            heroBssid.setText("打开 Wi-Fi 后显示链路参数");
            heroSignal.setText("—");
            heroQuality.setText("—");
            heroQuality.setTextColor(Ui.MUTED);
            bars.set(0, Ui.DIM);
            heroLink.setText("—");
            fillSpec(w, false);
            trend.setData(new float[0]);
            wifiNote.setText("打开 Wi-Fi 开关后可读取实时信号、邻居热点和信道占用。");
            wifiNote.setVisibility(View.VISIBLE);
            applyChart();
            return;
        }
        if (!connected) {
            heroTitle.setText("未连接 Wi-Fi");
            heroBssid.setText("BSSID —");
            heroSignal.setText("—");
            heroQuality.setText("—");
            heroQuality.setTextColor(Ui.MUTED);
            bars.set(0, Ui.DIM);
            heroLink.setText("—");
            fillSpec(w, false);
            trend.setData(new float[0]);
            wifiNote.setText(w.error != null ? w.error : "选择一个网络连接后即可查看实时 RSSI 与信道。");
            wifiNote.setVisibility(View.VISIBLE);
            applyChart();
            return;
        }

        heroTitle.setText(w.ssidKnown ? w.ssid : "Wi-Fi 名称受限");
        heroBssid.setText("BSSID " + nz(w.bssid));
        heroSignal.setText(w.rssi > -120 ? String.valueOf(w.rssi) : "—");
        int color = Ui.wifiQualityColor(w.rssi);
        heroQuality.setText(w.rssi > -120 ? Ui.wifiQuality(w.rssi) : "未知");
        heroQuality.setTextColor(color);
        bars.set(NetProbe.wifiLevel(w.rssi), color);
        heroLink.setText(w.linkSpeedMbps > 0 ? String.valueOf(w.linkSpeedMbps) : "—");
        heroLinkLabel.setText("协商 Mbps");
        fillSpec(w, true);
        if (w.rssi > -120) {
            appendHistory(w.rssi);
            trend.setData(toArray(rssiHistory));
            trend.setColor(color);
        }
        String note = "";
        if (w.securityNote != null) note = w.securityNote;
        if (w.error != null) note = w.error;
        wifiNote.setText(note);
        wifiNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
        applyChart();
    }

    private void fillSpec(NetProbe.WifiData w, boolean connected) {
        specChannel.setText(connected && w.channel > 0 ? String.valueOf(w.channel) : "—");
        specBand.setText(connected ? nz(w.band) : "—");
        specWidth.setText(connected && w.widthMhz > 0 ? Math.round(w.widthMhz) + "MHz" : "—");
        specStandard.setText(connected ? compactStandard(w.standard) : "—");
        specSecurity.setText(connected ? shortSecurity(w.security) : "—");
        specLink.setText(connected && w.linkSpeedMbps > 0 ? w.linkSpeedMbps + " Mbps" : "—");
        detailIp.setText(connected ? nz(prefer(w.ipv4, w.ipv6)) : "—");
        detailGateway.setText(connected ? nz(w.gateway) : "—");
        detailDns.setText(connected ? nz(w.dns) : "—");
        detailBssid.setText(connected ? nz(w.bssid) : "—");
        detailMac.setText(connected ? nz(w.mac) : "—");
        String iface = connected ? nz(w.interfaceName) : "—";
        if (connected && w.mtu > 0) iface += " · MTU " + w.mtu;
        detailIface.setText(iface);
        detailLinkRates.setText(connected
                ? (w.txLinkSpeedMbps > 0 ? w.txLinkSpeedMbps : "—") + " / "
                  + (w.rxLinkSpeedMbps > 0 ? w.rxLinkSpeedMbps : "—") + " Mbps"
                : "—");
        detailIpv6.setText(connected ? nz(w.ipv6) : "—");
    }

    private void updatePermission(boolean permOk, NetProbe.Snapshot s) {
        boolean locationOff = !s.locationEnabled;
        if (permOk && !locationOff) {
            permissionCard.setVisibility(View.GONE);
            return;
        }
        permissionCard.setVisibility(View.VISIBLE);
        if (!permOk) {
            permissionText.setText("扫描附近网络需要精确定位权限（Android 系统限制），网脉不会读取或上传你的位置。");
            permissionAction.setText("去授权");
        } else {
            permissionText.setText("系统定位服务已关闭，SSID 与附近热点会被 Android 隐藏。请开启定位。");
            permissionAction.setText("开启定位");
        }
    }

    private boolean canScan() {
        return NetProbe.hasFineLocation(host) && NetProbe.hasNearbyWifiPermission(host)
                && host.probe().isLocationEnabled();
    }

    private void scanWifi(boolean manual) {
        if (scanning) return;
        if (!canScan()) {
            if (manual) {
                updatePermission(false, host.latestSnapshot() == null ? new NetProbe.Snapshot() : host.latestSnapshot());
                host.requestCorePermissions();
            }
            return;
        }
        if (manual && System.currentTimeMillis() - lastScanAt < 4500 && lastScanCount >= 0) {
            Ui.toast(host, "系统限制扫描频率，已展示最近一次结果");
            applyScanResults(host.probe().scanResults());
            return;
        }
        scanning = true;
        scanButton.setText("扫描中…");
        scanButton.setTextColor(Ui.DIM);
        scanButton.setEnabled(false);
        nearbyMeta.setText("正在请求系统 Wi-Fi 扫描…");
        boolean started = host.probe().startWifiScan();
        main.postDelayed(() -> {
            List<NetProbe.WifiAp> results = host.probe().scanResults();
            scanning = false;
            scanButton.setText("扫描");
            scanButton.setTextColor(Ui.ACCENT);
            scanButton.setEnabled(true);
            lastScanAt = System.currentTimeMillis();
            applyScanResults(results);
            if (!started && results.isEmpty()) {
                nearbyMeta.setText("系统拒绝了本次扫描（扫描频率限制或权限不足）。请稍后再试或检查定位。");
            }
        }, started ? 1700 : 350);
    }

    private void applyScanResults(List<NetProbe.WifiAp> results) {
        scanResults.clear();
        if (results != null) scanResults.addAll(results);
        lastScanCount = scanResults.size();
        NetProbe.WifiData w = host.latestSnapshot() == null ? null : host.latestSnapshot().wifi;
        String currentBssid = w == null ? null : w.bssid;
        for (NetProbe.WifiAp ap : scanResults) {
            ap.connected = ap.bssid != null && ap.bssid.equalsIgnoreCase(currentBssid == null ? "" : currentBssid);
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        if (scanResults.isEmpty()) {
            nearbyMeta.setText("扫描于 " + time + " · 没有发现热点。请确认定位已开启，并靠近路由器后重试。");
        } else {
            nearbyMeta.setText("扫描于 " + time + " · 共 " + scanResults.size() + " 个热点 · 点击查看详情");
        }
        apCount.setText(String.valueOf(scanResults.size()));
        apCount.setTextColor(scanResults.isEmpty() ? Ui.MUTED : Ui.ACCENT);
        GradientDrawable cbg = Ui.rounded(host, Ui.withAlpha(scanResults.isEmpty() ? Ui.MUTED : Ui.ACCENT, 0.12f), 99);
        cbg.setStroke(Ui.dp(host, 1), Ui.withAlpha(scanResults.isEmpty() ? Ui.MUTED : Ui.ACCENT, 0.28f));
        apCount.setBackground(cbg);
        rebuildNearbyList();
        applyChart();
    }

    private void rebuildNearbyList() {
        nearbyList.removeAllViews();
        if (scanResults.isEmpty()) {
            TextView empty = Ui.small(host, canScan()
                    ? "附近没有扫描到 Wi-Fi 热点。"
                    : "授予定位权限后才能扫描附近热点。");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(host, 18), 0, Ui.dp(host, 16));
            nearbyList.addView(empty);
            return;
        }
        int limit = expanded ? scanResults.size() : Math.min(8, scanResults.size());
        for (int i = 0; i < limit; i++) {
            addApRow(scanResults.get(i), i == limit - 1 && limit == scanResults.size());
        }
        if (!expanded && scanResults.size() > limit) {
            TextView more = Ui.outlineButton(host, "查看全部 " + scanResults.size() + " 个热点");
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.topMargin = Ui.dp(host, 10);
            nearbyList.addView(more, mlp);
            more.setOnClickListener(v -> {
                expanded = true;
                rebuildNearbyList();
            });
        }
    }

    private void addApRow(NetProbe.WifiAp ap, boolean last) {
        LinearLayout row = Ui.row(host);
        row.setPadding(Ui.dp(host, 2), Ui.dp(host, 10), Ui.dp(host, 2), Ui.dp(host, 10));
        row.setClickable(true);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF),
                Ui.rounded(host, 0x00000000, 10), null));
        SignalBarsView b = new SignalBarsView(host);
        b.setBars(4);
        int color = Ui.wifiQualityColor(ap.rssi);
        b.set(NetProbe.wifiLevel(ap.rssi), color);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(host, 23), Ui.dp(host, 16));
        blp.rightMargin = Ui.dp(host, 9);
        row.addView(b, blp);
        LinearLayout col = Ui.column(host);
        LinearLayout nameRow = Ui.row(host);
        TextView name = Ui.text(host, ap.ssid, 12.5f, ap.connected ? Ui.ACCENT : Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ap.connected) {
            TextView now = Ui.chip(host, "当前", Ui.ACCENT);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(host, 6);
            nameRow.addView(now, nlp);
        }
        col.addView(nameRow);
        String band = NetProbe.bandName(ap.frequency);
        String second = band + " · CH " + (ap.channel > 0 ? ap.channel : "—")
                + " · " + ap.widthMhz + " MHz"
                + (ap.standard == null ? "" : " · " + compactStandard(ap.standard));
        TextView meta = Ui.text(host, second, 9.2f, Ui.MUTED, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(meta, topMargin(3));
        String third = ap.security + " · " + shortBssid(ap.bssid);
        TextView sec = Ui.text(host, third, 8.5f, Ui.DIM, false);
        sec.setSingleLine(true);
        sec.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(sec, topMargin(2));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rssi = Ui.mono(host, ap.rssi + "", 11f, color);
        rssi.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.dp(host, 8);
        row.addView(rssi, rlp);
        row.setOnClickListener(v -> showApDetail(ap));
        nearbyList.addView(row);
        if (!last) {
            View d = new View(host);
            d.setBackgroundColor(0x12FFFFFF);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 1));
            dlp.leftMargin = Ui.dp(host, 31);
            nearbyList.addView(d, dlp);
        }
    }

    private void showApDetail(NetProbe.WifiAp ap) {
        String msg = "BSSID：" + nz(ap.bssid)
                + "\n信号：" + ap.rssi + " dBm（" + Ui.wifiQuality(ap.rssi) + "）"
                + "\n频段：" + NetProbe.bandName(ap.frequency) + " · 信道 " + (ap.channel > 0 ? ap.channel : "—")
                + "\n频宽：" + ap.widthMhz + " MHz"
                + "\n协议：" + nz(ap.standard)
                + "\n安全：" + nz(ap.security)
                + (ap.connected ? "\n\n● 这是当前已连接的网络" : "")
                + "\n\n原始能力字段：\n" + (ap.capabilities.isEmpty() ? "—" : ap.capabilities);
        Ui.info(host, ap.ssid, msg);
    }

    private void applyChart() {
        if (spectrum == null) return;
        NetProbe.WifiData w = host.latestSnapshot() == null ? null : host.latestSnapshot().wifi;
        boolean connected = w != null && w.connected;
        List<NetProbe.WifiAp> band = new ArrayList<>();
        boolean currentInBand = connected && w.channel > 0
                && (fiveGhz ? w.channel > 14 : w.channel <= 14);
        for (NetProbe.WifiAp ap : scanResults) {
            if (fiveGhz ? ap.channel > 14 : ap.channel <= 14 && ap.channel > 0) band.add(ap);
        }
        boolean currentInList = false;
        for (NetProbe.WifiAp ap : band) {
            if (connected && ap.bssid != null && ap.bssid.equalsIgnoreCase(w.bssid)) {
                currentInList = true;
                ap.connected = true;
            }
        }
        if (!currentInList && currentInBand) {
            NetProbe.WifiAp cur = new NetProbe.WifiAp(w.ssid, w.bssid, w.rssi, w.frequency,
                    w.channel, w.widthMhz, w.security, "");
            cur.standard = w.standard;
            cur.connected = true;
            band.add(0, cur);
        }
        List<ChannelSpectrumView.Ap> chartAps = new ArrayList<>();
        for (int i = 0; i < band.size(); i++) {
            NetProbe.WifiAp ap = band.get(i);
            chartAps.add(new ChannelSpectrumView.Ap(ap.ssid, ap.rssi, ap.channel, ap.widthMhz,
                    ap.bssid, i, ChannelSpectrumView.colorForIndex(i)));
        }
        spectrum.setAccessPoints(chartAps, fiveGhz);
        spectrum.setHighlightBssid(connected ? w.bssid : null);
        rebuildLegend(band, connected ? w.bssid : null);
        updateChannelAdvice(chartAps, w, connected);
    }

    private void rebuildLegend(List<NetProbe.WifiAp> band, String currentBssid) {
        if (channelLegend == null) return;
        StringBuilder sig = new StringBuilder(fiveGhz ? "5|" : "2|");
        for (NetProbe.WifiAp ap : band) {
            sig.append(ap.bssid).append('|').append(ap.ssid).append('|')
                    .append(ap.channel).append('|').append(ap.rssi).append(';');
        }
        sig.append("cur=").append(currentBssid);
        if (sig.toString().equals(lastLegendSignature)) return;
        lastLegendSignature = sig.toString();
        channelLegend.removeAllViews();
        if (band.isEmpty()) {
            channelLegend.addView(Ui.small(host, "该频段暂无可标注的热点。"));
            return;
        }
        channelLegend.addView(Ui.text(host,
                "图例 · 编号与图中标记对应（点击查看详情）", 9f, Ui.DIM, false));
        java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
        for (NetProbe.WifiAp ap : band) nameCounts.put(ap.ssid, nameCounts.getOrDefault(ap.ssid, 0) + 1);
        LinearLayout row = null;
        for (int i = 0; i < band.size(); i++) {
            if (i % 2 == 0) {
                row = Ui.row(host);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(host, 5);
                channelLegend.addView(row, rlp);
            }
            NetProbe.WifiAp item = band.get(i);
            boolean duplicated = nameCounts.getOrDefault(item.ssid, 0) > 1;
            row.addView(legendCell(item, i, currentBssid, duplicated), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (band.size() % 2 == 1 && row != null) {
            View spacer = new View(host);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private LinearLayout legendCell(NetProbe.WifiAp ap, int index, String currentBssid,
                                    boolean duplicatedName) {
        int color = ChannelSpectrumView.colorForIndex(index);
        boolean current = currentBssid != null && ap.bssid != null
                && ap.bssid.equalsIgnoreCase(currentBssid);
        LinearLayout cell = Ui.row(host);
        cell.setPadding(Ui.dp(host, 6), Ui.dp(host, 6), Ui.dp(host, 6), Ui.dp(host, 6));
        GradientDrawable bg = Ui.rounded(host, current ? 0x1638F2C0 : 0x08FFFFFF, 8);
        bg.setStroke(Ui.dp(host, 1), current ? 0x4438F2C0 : 0x12FFFFFF);
        cell.setBackground(bg);
        cell.setClickable(true);
        TextView badge = Ui.mono(host, String.valueOf(index + 1), 8.5f, color);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Ui.medium(host));
        GradientDrawable bbg = Ui.rounded(host, Ui.withAlpha(color, 0.14f), 5);
        bbg.setStroke(Ui.dp(host, 1), Ui.withAlpha(color, 0.7f));
        badge.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(host, 20), Ui.dp(host, 20));
        blp.rightMargin = Ui.dp(host, 6);
        cell.addView(badge, blp);
        LinearLayout col = Ui.column(host);
        String name = (current ? "★ " : "") + (ap.ssid == null ? "—" : ap.ssid);
        TextView nameView = Ui.text(host, name, 9.2f, current ? Ui.ACCENT : Ui.TEXT, true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(nameView);
        String meta = "CH " + (ap.channel > 0 ? ap.channel : "—") + " · " + ap.rssi + " dBm";
        if (duplicatedName) {
            String b = ap.bssid == null ? "" : ap.bssid;
            if (b.length() >= 5) meta += " · " + b.substring(b.length() - 5);
        }
        col.addView(Ui.text(host, meta, 7.8f, Ui.DIM, false), topMargin(2));
        cell.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.setOnClickListener(v -> showApDetail(ap));
        return cell;
    }

    private void updateChannelAdvice(List<ChannelSpectrumView.Ap> aps, NetProbe.WifiData w, boolean connected) {
        boolean sameBand = connected && (fiveGhz ? w.channel > 14 : w.channel > 0 && w.channel <= 14);
        int currentChannel = sameBand ? w.channel : 0;
        if (aps.isEmpty()) {
            channelSummary.setText("暂无 " + (fiveGhz ? "5 GHz" : "2.4 GHz") + " 数据");
            channelAdvice.setText("扫描附近 Wi-Fi，或连接该频段的网络后显示信道占用。");
            return;
        }
        if (!fiveGhz) {
            int[] candidates = {1, 6, 11};
            int best = 6;
            double bestScore = Double.MAX_VALUE;
            for (int ch : candidates) {
                double score = interference(aps, ch);
                if (score < bestScore) {
                    bestScore = score;
                    best = ch;
                }
            }
            int coChannel = 0;
            for (ChannelSpectrumView.Ap ap : aps) if (ap.channel == currentChannel) coChannel++;
            channelSummary.setText("当前信道 " + (currentChannel > 0 ? currentChannel : "—")
                    + " · 同频热点 " + coChannel + " 个 · 2.4 GHz 建议 1/6/11");
            if (currentChannel > 0 && currentChannel != 1 && currentChannel != 6 && currentChannel != 11) {
                channelAdvice.setText("当前信道与相邻信道重叠，干扰风险较高。综合信号强度，推荐切换到信道 "
                        + best + "（1/6/11 中干扰最低）。");
            } else {
                channelAdvice.setText("推荐信道 " + best + "。若路由器支持，优先使用 5 GHz 或 Wi-Fi 6，"
                        + "并把 2.4 GHz 固定在 1 / 6 / 11 以减少重叠。");
            }
        } else {
            int[] candidates = {36, 40, 44, 48, 149, 153, 157, 161, 165};
            int best = 36;
            double bestScore = Double.MAX_VALUE;
            for (int ch : candidates) {
                double score = 0;
                for (ChannelSpectrumView.Ap ap : aps) if (ap.channel == ch) score += 1;
                if (score < bestScore) {
                    bestScore = score;
                    best = ch;
                }
            }
            channelSummary.setText("当前信道 " + (currentChannel > 0 ? currentChannel : "—")
                    + " · 频宽 " + (connected ? w.widthMhz : 0) + " MHz");
            if (connected && currentChannel > 0) {
                channelAdvice.setText("5 GHz 信道互不重叠；扫描到 " + aps.size()
                        + " 个 5 GHz 热点。当前信道干扰较少时保持即可，若出现拥塞可尝试 " + best + " 信道。");
            } else {
                channelAdvice.setText("推荐从非 DFS 信道中选择干扰较少的信道，例如 " + best + "。");
            }
        }
    }

    private double interference(List<ChannelSpectrumView.Ap> aps, int channel) {
        double score = 0;
        for (ChannelSpectrumView.Ap ap : aps) {
            int d = Math.abs(ap.channel - channel);
            double overlap = d == 0 ? 1.0 : d == 1 ? 0.55 : d == 2 ? 0.24 : d == 3 ? 0.07 : 0;
            if (overlap <= 0) continue;
            double strength = Math.max(0.12, Math.min(1.0, (-ap.rssi - 40) / 55.0));
            score += overlap * strength;
        }
        return score;
    }

    private void appendHistory(float value) {
        if (!Float.isFinite(value) || value <= -120) return;
        rssiHistory.add(value);
        while (rssiHistory.size() > 80) rssiHistory.remove(0);
    }

    private float[] toArray(List<Float> values) {
        float[] a = new float[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i);
        return a;
    }

    private String chipText(NetProbe.WifiData w) {
        if (w.standard == null) return "WI-FI";
        if (w.standard.contains("Wi-Fi 7")) return "WI-FI 7";
        if (w.standard.contains("Wi-Fi 6")) return "WI-FI 6";
        if (w.standard.contains("Wi-Fi 5")) return "WI-FI 5";
        if (w.standard.contains("Wi-Fi 4")) return "WI-FI 4";
        return "WI-FI";
    }

    private String compactStandard(String standard) {
        if (standard == null) return "—";
        if (standard.contains("Wi-Fi 7")) return "WiFi 7";
        if (standard.contains("Wi-Fi 6")) return "WiFi 6";
        if (standard.contains("Wi-Fi 5")) return "WiFi 5";
        if (standard.contains("Wi-Fi 4")) return "WiFi 4";
        return standard.replace("802.11ax", "ax").replace("802.11ac", "ac")
                .replace("802.11n", "n").replace("802.11be", "be");
    }

    private String shortSecurity(String security) {
        if (security == null) return "—";
        return security.replace("/WPA3-PSK", "/3").replace("WPA2-PSK", "WPA2")
                .replace("-PSK", "").replace(" 企业级", " 企业");
    }

    private String shortBssid(String bssid) {
        if (bssid == null || bssid.isEmpty()) return "—";
        return bssid.length() > 11 ? bssid.substring(0, 8) + "…" + bssid.substring(bssid.length() - 2) : bssid;
    }

    private String nz(String v) {
        return v == null || v.isEmpty() ? "—" : v;
    }

    private String prefer(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
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

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(host, dp);
        return lp;
    }
}
