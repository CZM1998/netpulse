package com.netpulse.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ToolsScreen extends ScrollView implements MainActivity.Screen {
    private final MainActivity host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout content;

    private TextView healthScore, healthGrade, healthTime, healthButton;
    private LinearLayout healthChecks;
    private long shownHealthTs = -1;
    private boolean healthChecking;

    private TextView publicIp, publicOrg, publicLocation, publicSource, localIp, localGateway, localDns;
    private LinearLayout publicInfoCard;

    private final List<PingRow> pingRows = new ArrayList<>();
    private TextView pingButton;
    private boolean pinging;

    private final List<DnsRow> dnsRows = new ArrayList<>();
    private TextView dnsButton;
    private boolean dnsRunning;

    private final List<PermissionRow> permissionRows = new ArrayList<>();
    private TextView permissionButton, locationButton;

    public ToolsScreen(MainActivity host) {
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
        titleCol.addView(Ui.section(host, "工具与诊断"));
        titleCol.addView(Ui.small(host, "一键体检、Ping、DNS、公网出口与权限"), topMargin(4));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView share = Ui.quietButton(host, "分享报告 ↗", Ui.MUTED);
        share.setOnClickListener(v -> host.shareReport());
        head.addView(share);
        content.addView(head, topMargin(8));

        buildHealthCard();
        buildPublicCard();
        buildPingCard();
        buildDnsCard();
        buildPermissionCard();
        buildAboutCard();
    }

    private void buildHealthCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "一键网络体检"));
        titleCol.addView(Ui.small(host, "外网 · DNS · 网关 · 信号 · 延迟"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout scoreCol = Ui.column(host);
        scoreCol.setGravity(Gravity.END);
        LinearLayout scoreRow = Ui.row(host);
        scoreRow.setGravity(Gravity.BOTTOM);
        healthScore = Ui.text(host, "—", 27f, Ui.TEXT, true);
        scoreRow.addView(healthScore);
        TextView total = Ui.text(host, "/100", 9f, Ui.DIM, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(host, 2);
        tlp.bottomMargin = Ui.dp(host, 4);
        scoreRow.addView(total, tlp);
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
        healthTime = Ui.small(host, "点击下方按钮，生成当前网络的 100 分制体检报告");
        card.addView(healthTime, topMargin(8));
        healthButton = Ui.primaryButton(host, "开始体检");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(host, 12);
        card.addView(healthButton, blp);
        healthButton.setOnClickListener(v -> runHealth());
        content.addView(card);
    }

    private void buildPublicCard() {
        publicInfoCard = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "公网出口"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = Ui.quietButton(host, "刷新", Ui.ACCENT);
        refresh.setOnClickListener(v -> {
            host.maybeFetchPublicInfo(true);
            Ui.toast(host, "正在刷新公网信息…");
        });
        head.addView(refresh);
        publicInfoCard.addView(head);
        publicIp = Ui.mono(host, "获取中…", 20f, Ui.TEXT);
        publicIp.setSingleLine(true);
        publicIp.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        publicIp.setClickable(true);
        publicIp.setOnClickListener(v -> copyIp());
        publicInfoCard.addView(publicIp, topMargin(12));
        publicOrg = Ui.text(host, "正在识别运营商 / ASN", 11f, Ui.MUTED, false);
        publicOrg.setMaxLines(2);
        publicInfoCard.addView(publicOrg, topMargin(5));
        publicLocation = Ui.small(host, "位置 — · 来源 —");
        publicInfoCard.addView(publicLocation, topMargin(3));
        publicSource = Ui.text(host, "点击 IP 可复制", 8.8f, Ui.DIM, false);
        publicInfoCard.addView(publicSource, topMargin(7));

        publicInfoCard.addView(Ui.divider(host));
        localIp = detail(publicInfoCard, "本机地址");
        localGateway = detail(publicInfoCard, "默认网关");
        localDns = detail(publicInfoCard, "DNS 服务器");
        content.addView(publicInfoCard);
    }

    private TextView detail(LinearLayout parent, String label) {
        LinearLayout row = Ui.row(host);
        row.addView(Ui.text(host, label, 10f, Ui.DIM, false), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.mono(host, "—", 10f, Ui.MUTED);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.8f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(host, 7);
        parent.addView(row, rlp);
        return value;
    }

    private void buildPingCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "Ping 链路探测"));
        titleCol.addView(Ui.small(host, "ICMP 优先，失败自动回退 TCP"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "4 TARGET", Ui.BLUE));
        card.addView(head);

        LinearLayout list = Ui.column(host);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(host, 8);
        card.addView(list, llp);
        pingRows.add(addPingRow(list, "局域网网关", "自动检测默认网关"));
        pingRows.add(addPingRow(list, "阿里公共 DNS", "223.5.5.5"));
        pingRows.add(addPingRow(list, "Cloudflare", "1.1.1.1"));
        pingRows.add(addPingRow(list, "测速边缘节点", "speed.cloudflare.com"));

        pingButton = Ui.outlineButton(host, "开始 Ping 测试");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(host, 12);
        card.addView(pingButton, blp);
        pingButton.setOnClickListener(v -> runPing());
        content.addView(card);
    }

    private PingRow addPingRow(LinearLayout parent, String label, String target) {
        PingRow row = new PingRow();
        row.root = Ui.row(host);
        row.root.setPadding(0, Ui.dp(host, 8), 0, Ui.dp(host, 8));
        LinearLayout col = Ui.column(host);
        row.title = Ui.text(host, label, 11f, Ui.TEXT, true);
        row.host = Ui.mono(host, target, 8.8f, Ui.DIM);
        col.addView(row.title);
        col.addView(row.host, topMargin(2));
        row.root.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        LinearLayout right = Ui.column(host);
        right.setGravity(Gravity.END);
        row.value = Ui.text(host, "待测试", 12f, Ui.MUTED, true);
        row.value.setGravity(Gravity.END);
        right.addView(row.value);
        row.detail = Ui.text(host, "—", 8.5f, Ui.DIM, false);
        row.detail.setGravity(Gravity.END);
        right.addView(row.detail, topMargin(2));
        row.root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        parent.addView(row.root);
        return row;
    }

    private void buildDnsCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "DNS 解析"));
        titleCol.addView(Ui.small(host, "测量域名解析耗时与返回地址"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "DNS", Ui.PURPLE));
        card.addView(head);
        LinearLayout list = Ui.column(host);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(host, 8);
        card.addView(list, llp);
        String[] domains = {"www.baidu.com", "www.qq.com", "www.taobao.com"};
        for (String d : domains) dnsRows.add(addDnsRow(list, d));
        dnsButton = Ui.outlineButton(host, "开始 DNS 测试");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(host, 12);
        card.addView(dnsButton, blp);
        dnsButton.setOnClickListener(v -> runDns());
        content.addView(card);
    }

    private DnsRow addDnsRow(LinearLayout parent, String domain) {
        DnsRow row = new DnsRow();
        row.root = Ui.row(host);
        row.root.setPadding(0, Ui.dp(host, 8), 0, Ui.dp(host, 8));
        row.domain = Ui.text(host, domain, 11f, Ui.TEXT, true);
        LinearLayout col = Ui.column(host);
        col.addView(row.domain);
        row.ip = Ui.mono(host, "尚未解析", 8.5f, Ui.DIM);
        col.addView(row.ip, topMargin(2));
        row.root.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.value = Ui.text(host, "—", 11.5f, Ui.MUTED, true);
        row.value.setGravity(Gravity.END);
        row.root.addView(row.value);
        parent.addView(row.root);
        return row;
    }

    private void buildPermissionCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "权限与传感器"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "ANDROID " + Build.VERSION.RELEASE, Ui.MUTED));
        card.addView(head);
        LinearLayout list = Ui.column(host);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(host, 8);
        card.addView(list, llp);
        permissionRows.add(addPermissionRow(list, "精确定位权限"));
        permissionRows.add(addPermissionRow(list, "电话 / SIM 权限"));
        permissionRows.add(addPermissionRow(list, "附近 Wi-Fi 权限"));
        permissionRows.add(addPermissionRow(list, "系统定位服务"));
        LinearLayout actions = Ui.row(host);
        permissionButton = Ui.outlineButton(host, "管理权限");
        permissionButton.setOnClickListener(v -> host.requestCorePermissions());
        actions.addView(permissionButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        locationButton = Ui.outlineButton(host, "系统定位设置");
        locationButton.setOnClickListener(v -> host.openLocationSettings());
        LinearLayout.LayoutParams llp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp2.leftMargin = Ui.dp(host, 8);
        actions.addView(locationButton, llp2);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(host, 12);
        card.addView(actions, alp);
        TextView note = Ui.small(host, "Android 要求定位权限才能返回 Wi-Fi SSID 与基站字段；这些权限不会用于广告或定位追踪。");
        note.setMaxLines(3);
        card.addView(note, topMargin(9));
        content.addView(card);
    }

    private PermissionRow addPermissionRow(LinearLayout parent, String label) {
        PermissionRow row = new PermissionRow();
        row.root = Ui.row(host);
        row.root.setPadding(0, Ui.dp(host, 8), 0, Ui.dp(host, 8));
        row.dot = new View(host);
        row.dot.setBackground(Ui.rounded(host, Ui.DIM, 99));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(host, 6), Ui.dp(host, 6));
        dlp.rightMargin = Ui.dp(host, 9);
        row.root.addView(row.dot, dlp);
        row.label = Ui.text(host, label, 10.5f, Ui.TEXT, false);
        row.root.addView(row.label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.state = Ui.text(host, "—", 9.5f, Ui.MUTED, true);
        row.root.addView(row.state);
        parent.addView(row.root);
        return row;
    }

    private void buildAboutCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "报告与隐私"));
        titleCol.addView(Ui.small(host, "本地生成 · 不自动上传"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "v1.0", Ui.MUTED));
        card.addView(head);
        TextView body = Ui.small(host, "报告包含当前 Wi-Fi、SIM、信号、体检结果和测速历史。只有你主动选择分享目标后，"
                + "文本才会离开设备。公网 IP 查询使用 ipinfo.io / Cloudflare；测速下载使用华为云、南大、中科大、上交镜像或 Cloudflare，上传使用 Cloudflare。");
        body.setMaxLines(5);
        card.addView(body, topMargin(10));
        TextView share = Ui.primaryButton(host, "分享完整诊断报告");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(host, 13);
        card.addView(share, slp);
        share.setOnClickListener(v -> host.shareReport());
        TextView meta = Ui.mono(host, "NETPULSE 1.0 · NATIVE ANDROID · NO ADS",
                8.3f, Ui.DIM);
        meta.setGravity(Gravity.CENTER);
        card.addView(meta, topMargin(14));
        content.addView(card);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (host.latestSnapshot() != null) update(host.latestSnapshot(), 0, 0);
        applyHostHealth();
    }

    @Override
    public void onHide() {}

    @Override
    public void update(NetProbe.Snapshot s, double downloadMbps, double uploadMbps) {
        if (s == null) return;
        updatePublicInfo(s);
        updatePermissions(s);
        applyHostHealth();
    }

    private void updatePublicInfo(NetProbe.Snapshot s) {
        PublicInfoService.PublicInfo p = host.publicInfo();
        if (p == null) {
            publicIp.setText("获取中…");
            publicOrg.setText("正在识别运营商 / ASN");
            publicLocation.setText("位置 — · 来源 —");
        } else if (p.ip == null || p.ip.isEmpty()) {
            publicIp.setText("获取失败");
            publicOrg.setText(p.error == null || p.error.isEmpty() ? "公网出口不可用" : p.error);
            publicLocation.setText("位置 — · 来源 " + nz(p.source));
            publicIp.setTextColor(Ui.RED);
        } else {
            publicIp.setText(p.ip);
            publicIp.setTextColor(Ui.TEXT);
            publicOrg.setText(p.orgText());
            String loc = p.locationText();
            if (p.colo != null && !p.colo.isEmpty()) loc += " · CF " + p.colo;
            publicLocation.setText("位置 " + loc + " · 来源 " + nz(p.source));
        }
        if (localIp != null) {
            String ip = s.ipv4 != null && !s.ipv4.isEmpty() ? s.ipv4 : s.ipv6;
            localIp.setText(nz(ip));
            localGateway.setText(nz(s.gateway));
            localDns.setText(nz(s.dns));
        }
    }

    private void updatePermissions(NetProbe.Snapshot s) {
        boolean fine = NetProbe.hasFineLocation(host);
        boolean phone = NetProbe.hasPhonePermission(host);
        boolean nearby = NetProbe.hasNearbyWifiPermission(host);
        setPermission(permissionRows.get(0), fine, fine ? "已授予" : "未授予");
        setPermission(permissionRows.get(1), phone, phone ? "已授予" : "未授予");
        setPermission(permissionRows.get(2), nearby,
                Build.VERSION.SDK_INT >= 33 ? (nearby ? "已授予" : "未授予") : "系统无需");
        setPermission(permissionRows.get(3), s.locationEnabled, s.locationEnabled ? "已开启" : "已关闭");
    }

    private void setPermission(PermissionRow row, boolean ok, String text) {
        int color = ok ? Ui.ACCENT : Ui.AMBER;
        row.dot.setBackground(Ui.rounded(host, color, 99));
        row.state.setText(text);
        row.state.setTextColor(color);
    }

    private void applyHostHealth() {
        Diagnostics.Report report = host.healthReport();
        if (report != null && report.timestamp != shownHealthTs && !healthChecking) {
            renderHealth(report);
        }
    }

    private void runHealth() {
        if (healthChecking) return;
        healthChecking = true;
        healthButton.setText("检测中…");
        healthButton.setAlpha(0.55f);
        healthButton.setEnabled(false);
        healthTime.setText("正在测试外网、DNS、网关、信号与抖动，通常需要 10–25 秒…");
        host.requestHealth(true, report -> {
            healthChecking = false;
            healthButton.setText("重新体检");
            healthButton.setAlpha(1f);
            healthButton.setEnabled(true);
            renderHealth(report);
        });
    }

    private void renderHealth(Diagnostics.Report report) {
        if (report == null) return;
        shownHealthTs = report.timestamp;
        healthScore.setText(String.valueOf(report.score));
        int color = report.score >= 75 ? Ui.ACCENT : report.score >= 55 ? Ui.AMBER : Ui.RED;
        healthScore.setTextColor(color);
        healthGrade.setText(report.grade);
        healthGrade.setTextColor(color);
        healthChecks.removeAllViews();
        for (Diagnostics.Check check : report.checks) {
            LinearLayout row = Ui.row(host);
            View dot = new View(host);
            int c = check.status == Diagnostics.PASS ? Ui.ACCENT
                    : check.status == Diagnostics.WARN ? Ui.AMBER : Ui.RED;
            dot.setBackground(Ui.rounded(host, c, 99));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(host, 6), Ui.dp(host, 6));
            dlp.rightMargin = Ui.dp(host, 9);
            row.addView(dot, dlp);
            LinearLayout col = Ui.column(host);
            col.addView(Ui.text(host, check.name, 10.8f, Ui.TEXT, true));
            TextView d = Ui.text(host, check.detail, 9f, Ui.DIM, false);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(d, topMargin(2));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.mono(host, check.earned + "/" + check.weight, 8.8f, c));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(host, 9);
            healthChecks.addView(row, rlp);
        }
        if (!report.suggestions.isEmpty()) {
            TextView tip = Ui.small(host, "建议：" + report.suggestions.get(0));
            tip.setMaxLines(3);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(host, 10);
            healthChecks.addView(tip, tlp);
        }
        healthTime.setText("最近体检 "
                + new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(report.timestamp))
                + " · " + report.summary);
        healthButton.setText("重新体检");
        healthButton.setAlpha(1f);
        healthButton.setEnabled(true);
    }

    private void runPing() {
        if (pinging) return;
        pinging = true;
        pingButton.setEnabled(false);
        pingButton.setAlpha(0.55f);
        pingButton.setText("Ping 测试中…");
        for (PingRow row : pingRows) {
            row.value.setText("…");
            row.value.setTextColor(Ui.AMBER);
            row.detail.setText("测试中");
        }
        final NetProbe.Snapshot snapshot = host.latestSnapshot();
        final String gateway = snapshot == null ? null
                : (snapshot.gateway != null && !snapshot.gateway.isEmpty() ? snapshot.gateway
                : snapshot.wifi != null ? snapshot.wifi.gateway : null);
        host.runIo(() -> {
            List<Object[]> results = new ArrayList<>();
            String[] hosts = {gateway, "223.5.5.5", "1.1.1.1", "speed.cloudflare.com"};
            for (String target : hosts) {
                Diagnostics.PingResult result;
                if (target == null || TextUtils.isEmpty(target)) {
                    result = new Diagnostics.PingResult();
                    result.error = "没有默认网关";
                } else {
                    result = Diagnostics.ping(target, 4, 1200);
                }
                results.add(new Object[]{target, result});
                // Stream each row as soon as it is ready.
                final int index = results.size() - 1;
                final Diagnostics.PingResult r = result;
                main.post(() -> applyPingResult(index, r, target));
            }
            main.post(() -> {
                pinging = false;
                pingButton.setEnabled(true);
                pingButton.setAlpha(1f);
                pingButton.setText("重新 Ping 测试");
            });
        });
    }

    private void applyPingResult(int index, Diagnostics.PingResult r, String target) {
        if (index < 0 || index >= pingRows.size()) return;
        PingRow row = pingRows.get(index);
        row.host.setText(target == null ? "未取得网关" : target);
        if (r.ok) {
            row.value.setText(String.format(Locale.US, "%.0f ms", r.avgMs));
            row.value.setTextColor(Ui.MUTED);
            row.detail.setText(String.format(Locale.US, "%s · 丢包 %.0f%% · 抖动 %.0f ms",
                    r.method, r.lossPercent(), r.jitterMs));
            int quality = r.avgMs < 30 ? Ui.ACCENT : r.avgMs < 100 ? Ui.AMBER : Ui.RED;
            row.value.setTextColor(quality);
        } else {
            row.value.setText("不可达");
            row.value.setTextColor(Ui.RED);
            row.detail.setText(r.error == null ? "测试失败" : r.error);
        }
    }

    private void runDns() {
        if (dnsRunning) return;
        dnsRunning = true;
        dnsButton.setEnabled(false);
        dnsButton.setAlpha(0.55f);
        dnsButton.setText("DNS 解析中…");
        for (DnsRow row : dnsRows) {
            row.value.setText("…");
            row.value.setTextColor(Ui.AMBER);
            row.ip.setText("解析中");
        }
        host.runIo(() -> {
            int index = 0;
            for (DnsRow row : new ArrayList<>(dnsRows)) {
                String domain = row.domain.getText().toString();
                long start = System.nanoTime();
                String ip = null;
                long elapsed = -1;
                try {
                    InetAddress[] addrs = InetAddress.getAllByName(domain);
                    elapsed = (System.nanoTime() - start) / 1_000_000L;
                    if (addrs != null && addrs.length > 0) ip = addrs[0].getHostAddress();
                } catch (Exception ignored) {}
                final int idx = index++;
                final String ipFinal = ip;
                final long elapsedFinal = elapsed;
                main.post(() -> {
                    DnsRow r = dnsRows.get(idx);
                    if (ipFinal == null) {
                        r.value.setText("失败");
                        r.value.setTextColor(Ui.RED);
                        r.ip.setText("无法解析");
                    } else {
                        r.value.setText(elapsedFinal + " ms");
                        r.value.setTextColor(elapsedFinal < 120 ? Ui.ACCENT
                                : elapsedFinal < 400 ? Ui.AMBER : Ui.RED);
                        r.ip.setText(ipFinal);
                    }
                });
            }
            main.post(() -> {
                dnsRunning = false;
                dnsButton.setEnabled(true);
                dnsButton.setAlpha(1f);
                dnsButton.setText("重新 DNS 测试");
            });
        });
    }

    private void copyIp() {
        PublicInfoService.PublicInfo p = host.publicInfo();
        if (p == null || p.ip == null || p.ip.isEmpty()) {
            Ui.toast(host, "暂无可复制的公网 IP");
            return;
        }
        ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("公网 IP", p.ip));
            Ui.toast(host, "已复制公网 IP：" + p.ip);
        }
    }

    private String nz(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(host, dp);
        return lp;
    }

    private static class PingRow {
        LinearLayout root;
        TextView title, host, value, detail;
    }

    private static class DnsRow {
        LinearLayout root;
        TextView domain, ip, value;
    }

    private static class PermissionRow {
        LinearLayout root;
        View dot;
        TextView label, state;
    }
}
