package com.netpulse.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpeedScreen extends ScrollView implements MainActivity.Screen {
    private final MainActivity host;
    private final SharedPreferences prefs;
    private final LinearLayout content;
    private final SpeedTestEngine engine = new SpeedTestEngine();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Float> samples = new ArrayList<>();

    private TextView modeQuick, modeFull, serverChip, providerNameView, providerSubView;
    private GaugeView gauge;
    private TextView statDown, statUp, statPing, statJitter;
    private TextView phaseHint, startButton, cancelButton;
    private TrendChartView liveChart;
    private TextView warningText;

    private LinearLayout resultCard;
    private TextView resultChip, resultDown, resultUp, resultPing, resultJitter, resultLoss, resultData, resultTime, resultRoute, resultNote, resultShare;
    private LinearLayout historyList;
    private TextView historyEmpty, clearHistory;

    private boolean quickMode = true;
    private boolean testing;
    private boolean warned;
    private boolean uploadPhase;
    private HistoryEntry selectedHistory;
    private String providerId = "auto";
    private long lastUiPhase;

    public SpeedScreen(MainActivity host) {
        super(host);
        this.host = host;
        this.prefs = host.getSharedPreferences("netpulse", android.content.Context.MODE_PRIVATE);
        quickMode = prefs.getBoolean("speed_quick", true);
        warned = prefs.getBoolean("speed_warned", false);
        providerId = prefs.getString("speed_provider", "auto");
        if (SpeedTestEngine.findProvider(providerId) == null) providerId = "auto";
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setPadding(0, Ui.dp(host, 2), 0, Ui.dp(host, 16));
        content = Ui.column(host);
        content.setPadding(Ui.dp(host, 16), 0, Ui.dp(host, 16), Ui.dp(host, 26));
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loadHistory();
        build();
        renderHistory();
    }

    private void build() {
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.section(host, "网络测速"));
        titleCol.addView(Ui.small(host, "多 CDN 节点 · 延迟 / 下载 / 上传"), topMargin(4));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serverChip = Ui.chip(host, "MULTI CDN", Ui.BLUE);
        head.addView(serverChip);
        content.addView(head, topMargin(8));

        buildMainCard();
        buildResultCard();
        buildHistoryCard();
        buildInfoCard();
    }

    private void buildMainCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout top = Ui.row(host);
        top.addView(Ui.chip(host, "TCP · 4 THREAD", Ui.PURPLE));
        View spacer = new View(host);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout toggle = Ui.row(host);
        GradientDrawable tbg = Ui.rounded(host, 0x0FFFFFFF, 9);
        tbg.setStroke(Ui.dp(host, 1), Ui.BORDER);
        toggle.setBackground(tbg);
        toggle.setPadding(Ui.dp(host, 3), Ui.dp(host, 3), Ui.dp(host, 3), Ui.dp(host, 3));
        modeQuick = Ui.text(host, "快速", 9f, Ui.TEXT, true);
        modeQuick.setGravity(Gravity.CENTER);
        modeQuick.setPadding(Ui.dp(host, 10), Ui.dp(host, 5), Ui.dp(host, 10), Ui.dp(host, 5));
        modeQuick.setClickable(true);
        modeFull = Ui.text(host, "完整", 9f, Ui.MUTED, true);
        modeFull.setGravity(Gravity.CENTER);
        modeFull.setPadding(Ui.dp(host, 10), Ui.dp(host, 5), Ui.dp(host, 10), Ui.dp(host, 5));
        modeFull.setClickable(true);
        toggle.addView(modeQuick);
        toggle.addView(modeFull);
        top.addView(toggle);
        card.addView(top);
        modeQuick.setOnClickListener(v -> setMode(true));
        modeFull.setOnClickListener(v -> setMode(false));
        styleToggle();
        buildProviderRow(card);

        gauge = new GaugeView(host);
        gauge.reset(120);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 230));
        glp.topMargin = Ui.dp(host, 2);
        card.addView(gauge, glp);

        LinearLayout stats = Ui.row(host);
        LinearLayout c1 = speedStat(stats, "下载", "Mbps", Ui.ACCENT);
        statDown = (TextView) c1.getChildAt(1);
        LinearLayout c2 = speedStat(stats, "上传", "Mbps", Ui.BLUE);
        statUp = (TextView) c2.getChildAt(1);
        LinearLayout c3 = speedStat(stats, "延迟", "TCP ms", Ui.PURPLE);
        statPing = (TextView) c3.getChildAt(1);
        LinearLayout c4 = speedStat(stats, "抖动", "ms", Ui.AMBER);
        statJitter = (TextView) c4.getChildAt(1);
        card.addView(stats, topMargin(2));

        liveChart = new TrendChartView(host);
        liveChart.setFixedRange(0, 100);
        liveChart.setColor(Ui.ACCENT);
        liveChart.setEmptyText("测速时显示实时吞吐曲线");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 72));
        clp.topMargin = Ui.dp(host, 8);
        card.addView(liveChart, clp);
        phaseHint = Ui.small(host, "准备就绪 · 推荐使用 Wi-Fi 进行测试");
        card.addView(phaseHint, topMargin(7));

        startButton = Ui.primaryButton(host, "开始测速");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(host, 12);
        card.addView(startButton, slp);
        startButton.setOnClickListener(v -> prepareStart());
        cancelButton = Ui.outlineButton(host, "取消测速");
        cancelButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp2.topMargin = Ui.dp(host, 8);
        card.addView(cancelButton, clp2);
        cancelButton.setOnClickListener(v -> {
            if (!testing) return;
            cancelButton.setText("正在取消…");
            cancelButton.setEnabled(false);
            engine.cancel();
        });

        warningText = Ui.text(host, "", 9f, Ui.DIM, false);
        warningText.setMaxLines(3);
        card.addView(warningText, topMargin(9));
        updateWarning();
        content.addView(card);
    }

    private void buildProviderRow(LinearLayout card) {
        LinearLayout row = Ui.row(host);
        row.setPadding(Ui.dp(host, 12), Ui.dp(host, 10), Ui.dp(host, 12), Ui.dp(host, 10));
        GradientDrawable bg = Ui.rounded(host, 0x0BFFFFFF, 12);
        bg.setStroke(Ui.dp(host, 1), 0x1CFFFFFF);
        row.setBackground(bg);
        row.setClickable(true);
        IconView globe = new IconView(host);
        globe.setIcon("globe");
        globe.setColor(Ui.BLUE);
        row.addView(globe, new LinearLayout.LayoutParams(Ui.dp(host, 17), Ui.dp(host, 17)));
        LinearLayout col = Ui.column(host);
        providerNameView = Ui.text(host, "自动选择（推荐）", 11.5f, Ui.TEXT, true);
        providerSubView = Ui.text(host, "自动优选 CDN · 上传 Cloudflare", 8.5f, Ui.DIM, false);
        col.addView(providerNameView);
        col.addView(providerSubView, topMargin(2));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = Ui.dp(host, 9);
        row.addView(col, clp);
        TextView change = Ui.quietButton(host, "更换", Ui.ACCENT);
        change.setOnClickListener(v -> chooseProvider());
        row.addView(change);
        row.setOnClickListener(v -> chooseProvider());
        updateProviderLabel();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(host, 9);
        card.addView(row, rlp);
    }

    private void updateProviderLabel() {
        SpeedTestEngine.Provider p = SpeedTestEngine.findProvider(providerId);
        if (providerNameView == null || p == null) return;
        providerNameView.setText(p.name);
        if (p.auto) {
            providerSubView.setText("自动优选 CDN · 上传 Cloudflare");
        } else if (p.cloudflare) {
            providerSubView.setText("下载 / 上传 · Cloudflare 全球");
        } else {
            providerSubView.setText("下载 " + p.shortName + " · 上传 Cloudflare");
        }
    }

    private void chooseProvider() {
        if (testing) return;
        SpeedTestEngine.Provider[] ps = SpeedTestEngine.PROVIDERS;
        String[] names = new String[ps.length];
        int selected = 0;
        for (int i = 0; i < ps.length; i++) {
            names[i] = ps[i].name;
            if (ps[i].id.equals(providerId)) selected = i;
        }
        final int current = selected;
        Ui.choices(host, "选择测速节点", names, current, index -> {
            providerId = ps[index].id;
            prefs.edit().putString("speed_provider", providerId).apply();
            updateProviderLabel();
            Ui.toast(host, "测速节点：" + ps[index].name);
        });
    }

    private LinearLayout speedStat(LinearLayout parent, String label, String unit, int color) {
        LinearLayout l = Ui.column(host);
        parent.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView labelView = Ui.text(host, label, 9f, Ui.DIM, false);
        l.addView(labelView);
        TextView value = Ui.text(host, "—", 17f, Ui.MUTED, true);
        l.addView(value, topMargin(4));
        TextView unitView = Ui.text(host, unit, 8f, Ui.DIM, false);
        l.addView(unitView, topMargin(2));
        return l;
    }

    private void buildResultCard() {
        resultCard = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "测速结果"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resultChip = Ui.chip(host, "—", Ui.MUTED);
        head.addView(resultChip);
        resultCard.addView(head);

        LinearLayout big = Ui.row(host);
        LinearLayout downCol = Ui.column(host);
        downCol.addView(Ui.text(host, "↓ 下载", 10f, Ui.ACCENT, true));
        resultDown = Ui.text(host, "—", 30f, Ui.TEXT, true);
        downCol.addView(resultDown, topMargin(4));
        downCol.addView(Ui.text(host, "Mbps", 9f, Ui.DIM, false));
        big.addView(downCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout upCol = Ui.column(host);
        upCol.addView(Ui.text(host, "↑ 上传", 10f, Ui.BLUE, true));
        resultUp = Ui.text(host, "—", 30f, Ui.TEXT, true);
        upCol.addView(resultUp, topMargin(4));
        upCol.addView(Ui.text(host, "Mbps", 9f, Ui.DIM, false));
        big.addView(upCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resultCard.addView(big, topMargin(13));

        LinearLayout grid1 = Ui.row(host);
        resultPing = resultMetric(grid1, "TCP 延迟");
        resultJitter = resultMetric(grid1, "抖动");
        resultLoss = resultMetric(grid1, "连接失败率");
        resultCard.addView(grid1, topMargin(13));
        LinearLayout grid2 = Ui.row(host);
        resultData = resultMetric(grid2, "实测流量");
        resultRoute = resultMetric(grid2, "下载节点");
        resultTime = resultMetric(grid2, "时间");
        resultCard.addView(grid2, topMargin(10));
        resultNote = Ui.small(host, "");
        resultNote.setMaxLines(3);
        resultCard.addView(resultNote, topMargin(8));
        resultShare = Ui.outlineButton(host, "分享本次测速结果");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(host, 12);
        resultCard.addView(resultShare, rlp);
        resultShare.setOnClickListener(v -> shareLatest());
        resultCard.setVisibility(View.GONE);
        content.addView(resultCard);
        if (!history.isEmpty()) showHistoryResult(history.get(0), false);
    }

    private TextView resultMetric(LinearLayout parent, String label) {
        LinearLayout cell = Ui.column(host);
        parent.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(Ui.text(host, label, 8.8f, Ui.DIM, false));
        TextView value = Ui.text(host, "—", 11.5f, Ui.TEXT, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(value, topMargin(4));
        return value;
    }

    private void buildHistoryCard() {
        LinearLayout card = Ui.card(host);
        LinearLayout head = Ui.row(host);
        head.addView(Ui.title(host, "测速记录"), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clearHistory = Ui.quietButton(host, "清空", Ui.DIM);
        head.addView(clearHistory);
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        card.addView(head);
        historyList = Ui.column(host);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(host, 8);
        card.addView(historyList, hlp);
        historyEmpty = Ui.small(host, "还没有测速记录。完成一次测试后，结果会保存在本机。");
        historyEmpty.setGravity(Gravity.CENTER);
        historyEmpty.setPadding(0, Ui.dp(host, 14), 0, Ui.dp(host, 8));
        historyList.addView(historyEmpty);
        content.addView(card);
    }

    private void buildInfoCard() {
        LinearLayout card = Ui.flatCard(host, 0x0CFFFFFF);
        LinearLayout row = Ui.row(host);
        IconView icon = new IconView(host);
        icon.setIcon("globe");
        icon.setColor(Ui.BLUE);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 18), Ui.dp(host, 18)));
        TextView title = Ui.text(host, "测速说明", 12f, Ui.TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(host, 8);
        row.addView(title, tlp);
        card.addView(row);
        TextView body = Ui.small(host, "下载支持华为云、南大、中科大、上交镜像及 Cloudflare；上传统一走 Cloudflare。"
                + "延迟为到下载节点的 TCP 握手时间，吞吐采用 4 线程并发。测试数据流式读取并直接丢弃，不写入手机存储，无需事后删除文件。结果受节点、路由、Wi-Fi 干扰和运营商策略影响。");
        body.setMaxLines(5);
        card.addView(body, topMargin(7));
        content.addView(card);
    }

    private void setMode(boolean quick) {
        if (testing) return;
        quickMode = quick;
        prefs.edit().putBoolean("speed_quick", quick).apply();
        styleToggle();
        updateWarning();
    }

    private void styleToggle() {
        if (modeQuick == null) return;
        modeQuick.setTextColor(quickMode ? 0xFF061611 : Ui.MUTED);
        modeQuick.setBackground(quickMode ? Ui.rounded(host, Ui.ACCENT, 7) : Ui.rounded(host, 0, 7));
        modeFull.setTextColor(!quickMode ? 0xFF061611 : Ui.MUTED);
        modeFull.setBackground(!quickMode ? Ui.rounded(host, Ui.ACCENT, 7) : Ui.rounded(host, 0, 7));
    }

    private void updateWarning() {
        if (warningText == null) return;
        warningText.setText(quickMode
                ? "快速模式：6 秒下载 + 6 秒上传 + TCP 延迟采样。数据只读流式丢弃，不写入手机存储；高速网络可能消耗数百 MB 流量，建议 Wi-Fi。"
                : "完整模式：12 秒下载 + 12 秒上传 + TCP 延迟采样。高速网络下流量可能超过 1 GB；数据不写入手机存储，建议 Wi-Fi。");
    }

    private void prepareStart() {
        if (testing) return;
        NetProbe.Snapshot s = host.latestSnapshot();
        if (s != null && !s.online) {
            Ui.confirm(host, "互联网未验证", "当前网络尚未通过外网验证。仍然开始测速吗？",
                    "仍然测试", this::beginTest);
            return;
        }
        boolean cellular = s != null && !s.wifi.connected && s.transport != null
                && s.transport.contains("蜂窝");
        if (!warned || cellular) {
            String message = "本次" + (quickMode ? "快速" : "完整") + "测速会从所选 CDN 下载、向 Cloudflare 上传测试数据，"
                    + (quickMode ? "高速网络下约消耗数百 MB 流量。" : "高速网络下流量可能超过 1 GB。")
                    + "测速内容只用于计算速率，不会保存成文件；建议保持屏幕常亮并使用 Wi-Fi。";
            Ui.confirm(host, cellular ? "移动数据测速提醒" : "测速流量提醒", message, "开始测试", () -> {
                warned = true;
                prefs.edit().putBoolean("speed_warned", true).apply();
                beginTest();
            });
        } else {
            beginTest();
        }
    }

    private void beginTest() {
        if (testing) return;
        NetProbe.Snapshot s = host.latestSnapshot();
        String route = "当前网络";
        if (s != null) {
            if (s.wifi.connected) {
                route = "Wi-Fi · " + s.wifi.ssid;
            } else {
                NetProbe.CellSim sim = firstSim(s);
                if (sim != null) route = sim.carrier + " · " + sim.networkType;
                else if (s.transport != null) route = s.transport;
            }
        }
        testing = true;
        selectedHistory = null;
        uploadPhase = false;
        samples.clear();
        statDown.setText("—");
        statUp.setText("—");
        statPing.setText("—");
        statJitter.setText("—");
        statDown.setTextColor(Ui.MUTED);
        statUp.setTextColor(Ui.MUTED);
        statPing.setTextColor(Ui.MUTED);
        statJitter.setTextColor(Ui.MUTED);
        phaseHint.setText("正在准备测速…");
        gauge.reset(120);
        gauge.setPhase("准备测试", Ui.ACCENT);
        liveChart.setData(new float[0]);
        liveChart.setColor(Ui.ACCENT);
        liveChart.setFixedRange(0, 100);
        startButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setText("取消测速");
        cancelButton.setEnabled(true);
        resultCard.setVisibility(View.GONE);
        host.setKeepScreenOnForTest(true);
        engine.start(quickMode, providerId, route, new SpeedTestEngine.Listener() {
            @Override
            public void onPhase(String title, String detail) {
                lastUiPhase = System.currentTimeMillis();
                uploadPhase = title.contains("上传");
                phaseHint.setText(title + " · " + detail);
                int color = uploadPhase ? Ui.BLUE : Ui.ACCENT;
                gauge.setPhase(title, color);
                if (title.contains("下载")) {
                    gauge.setValue(0);
                    liveChart.setColor(Ui.ACCENT);
                    liveChart.setData(new float[0]);
                    liveChart.setFixedRange(0, 100);
                } else if (title.contains("上传")) {
                    gauge.setValue(0);
                    liveChart.setColor(Ui.BLUE);
                    liveChart.setData(new float[0]);
                    liveChart.setFixedRange(0, 100);
                }
            }

            @Override
            public void onProgress(double currentMbps, double downloadAvg, double uploadAvg) {
                gauge.setValue((float) currentMbps);
                if (uploadPhase) {
                    statUp.setText(NetProbe.formatMbps(currentMbps));
                    statUp.setTextColor(Ui.BLUE);
                } else {
                    statDown.setText(NetProbe.formatMbps(currentMbps));
                    statDown.setTextColor(Ui.ACCENT);
                }
                samples.add((float) currentMbps);
                while (samples.size() > 80) samples.remove(0);
                float max = 50;
                for (float v : samples) max = Math.max(max, v);
                liveChart.setFixedRange(0, Math.max(100, max * 1.25f));
                liveChart.setData(toArray(samples));
            }

            @Override
            public void onPing(SpeedTestEngine.PingStats stats) {
                if (stats.ok) {
                    statPing.setText(String.format(Locale.US, "%.0f", stats.avgMs));
                    statJitter.setText(String.format(Locale.US, "%.0f", stats.jitterMs));
                    statPing.setTextColor(Ui.PURPLE);
                    statJitter.setTextColor(Ui.AMBER);
                }
            }

            @Override
            public void onComplete(SpeedTestEngine.Result result) {
                testing = false;
                host.setKeepScreenOnForTest(false);
                resetControls();
                gauge.setValue((float) result.downloadMbps);
                gauge.setPhase("测速完成 · 下载", Ui.ACCENT);
                statDown.setText(NetProbe.formatMbps(result.downloadMbps));
                statUp.setText(NetProbe.formatMbps(result.uploadMbps));
                if (Double.isFinite(result.pingMs)) {
                    statPing.setText(String.format(Locale.US, "%.0f", result.pingMs));
                    statJitter.setText(String.format(Locale.US, "%.0f", result.jitterMs));
                }
                phaseHint.setText("测试完成 · " + result.providerName + " 下载 · "
                        + NetProbe.formatBytes(result.dataBytes) + " · 无文件写入");
                HistoryEntry entry = HistoryEntry.from(result);
                history.add(0, entry);
                while (history.size() > 8) history.remove(history.size() - 1);
                saveHistory();
                showHistoryResult(entry, true);
                renderHistory();
            }

            @Override
            public void onError(String message) {
                testing = false;
                host.setKeepScreenOnForTest(false);
                resetControls();
                gauge.setIdlePhase("测试未完成");
                phaseHint.setText("测速失败：" + message);
                Ui.info(host, "测速失败", message + "\n\n请检查外网连接与系统网络权限；可在「测速节点」中切换 CDN 通道。" );
            }

            @Override
            public void onCancelled() {
                testing = false;
                host.setKeepScreenOnForTest(false);
                resetControls();
                gauge.setIdlePhase("已取消");
                phaseHint.setText("本次测速已取消，未保存结果。");
            }
        });
    }

    private void resetControls() {
        startButton.setVisibility(View.VISIBLE);
        startButton.setText("重新测速");
        cancelButton.setVisibility(View.GONE);
        cancelButton.setEnabled(true);
        cancelButton.setText("取消测速");
    }

    private void showHistoryResult(HistoryEntry e, boolean animate) {
        resultCard.setVisibility(View.VISIBLE);
        selectedHistory = e;
        styleChip(resultChip, e.networkLabel == null ? "历史" : e.networkLabel, Ui.BLUE);
        resultDown.setText(NetProbe.formatMbps(e.downMbps));
        resultUp.setText(e.upMbps > 0 ? NetProbe.formatMbps(e.upMbps) : "—");
        resultPing.setText(Double.isFinite(e.pingMs) && e.pingMs > 0
                ? String.format(Locale.US, "%.0f ms", e.pingMs) : "—");
        resultJitter.setText(Double.isFinite(e.jitterMs) && e.jitterMs > 0
                ? String.format(Locale.US, "%.0f ms", e.jitterMs) : "—");
        resultLoss.setText(String.format(Locale.US, "%.0f%%", e.lossPct));
        resultData.setText(NetProbe.formatBytes(e.dataBytes));
        resultRoute.setText(e.providerName == null || e.providerName.isEmpty() ? "—" : e.providerName);
        resultTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(e.timestamp)));
        resultNote.setText("下载 " + (e.providerName == null || e.providerName.isEmpty() ? "—" : e.providerName)
                + " · 上传 " + (e.uploadProviderName == null || e.uploadProviderName.isEmpty()
                ? "Cloudflare" : e.uploadProviderName)
                + " · " + (e.quick ? "快速模式" : "完整模式")
                + (e.uploadNote == null || e.uploadNote.isEmpty() ? "" : " · " + e.uploadNote));
        resultShare.setVisibility(View.VISIBLE);
    }

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        if (history.isEmpty()) {
            historyList.addView(historyEmpty);
            historyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            HistoryEntry e = history.get(i);
            LinearLayout row = Ui.row(host);
            row.setPadding(0, Ui.dp(host, 9), 0, Ui.dp(host, 9));
            row.setClickable(true);
            row.setOnClickListener(v -> showHistoryResult(e, false));
            LinearLayout col = Ui.column(host);
            col.addView(Ui.text(host, new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(e.timestamp)), 10.5f, Ui.TEXT, true));
            String net = e.networkLabel == null ? "—" : e.networkLabel;
            if (e.providerName != null && !e.providerName.isEmpty()) net += " · " + e.providerName;
            if (net.length() > 30) net = net.substring(0, 28) + "…";
            col.addView(Ui.text(host, net, 8.8f, Ui.DIM, false), topMargin(2));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
            TextView down = Ui.text(host, "↓ " + NetProbe.formatMbps(e.downMbps), 11f, Ui.ACCENT, true);
            down.setGravity(Gravity.END);
            row.addView(down, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView up = Ui.text(host, "↑ " + NetProbe.formatMbps(e.upMbps), 11f, Ui.BLUE, true);
            up.setGravity(Gravity.END);
            row.addView(up, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            historyList.addView(row);
            if (i < history.size() - 1) {
                View divider = new View(host);
                divider.setBackgroundColor(0x12FFFFFF);
                historyList.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 1)));
            }
        }
    }

    private void confirmClearHistory() {
        if (history.isEmpty()) {
            Ui.toast(host, "没有测速记录");
            return;
        }
        Ui.confirm(host, "清空测速记录", "仅删除本机保存的测速历史，不影响网络设置。", "清空", () -> {
            history.clear();
            saveHistory();
            renderHistory();
            resultCard.setVisibility(View.GONE);
            Ui.toast(host, "已清空测速记录");
        });
    }

    private void shareLatest() {
        if (history.isEmpty()) return;
        HistoryEntry e = selectedHistory != null ? selectedHistory : history.get(0);
        String text = "网脉 NetPulse 测速结果\n"
                + "时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(e.timestamp)) + "\n"
                + "网络：" + (e.networkLabel == null ? "—" : e.networkLabel) + "\n"
                + "下载：" + NetProbe.formatMbps(e.downMbps) + " Mbps\n"
                + "上传：" + NetProbe.formatMbps(e.upMbps) + " Mbps\n"
                + "TCP 延迟：" + (Double.isFinite(e.pingMs) ? String.format(Locale.US, "%.0f", e.pingMs) + " ms" : "—") + "\n"
                + "抖动：" + (Double.isFinite(e.jitterMs) ? String.format(Locale.US, "%.0f", e.jitterMs) + " ms" : "—") + "\n"
                + "实测流量：" + NetProbe.formatBytes(e.dataBytes) + "\n"
                + "下载节点：" + (e.providerName == null ? "—" : e.providerName) + "\n"
                + "上传节点：" + (e.uploadProviderName == null ? "Cloudflare" : e.uploadProviderName) + "\n"
                + "文件写入：无（流式读取）\n";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            host.startActivity(Intent.createChooser(intent, "分享测速结果"));
        } catch (Exception ex) {
            Ui.info(host, "分享不可用", "设备上没有可用的分享应用。");
        }
    }

    private void loadHistory() {
        try {
            JSONArray array = new JSONArray(prefs.getString("speed_history", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                HistoryEntry e = new HistoryEntry();
                e.timestamp = o.optLong("timestamp");
                e.networkLabel = o.optString("network");
                e.downMbps = o.optDouble("down", 0);
                e.upMbps = o.optDouble("up", 0);
                e.pingMs = o.optDouble("ping", Double.NaN);
                e.jitterMs = o.optDouble("jitter", Double.NaN);
                e.lossPct = o.optDouble("loss", 0);
                e.dataBytes = o.optLong("bytes", 0);
                e.quick = o.optBoolean("quick", true);
                e.uploadNote = o.optString("upload_note", "");
                e.providerName = o.optString("provider_name", "");
                e.uploadProviderName = o.optString("upload_provider", "Cloudflare");
                history.add(e);
            }
        } catch (Exception ignored) {}
    }

    private void saveHistory() {
        try {
            JSONArray array = new JSONArray();
            for (HistoryEntry e : history) {
                JSONObject o = new JSONObject();
                o.put("timestamp", e.timestamp);
                o.put("network", e.networkLabel == null ? "" : e.networkLabel);
                o.put("down", e.downMbps);
                o.put("up", e.upMbps);
                o.put("ping", Double.isFinite(e.pingMs) ? e.pingMs : -1);
                o.put("jitter", Double.isFinite(e.jitterMs) ? e.jitterMs : -1);
                o.put("loss", e.lossPct);
                o.put("bytes", e.dataBytes);
                o.put("quick", e.quick);
                o.put("upload_note", e.uploadNote == null ? "" : e.uploadNote);
                o.put("provider_name", e.providerName == null ? "" : e.providerName);
                o.put("upload_provider", e.uploadProviderName == null ? "Cloudflare" : e.uploadProviderName);
                array.put(o);
            }
            prefs.edit().putString("speed_history", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String exportHistoryText() {
        if (history.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        for (HistoryEntry e : history) {
            sb.append(fmt.format(new Date(e.timestamp))).append(" · ")
                    .append(e.networkLabel == null ? "—" : e.networkLabel)
                    .append(" · ↓ ").append(NetProbe.formatMbps(e.downMbps)).append(" Mbps")
                    .append(" · ↑ ").append(NetProbe.formatMbps(e.upMbps)).append(" Mbps")
                    .append(" · TCP ").append(Double.isFinite(e.pingMs)
                            ? String.format(Locale.US, "%.0f", e.pingMs) + " ms" : "—")
                    .append(" · ").append(e.providerName == null || e.providerName.isEmpty()
                            ? "节点—" : e.providerName)
                    .append("\n");
        }
        return sb.toString();
    }

    private NetProbe.CellSim firstSim(NetProbe.Snapshot s) {
        if (s.sims == null || s.sims.isEmpty()) return null;
        for (NetProbe.CellSim sim : s.sims) if (sim.defaultData) return sim;
        return s.sims.get(0);
    }

    private float[] toArray(List<Float> values) {
        float[] a = new float[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i);
        return a;
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

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (host.latestSnapshot() != null) update(host.latestSnapshot(), 0, 0);
    }

    @Override
    public void onHide() {}

    @Override
    public void update(NetProbe.Snapshot snapshot, double downloadMbps, double uploadMbps) {
        if (snapshot == null || testing) return;
        String route;
        if (snapshot.wifi.connected) route = "Wi-Fi · " + snapshot.wifi.ssid;
        else {
            NetProbe.CellSim sim = firstSim(snapshot);
            route = sim == null ? snapshot.transport : sim.carrier + " · " + sim.networkType;
        }
        styleChip(serverChip, "MULTI CDN", Ui.BLUE);
        if (resultCard.getVisibility() != View.VISIBLE && !history.isEmpty()) {
            showHistoryResult(history.get(0), false);
        }
    }

    public void stopForDestroy() {
        if (testing) {
            engine.cancel();
            testing = false;
        }
        host.setKeepScreenOnForTest(false);
    }

    private static class HistoryEntry {
        long timestamp;
        String networkLabel;
        double downMbps;
        double upMbps;
        double pingMs = Double.NaN;
        double jitterMs = Double.NaN;
        double lossPct;
        long dataBytes;
        boolean quick;
        String uploadNote;
        String providerName = "";
        String uploadProviderName = "Cloudflare";

        static HistoryEntry from(SpeedTestEngine.Result result) {
            HistoryEntry e = new HistoryEntry();
            e.timestamp = result.timestamp;
            e.networkLabel = result.networkLabel;
            e.downMbps = result.downloadMbps;
            e.upMbps = result.uploadMbps;
            e.pingMs = result.pingMs;
            e.jitterMs = result.jitterMs;
            e.lossPct = result.lossPercent;
            e.dataBytes = result.dataBytes;
            e.quick = result.quick;
            e.uploadNote = result.uploadNote;
            e.providerName = result.providerName;
            e.uploadProviderName = result.uploadProviderName;
            return e;
        }
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(host, dp);
        return lp;
    }
}
