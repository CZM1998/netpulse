package com.netpulse.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 7140;
    private static final String TAG = "NetPulse";
    private static final long PUBLIC_INFO_TTL_MS = 60_000L;
    private static final long HEALTH_CACHE_MS = 5 * 60_000L;

    private NetProbe probe;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private ScheduledExecutorService poller;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Screen[] screens = new Screen[5];
    private int currentTab = 0;
    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<IconView> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private FrameLayout contentHost;

    private LinearLayout statusPill;
    private View statusDot;
    private TextView statusText;
    private TextView updatedText;

    private NetProbe.Snapshot latest;
    private double downRate;
    private double upRate;
    private long lastRx = -1;
    private long lastTx = -1;
    private long lastSampleAt;

    private PublicInfoService.PublicInfo publicInfo;
    private long publicInfoAt;
    private final AtomicBoolean publicFetching = new AtomicBoolean(false);

    private Diagnostics.Report healthReport;
    private long healthReportAt;
    private boolean healthRunning;
    private final List<HealthCallback> healthCallbacks = new ArrayList<>();

    public interface HealthCallback {
        void onHealth(Diagnostics.Report report);
    }

    public interface Screen {
        View getView();
        void onShow();
        void onHide();
        void update(NetProbe.Snapshot snapshot, double downloadMbps, double uploadMbps);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        probe = new NetProbe(this);
        configureWindow();
        buildUi();
        maybeOnboard();
        startPolling();
        maybeFetchPublicInfo(true);
    }

    private void configureWindow() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.NAV);
        w.getDecorView().setSystemUiVisibility(0);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF07121D, 0xFF08131F, 0xFF050C14}));
        root.setFitsSystemWindows(true);

        root.addView(buildHeader());
        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        screens[0] = new OverviewScreen(this);
        screens[1] = new WifiScreen(this);
        screens[2] = new CellularScreen(this);
        screens[3] = new SpeedScreen(this);
        screens[4] = new ToolsScreen(this);
        for (Screen screen : screens) {
            View v = screen.getView();
            v.setVisibility(View.GONE);
            contentHost.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.addView(buildBottomNav());
        setContentView(root);
        switchTab(0);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));

        LogoView logo = new LogoView(this);
        logo.setContentDescription("网脉 Logo");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        logoLp.rightMargin = Ui.dp(this, 10);
        header.addView(logo, logoLp);

        LinearLayout titleCol = Ui.column(this);
        TextView title = Ui.text(this, "网脉", 21f, Ui.TEXT, true);
        TextView sub = Ui.text(this, "NETPULSE · 信号与网速实验室", 8.3f, Ui.DIM, true);
        sub.setLetterSpacing(0.09f);
        titleCol.addView(title);
        titleCol.addView(sub, topMargin(3));
        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusPill = Ui.row(this);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(Ui.dp(this, 9), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        statusDot = new View(this);
        GradientDrawable dotBg = Ui.rounded(this, Ui.ACCENT, 99);
        statusDot.setBackground(dotBg);
        statusPill.addView(statusDot, new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6)));
        statusText = Ui.text(this, "启动中", 10f, Ui.ACCENT, true);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.leftMargin = Ui.dp(this, 6);
        statusPill.addView(statusText, stlp);
        applyStatusPill(0, "启动中");
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.rightMargin = Ui.dp(this, 9);
        header.addView(statusPill, pillLp);

        FrameLayout settings = Ui.iconButton(this, "settings", Ui.MUTED);
        settings.setContentDescription("设置");
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        return header;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, dp);
        return lp;
    }

    private View buildBottomNav() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(0x1CFFFFFF);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 7));
        bar.setBackgroundColor(Ui.NAV);
        bar.setElevation(Ui.dp(this, 10));

        String[] names = {"概览", "Wi-Fi", "蜂窝", "测速", "工具"};
        String[] icons = {"overview", "wifi", "signal", "speed", "tools"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 6));
            item.setClickable(true);
            item.setFocusable(true);
            IconView icon = new IconView(this);
            icon.setIcon(icons[i]);
            icon.setColor(Ui.MUTED);
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 21), Ui.dp(this, 21)));
            TextView label = Ui.text(this, names[i], 9.5f, Ui.MUTED, true);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = Ui.dp(this, 3);
            item.addView(label, llp);
            item.setOnClickListener(v -> switchTab(index));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            ilp.leftMargin = Ui.dp(this, 3);
            ilp.rightMargin = Ui.dp(this, 3);
            bar.addView(item, ilp);
            navItems.add(item);
            navIcons.add(icon);
            navLabels.add(label);
        }
        wrap.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));
        return wrap;
    }

    public void switchTab(int index) {
        if (index < 0 || index >= screens.length) return;
        for (int i = 0; i < screens.length; i++) {
            View v = screens[i].getView();
            v.setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        if (currentTab != index) screens[currentTab].onHide();
        currentTab = index;
        for (int i = 0; i < navItems.size(); i++) {
            boolean selected = i == index;
            navIcons.get(i).setColor(selected ? Ui.ACCENT : Ui.MUTED);
            navLabels.get(i).setTextColor(selected ? Ui.ACCENT : Ui.MUTED);
            GradientDrawable bg = selected
                    ? Ui.rounded(this, 0x1438F2C0, 13)
                    : Ui.rounded(this, Color.TRANSPARENT, 13);
            if (selected) bg.setStroke(Ui.dp(this, 1), 0x2638F2C0);
            navItems.get(i).setBackground(bg);
        }
        screens[index].onShow();
        if (latest != null) screens[index].update(latest, downRate, upRate);
    }

    private void startPolling() {
        if (poller != null && !poller.isShutdown()) return;
        lastRx = -1;
        lastTx = -1;
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(() -> {
            NetProbe.Snapshot snapshot;
            try {
                snapshot = probe.snapshot();
            } catch (Throwable e) {
                Log.e(TAG, "poll: snapshot failed", e);
                return;
            }
            long now = android.os.SystemClock.elapsedRealtime();
            final double[] rates = new double[2];
            synchronized (MainActivity.this) {
                if (lastRx >= 0 && now > lastSampleAt) {
                    long drx = Math.max(0, snapshot.totalRx - lastRx);
                    long dtx = Math.max(0, snapshot.totalTx - lastTx);
                    double seconds = (now - lastSampleAt) / 1000.0;
                    rates[0] = drx * 8.0 / seconds / 1_000_000.0;
                    rates[1] = dtx * 8.0 / seconds / 1_000_000.0;
                }
                lastRx = snapshot.totalRx;
                lastTx = snapshot.totalTx;
                lastSampleAt = now;
            }
            main.post(() -> {
                try {
                    applySnapshot(snapshot, rates[0], rates[1]);
                } catch (Throwable e) {
                    Log.e(TAG, "poll: UI apply failed", e);
                }
            });
        }, 0, 1200, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    private void applySnapshot(NetProbe.Snapshot snapshot, double down, double up) {
        latest = snapshot;
        downRate = down;
        upRate = up;
        updateStatus(snapshot);
        for (Screen screen : screens) {
            if (screen != null) screen.update(snapshot, down, up);
        }
    }

    private void updateStatus(NetProbe.Snapshot s) {
        if (s == null) return;
        if (s.online) {
            applyStatusPill(0, s.transport.equals("Wi-Fi") ? "在线" : s.transport + "在线");
        } else if (!"离线".equals(s.transport)) {
            applyStatusPill(1, "受限");
        } else {
            applyStatusPill(2, "离线");
        }
        if (updatedText != null) {
            long now = System.currentTimeMillis();
            updatedText.setText(new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(now)) + " 更新");
        }
    }

    private void applyStatusPill(int state, String text) {
        int color = state == 0 ? Ui.ACCENT : state == 1 ? Ui.AMBER : Ui.RED;
        statusText.setText(text);
        statusText.setTextColor(color);
        GradientDrawable bg = Ui.rounded(this, Ui.withAlpha(color, 0.10f), 99);
        bg.setStroke(Ui.dp(this, 1), Ui.withAlpha(color, 0.30f));
        statusPill.setBackground(bg);
        statusDot.setBackground(Ui.rounded(this, color, 99));
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPolling();
        maybeFetchPublicInfo(false);
    }

    @Override
    protected void onStop() {
        stopPolling();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        if (screens[3] instanceof SpeedScreen) {
            ((SpeedScreen) screens[3]).stopForDestroy();
        }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentTab != 0) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }

    public void setKeepScreenOnForTest(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void runIo(Runnable task) {
        if (!io.isShutdown()) io.execute(task);
    }

    public NetProbe probe() { return probe; }
    public NetProbe.Snapshot latestSnapshot() { return latest; }
    public PublicInfoService.PublicInfo publicInfo() { return publicInfo; }
    public Diagnostics.Report healthReport() { return healthReport; }
    public long healthReportAge() { return System.currentTimeMillis() - healthReportAt; }

    public void maybeFetchPublicInfo(boolean force) {
        if (!force && publicInfo != null && System.currentTimeMillis() - publicInfoAt < PUBLIC_INFO_TTL_MS) return;
        if (!publicFetching.compareAndSet(false, true)) return;
        io.execute(() -> {
            PublicInfoService.PublicInfo info = PublicInfoService.fetch();
            main.post(() -> {
                publicInfo = info;
                publicInfoAt = System.currentTimeMillis();
                publicFetching.set(false);
                if (latest != null) {
                    for (Screen screen : screens) screen.update(latest, downRate, upRate);
                }
            });
        });
    }

    public void requestHealth(boolean force, HealthCallback callback) {
        if (!force && healthReport != null
                && System.currentTimeMillis() - healthReportAt < HEALTH_CACHE_MS) {
            callback.onHealth(healthReport);
            return;
        }
        if (healthRunning) {
            healthCallbacks.add(callback);
            return;
        }
        healthRunning = true;
        healthCallbacks.add(callback);
        io.execute(() -> {
            Diagnostics.Report report;
            try {
                NetProbe.Snapshot snapshot = latest;
                if (snapshot == null) snapshot = probe.snapshot();
                report = Diagnostics.run(MainActivity.this, snapshot, publicInfo);
            } catch (Exception e) {
                report = new Diagnostics.Report();
                report.score = 0;
                report.grade = "失败";
                report.summary = "体检过程出错：" + e.getClass().getSimpleName();
                report.checks.add(new Diagnostics.Check("体检执行", report.summary,
                        Diagnostics.FAIL, 100, 0));
            }
            final Diagnostics.Report finalReport = report;
            main.post(() -> {
                healthReport = finalReport;
                healthReportAt = System.currentTimeMillis();
                healthRunning = false;
                List<HealthCallback> callbacks = new ArrayList<>(healthCallbacks);
                healthCallbacks.clear();
                for (HealthCallback cb : callbacks) cb.onHealth(finalReport);
            });
        });
    }

    public void requestCorePermissions() {
        List<String> missing = new ArrayList<>();
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!has(Manifest.permission.READ_PHONE_STATE)) missing.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 33 && !has("android.permission.NEARBY_WIFI_DEVICES")) {
            missing.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        if (missing.isEmpty()) {
            if (!probe.isLocationEnabled()) {
                Ui.confirm(this, "还需要定位服务", "Android 只有在系统定位开启时才会返回 Wi-Fi 名称、附近热点和基站信息。定位权限本身不会用于追踪你的位置。",
                        "去开启", this::openLocationSettings);
            } else {
                Ui.info(this, "权限已完整", "Wi-Fi、SIM 卡和基站信息所需权限都已授予。");
            }
            return;
        }
        requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private boolean has(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            int granted = 0;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted++;
            if (granted > 0) {
                Ui.toast(this, "已授予 " + granted + " 项权限，正在刷新数据");
                if (latest != null) {
                    for (Screen screen : screens) screen.update(latest, downRate, upRate);
                }
            } else {
                Ui.toast(this, "未授予权限，部分信息将不可见");
            }
        }
    }

    public void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "无法打开系统定位设置");
        }
    }

    public void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "无法打开 Wi-Fi 设置");
        }
    }

    public void openMobileNetworkSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Ui.toast(this, "无法打开系统设置");
            }
        }
    }

    public void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "无法打开应用设置");
        }
    }

    private void showSettings() {
        String[] options = {
                "权限与定位设置",
                "系统 Wi-Fi 设置",
                "系统移动网络设置",
                "刷新公网 IP 与出口信息",
                "分享完整诊断报告",
                "关于网脉"
        };
        Ui.choices(this, "设置与工具", options, -1, index -> {
            switch (index) {
                case 0: requestCorePermissions(); break;
                case 1: openWifiSettings(); break;
                case 2: openMobileNetworkSettings(); break;
                case 3:
                    publicInfoAt = 0;
                    maybeFetchPublicInfo(true);
                    Ui.toast(this, "正在刷新公网信息…");
                    break;
                case 4: shareReport(); break;
                case 5:
                    Ui.sheet(this, "关于网脉 / NetPulse",
                            "版本 1.0 · 本地原生网络实验室\n\n"
                                    + "数据来源：Android 公开网络与电话 API、Cloudflare 开放测速节点。\n\n"
                                    + "隐私：App 不上传你的 Wi-Fi 密码、号码或位置。测速和公网 IP 查询会产生正常的网络请求；所有历史仅保存在本机。",
                            null, null).show();
                    break;
                default: break;
            }
        });
    }

    private void maybeOnboard() {
        SharedPreferences prefs = getSharedPreferences("netpulse", MODE_PRIVATE);
        if (prefs.getBoolean("onboarded", false)) return;
        prefs.edit().putBoolean("onboarded", true).apply();
        main.postDelayed(() -> Ui.confirm(this, "欢迎使用网脉",
                "为了显示 Wi-Fi 名称、附近热点、信道和双卡基站信号，Android 需要定位与电话权限。\n\n"
                        + "定位仅用于满足系统的 Wi-Fi/基站信息读取限制；网脉不会追踪或上传你的位置。",
                "开始授权", this::requestCorePermissions), 500);
    }

    public void shareReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("网脉 NetPulse · 网络诊断报告\n");
        sb.append("时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date())).append("\n");
        sb.append("设备：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE).append("\n\n");
        NetProbe.Snapshot s = latest;
        if (s == null) {
            sb.append("尚未采集到网络快照。\n");
        } else {
            sb.append("【连接】\n");
            sb.append("传输：").append(s.transport)
                    .append(s.online ? " · 互联网已验证" : " · 互联网未验证")
                    .append(s.vpn ? " · VPN" : "").append("\n");
            sb.append("接口：").append(nz(s.interfaceName)).append("  MTU ").append(s.mtu).append("\n");
            sb.append("IPv4：").append(nz(s.ipv4)).append("\n");
            if (s.ipv6 != null && !s.ipv6.isEmpty()) sb.append("IPv6：").append(s.ipv6).append("\n");
            sb.append("网关：").append(nz(s.gateway)).append("  DNS：").append(nz(s.dns)).append("\n");
            sb.append("流量：本次开机后接收 ").append(NetProbe.formatBytes(s.totalRx))
                    .append(" / 发送 ").append(NetProbe.formatBytes(s.totalTx)).append("\n\n");

            sb.append("【Wi-Fi】\n");
            NetProbe.WifiData w = s.wifi;
            if (w.connected) {
                sb.append(w.ssid).append(" · ").append(nz(w.band)).append(" · 信道 ").append(w.channel)
                        .append(" · ").append(nz(w.standard)).append("\n");
                sb.append("信号：").append(w.rssi).append(" dBm（").append(Ui.wifiQuality(w.rssi)).append("）")
                        .append(" · 协商速率 ").append(w.linkSpeedMbps).append(" Mbps · 带宽 ")
                        .append(w.widthMhz).append(" MHz\n");
                sb.append("BSSID：").append(nz(w.bssid)).append(" · 安全：").append(nz(w.security)).append("\n");
                sb.append("IP：").append(nz(w.ipv4)).append(" · 网关：").append(nz(w.gateway)).append("\n");
            } else {
                sb.append(w.enabled ? "未连接 Wi-Fi" : "Wi-Fi 已关闭").append("\n");
            }
            sb.append("\n");

            sb.append("【移动网络】\n");
            sb.append("SIM 状态：").append(s.simState).append("\n");
            if (s.sims.isEmpty()) {
                sb.append(nz(s.simError)).append("\n");
            } else {
                for (NetProbe.CellSim sim : s.sims) {
                    sb.append("SIM ").append(sim.slot).append(" · ").append(sim.carrier)
                            .append(" · ").append(sim.networkType).append(" · ").append(sim.voiceService).append("\n");
                    if (sim.signalDbm != Integer.MIN_VALUE) {
                        sb.append("  信号 ").append(sim.signalDbm).append(" dBm");
                    }
                    for (int i = 0; i < Math.min(4, sim.metrics.size()); i++) {
                        NetProbe.SignalMetric m = sim.metrics.get(i);
                        sb.append(" · ").append(m.name).append(" ").append(m.text());
                    }
                    sb.append("\n");
                    if (!sim.cells.isEmpty()) {
                        NetProbe.CellItem cell = sim.cells.get(0);
                        sb.append("  主小区 ").append(cell.tech).append(" · PCI ").append(cell.pci)
                                .append(" · ").append(cell.band).append(" · ").append(cell.dbm).append(" dBm\n");
                    }
                }
            }
            sb.append("\n");
        }
        if (publicInfo != null) {
            sb.append("【公网出口】\n");
            sb.append("IP：").append(nz(publicInfo.ip)).append(" · ").append(nz(publicInfo.orgText()))
                    .append(" · ").append(nz(publicInfo.locationText())).append("\n\n");
        }
        if (healthReport != null) {
            sb.append("【一键体检】\n");
            sb.append("评分：").append(healthReport.score).append("/100 · ").append(healthReport.grade).append("\n");
            for (Diagnostics.Check c : healthReport.checks) {
                sb.append(statusWord(c.status)).append(" ").append(c.name).append("：")
                        .append(c.detail).append("\n");
            }
            sb.append("\n");
        }
        if (screens[3] instanceof SpeedScreen) {
            String history = ((SpeedScreen) screens[3]).exportHistoryText();
            if (history != null && !history.isEmpty()) {
                sb.append("【测速记录】\n").append(history).append("\n");
            }
        }
        sb.append("\n由网脉 NetPulse 生成 · 公开 API 数据仅供参考\n");
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "网脉网络诊断报告");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        try {
            startActivity(Intent.createChooser(send, "分享网络诊断报告"));
        } catch (ActivityNotFoundException e) {
            Ui.info(this, "分享不可用", "设备上没有可用的分享应用。");
        }
    }

    private String statusWord(int status) {
        return status == Diagnostics.PASS ? "✓" : status == Diagnostics.WARN ? "!" : "×";
    }

    private String nz(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }
}
