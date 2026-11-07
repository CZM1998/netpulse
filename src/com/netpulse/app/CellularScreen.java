package com.netpulse.app;

import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CellularScreen extends ScrollView implements MainActivity.Screen {
    private final MainActivity host;
    private final LinearLayout content;
    private final LinearLayout simContainer;
    private final Map<Integer, SimHolder> holders = new LinkedHashMap<>();
    private final Map<Integer, List<Float>> histories = new HashMap<>();

    private LinearLayout permissionCard;
    private TextView permissionText, permissionAction;
    private LinearLayout locationCard;
    private LinearLayout emptyCard;
    private TextView emptyTitle, emptyBody, emptyState, emptyAirplane, emptySlots, emptyTransport;
    private TextView emptyAction;
    private LinearLayout radioCard;
    private final TextView[] radioValues = new TextView[12];
    private TextView radioCarriers, radioSignal, radioMetrics, radioCellsNote;
    private LinearLayout radioCellList;
    private String lastRadioCellSig = "";
    private String lastSignature = "";
    private int phoneCount = -1;

    public CellularScreen(MainActivity host) {
        super(host);
        this.host = host;
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        setPadding(0, Ui.dp(host, 2), 0, Ui.dp(host, 16));
        content = Ui.column(host);
        content.setPadding(Ui.dp(host, 16), 0, Ui.dp(host, 16), Ui.dp(host, 26));
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        simContainer = Ui.column(host);
        build();
    }

    private void build() {
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.section(host, "蜂窝与 SIM"));
        titleCol.addView(Ui.small(host, "双卡状态、网络制式、服务小区与邻区"), topMargin(4));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = Ui.quietButton(host, "移动网络 ↗", Ui.BLUE);
        settings.setOnClickListener(v -> host.openMobileNetworkSettings());
        head.addView(settings);
        content.addView(head, topMargin(8));

        buildPermissionCard();
        buildLocationCard();
        buildRadioCard();
        buildEmptyCard();
        content.addView(simContainer);
        TextView footer = Ui.small(host, "说明：Android 公开 API 仅提供部分小区字段，5G 频段与邻区取决于基带、运营商和系统版本。");
        footer.setMaxLines(4);
        footer.setPadding(Ui.dp(host, 2), Ui.dp(host, 4), Ui.dp(host, 2), 0);
        content.addView(footer);
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
        icon.setIcon("sim");
        icon.setColor(Ui.AMBER);
        iconWrap.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 18), Ui.dp(host, 18)));
        row.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(host, 34), Ui.dp(host, 34)));
        LinearLayout col = Ui.column(host);
        col.addView(Ui.text(host, "读取双卡需要电话权限", 12.5f, Ui.TEXT, true));
        permissionText = Ui.small(host, "授予权限后可查看 SIM 状态、运营商与蜂窝信号。");
        permissionText.setMaxLines(3);
        col.addView(permissionText, topMargin(3));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = Ui.dp(host, 10);
        row.addView(col, clp);
        permissionAction = Ui.quietButton(host, "去授权", Ui.AMBER);
        permissionAction.setOnClickListener(v -> host.requestCorePermissions());
        row.addView(permissionAction);
        permissionCard.addView(row);
        permissionCard.setVisibility(View.GONE);
        content.addView(permissionCard);
    }

    private void buildLocationCard() {
        locationCard = Ui.flatCard(host, 0x14FFC266);
        GradientDrawable bg = Ui.rounded(host, 0x14FFC266, 12);
        bg.setStroke(Ui.dp(host, 1), 0x44FFC266);
        locationCard.setBackground(bg);
        LinearLayout row = Ui.row(host);
        IconView icon = new IconView(host);
        icon.setIcon("warning");
        icon.setColor(Ui.AMBER);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(host, 16), Ui.dp(host, 16)));
        TextView tv = Ui.text(host, "定位服务已关闭，小区列表和部分基站字段会被系统隐藏。", 10f, Ui.AMBER, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(host, 8);
        row.addView(tv, tlp);
        TextView action = Ui.quietButton(host, "开启", Ui.AMBER);
        action.setOnClickListener(v -> host.openLocationSettings());
        row.addView(action);
        locationCard.addView(row);
        locationCard.setVisibility(View.GONE);
        content.addView(locationCard);
    }

    private void buildEmptyCard() {
        emptyCard = Ui.card(host);
        LinearLayout iconWrap = Ui.row(host);
        iconWrap.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x3338F2C0, 0x125BA8FF});
        iconBg.setCornerRadius(Ui.dp(host, 18));
        iconBg.setStroke(Ui.dp(host, 1), 0x4438F2C0);
        iconWrap.setBackground(iconBg);
        IconView simIcon = new IconView(host);
        simIcon.setIcon("sim");
        simIcon.setColor(Ui.ACCENT);
        iconWrap.addView(simIcon, new LinearLayout.LayoutParams(Ui.dp(host, 30), Ui.dp(host, 30)));
        emptyCard.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(host, 58), Ui.dp(host, 58)));
        emptyTitle = Ui.text(host, "未检测到活动 SIM 卡", 19f, Ui.TEXT, true);
        emptyCard.addView(emptyTitle, topMargin(14));
        emptyBody = Ui.small(host, "卡槽为空、SIM 卡未启用，或当前没有蜂窝服务。插入卡片后会自动刷新。");
        emptyBody.setMaxLines(4);
        emptyCard.addView(emptyBody, topMargin(6));

        LinearLayout facts = Ui.row(host);
        LinearLayout f1 = factCell(facts, "SIM 状态");
        emptyState = (TextView) f1.getChildAt(1);
        LinearLayout f2 = factCell(facts, "飞行模式");
        emptyAirplane = (TextView) f2.getChildAt(1);
        LinearLayout f3 = factCell(facts, "卡槽能力");
        emptySlots = (TextView) f3.getChildAt(1);
        emptyCard.addView(facts, topMargin(14));
        LinearLayout tRow = Ui.row(host);
        tRow.addView(Ui.text(host, "当前传输", 10f, Ui.DIM, false), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        emptyTransport = Ui.text(host, "—", 11f, Ui.MUTED, true);
        tRow.addView(emptyTransport);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trlp.topMargin = Ui.dp(host, 8);
        emptyCard.addView(tRow, trlp);

        emptyAction = Ui.primaryButton(host, "打开移动网络设置");
        emptyAction.setOnClickListener(v -> host.openMobileNetworkSettings());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(host, 16);
        emptyCard.addView(emptyAction, alp);
        TextView note = Ui.small(host, "提示：如果 SIM 卡插着但没有服务，检查卡是否被系统停用、是否欠费，或在系统设置中切换默认数据卡。");
        note.setMaxLines(4);
        emptyCard.addView(note, topMargin(10));
        emptyCard.setVisibility(View.GONE);
        content.addView(emptyCard);
    }

    private void buildRadioCard() {
        radioCard = Ui.card(host);
        LinearLayout head = Ui.row(host);
        LinearLayout titleCol = Ui.column(host);
        titleCol.addView(Ui.title(host, "本机射频与基站"));
        titleCol.addView(Ui.small(host, "无 SIM 时系统仍公开的无线电与运营商信息"), topMargin(3));
        head.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.chip(host, "NO SIM · LIMITED", Ui.AMBER));
        radioCard.addView(head);
        TextView intro = Ui.text(host, "无 SIM 无法注册蜂窝网络。下列信息来自本机基带/Android 公开 API；若为空，代表系统未提供，不是 App 隐藏。",
                9.5f, Ui.DIM, false);
        intro.setMaxLines(4);
        radioCard.addView(intro, topMargin(8));

        String[] labels = {
                "SIM 状态", "射频类型", "Modem / 卡槽",
                "网络运营商", "MCC / MNC", "网络国家",
                "当前制式", "语音注册", "数据注册",
                "数据连接", "数据开关", "漫游 / 选网"
        };
        LinearLayout grid = Ui.column(host);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Ui.dp(host, 12);
        radioCard.addView(grid, glp);
        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout row = Ui.row(host);
            for (int col = 0; col < 3; col++) {
                int idx = rowIndex * 3 + col;
                LinearLayout cell = Ui.column(host);
                cell.addView(Ui.text(host, labels[idx], 8.5f, Ui.DIM, false));
                TextView value = Ui.text(host, "—", 10.5f, Ui.TEXT, true);
                value.setSingleLine(true);
                value.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(value, topMargin(3));
                radioValues[idx] = value;
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col > 0) clp.leftMargin = Ui.dp(host, 7);
                row.addView(cell, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(host, 10);
            grid.addView(row, rlp);
        }

        LinearLayout carrierRow = Ui.row(host);
        TextView carrierLabel = Ui.text(host, "运营商 / 小区广播", 9f, Ui.DIM, false);
        carrierRow.addView(carrierLabel);
        radioCarriers = Ui.text(host, "—", 9.5f, Ui.MUTED, false);
        radioCarriers.setGravity(Gravity.END);
        carrierRow.addView(radioCarriers, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crlp.topMargin = Ui.dp(host, 12);
        radioCard.addView(carrierRow, crlp);

        radioCard.addView(Ui.divider(host));
        LinearLayout sigHead = Ui.row(host);
        sigHead.addView(Ui.text(host, "信号读数", 10.5f, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radioSignal = Ui.text(host, "—", 13f, Ui.MUTED, true);
        sigHead.addView(radioSignal);
        radioCard.addView(sigHead);
        radioMetrics = Ui.small(host, "无活动 SIM 时通常没有实时信号读数");
        radioMetrics.setMaxLines(3);
        radioCard.addView(radioMetrics, topMargin(4));

        LinearLayout cellHead = Ui.row(host);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chlp.topMargin = Ui.dp(host, 12);
        cellHead.addView(Ui.text(host, "基带收到的小区", 10.5f, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cellHead.addView(Ui.chip(host, "PUBLIC API", Ui.BLUE));
        radioCard.addView(cellHead, chlp);
        radioCellList = Ui.column(host);
        radioCard.addView(radioCellList);
        radioCellsNote = Ui.small(host, "");
        radioCellsNote.setMaxLines(4);
        radioCard.addView(radioCellsNote, topMargin(4));

        TextView note = Ui.small(host, "说明：下列小区与 PLMN 来自公开 CellInfo；无 SIM 时字段可能不完整（运营商名、RSRQ/SINR、服务小区标记可能缺失）。插入并启用 SIM 后会自动切换为完整双卡/基站视图。");
        note.setMaxLines(5);
        radioCard.addView(note, topMargin(10));

        LinearLayout actions = Ui.row(host);
        TextView refresh = Ui.outlineButton(host, "重新读取射频");
        refresh.setOnClickListener(v -> {
            NetProbe.Snapshot snap = host.latestSnapshot();
            if (snap != null) update(snap, 0, 0);
            Ui.toast(host, "已重新读取基带公开信息");
        });
        actions.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = Ui.outlineButton(host, "移动网络设置");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = Ui.dp(host, 8);
        settings.setOnClickListener(v -> host.openMobileNetworkSettings());
        actions.addView(settings, slp);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(host, 12);
        radioCard.addView(actions, alp);
        radioCard.setVisibility(View.GONE);
        content.addView(radioCard);
    }

    private void updateRadio(NetProbe.RadioInfo r, NetProbe.Snapshot s) {
        if (r == null || radioCard == null) return;
        NetProbe.CellItem strongest = null;
        if (r.cells != null) {
            for (NetProbe.CellItem c : r.cells) {
                if (c.dbm > -200 && (strongest == null || c.dbm > strongest.dbm)) strongest = c;
            }
        }
        String plmn = "";
        String strongestTech = "";
        if (strongest != null) {
            strongestTech = strongest.tech;
            if (strongest.plmn != null && !strongest.plmn.isEmpty()) plmn = strongest.plmn;
        }
        if (plmn.isEmpty() && r.mccMnc != null && r.mccMnc.length() >= 5) plmn = r.mccMnc;
        String carrier = NetProbe.carrierNameForPlmn(plmn);
        radioValues[0].setText(nz(r.simStateLabel));
        radioValues[1].setText(nz(r.phoneType));
        String modem = r.supportedModemCount > 0
                ? r.activeModemCount + " / " + r.supportedModemCount + " 路"
                : (phoneCount > 0 ? phoneCount + " 张" : "—");
        radioValues[2].setText(modem);
        String op = r.operatorName == null || r.operatorName.isEmpty() ? r.operator : r.operatorName;
        if ((op == null || op.isEmpty()) && !carrier.isEmpty()) op = carrier;
        if (op == null || op.isEmpty()) op = "未注册";
        radioValues[3].setText(op);
        radioValues[4].setText(plmn.isEmpty() ? "—" : plmn);
        radioValues[5].setText(r.networkCountry == null || r.networkCountry.isEmpty()
                ? "—" : r.networkCountry.toUpperCase(Locale.US));
        String netType = r.networkType;
        if ((netType == null || netType.equals("未知") || netType.contains("网络类型"))
                && strongest != null) netType = strongestTech + "（小区扫描）";
        radioValues[6].setText(nz(netType));
        radioValues[7].setText("未知".equals(r.voiceService) ? "无服务（无卡）" : nz(r.voiceService));
        radioValues[8].setText("未知".equals(r.dataService) ? "无服务（无卡）" : nz(r.dataService));
        radioValues[9].setText(nz(r.dataState));
        radioValues[10].setText(r.dataEnabledKnown ? (r.dataEnabled ? "已开启" : "已关闭") : "—");
        radioValues[11].setText((r.roaming ? "漫游 · " : "非漫游 · ") + nz(r.selectionMode));
        if (r.accessibleCarriers != null && !r.accessibleCarriers.isEmpty()) {
            radioCarriers.setText(TextUtils.join(" · ", r.accessibleCarriers));
            radioCarriers.setTextColor(Ui.MUTED);
        } else if (!carrier.isEmpty()) {
            radioCarriers.setText("小区广播：" + carrier + " · " + plmn);
            radioCarriers.setTextColor(Ui.MUTED);
        } else {
            radioCarriers.setText("系统未暴露运营商记录");
            radioCarriers.setTextColor(Ui.DIM);
        }

        int dbm = r.signalDbm;
        if (dbm == Integer.MIN_VALUE && strongest != null) dbm = strongest.dbm;
        boolean hasSignal = dbm > -200 && dbm != Integer.MIN_VALUE;
        if (hasSignal) {
            radioSignal.setText(dbm + " dBm · " + Ui.cellQuality(dbm));
            radioSignal.setTextColor(Ui.cellQualityColor(dbm));
        } else {
            radioSignal.setText("系统未提供");
            radioSignal.setTextColor(Ui.DIM);
        }
        String metrics = metricsText(r.metrics);
        if (metrics.isEmpty() && strongest != null) {
            metrics = "最强小区 " + strongest.tech + " · PCI " + strongest.pci
                    + (strongest.hasBand() ? " · " + strongest.band : "")
                    + " · " + dbm + " dBm"
                    + (strongest.extra == null || strongest.extra.isEmpty() ? "" : " · " + strongest.extra);
        }
        if (metrics.isEmpty()) {
            radioMetrics.setText(r.signalNote == null || r.signalNote.isEmpty()
                    ? "无活动 SIM 时通常没有实时信号读数。" : r.signalNote);
        } else {
            radioMetrics.setText(metrics);
        }
        rebuildRadioCells(r);
    }

    private String metricsText(List<NetProbe.SignalMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(6, metrics.size()); i++) {
            NetProbe.SignalMetric m = metrics.get(i);
            if (sb.length() > 0) sb.append(" · ");
            sb.append(m.name).append(' ').append(m.text());
        }
        return sb.toString();
    }

    private void rebuildRadioCells(NetProbe.RadioInfo r) {
        if (radioCellList == null) return;
        StringBuilder sig = new StringBuilder();
        if (r.cells != null) {
            for (NetProbe.CellItem c : r.cells) {
                sig.append(c.tech).append('|').append(c.pci).append('|').append(c.channelHint)
                        .append('|').append(c.dbm).append('|').append(c.plmn).append(';');
            }
        }
        if (sig.toString().equals(lastRadioCellSig)) {
            if (r.cells == null || r.cells.isEmpty()) {
                radioCellsNote.setText(r.cellsNote == null || r.cellsNote.isEmpty()
                        ? "无活动 SIM 时 Android 通常不返回服务小区或邻区。" : r.cellsNote);
                radioCellsNote.setVisibility(View.VISIBLE);
            }
            return;
        }
        lastRadioCellSig = sig.toString();
        radioCellList.removeAllViews();
        if (r.cells == null || r.cells.isEmpty()) {
            radioCellsNote.setText(r.cellsNote == null || r.cellsNote.isEmpty()
                    ? "无活动 SIM 时 Android 通常不返回服务小区或邻区。" : r.cellsNote);
            radioCellsNote.setVisibility(View.VISIBLE);
            return;
        }
        radioCellsNote.setVisibility(View.GONE);
        int shown = 0;
        boolean first = true;
        for (NetProbe.CellItem c : r.cells) {
            if (shown >= 8) break;
            LinearLayout block = Ui.column(host);
            block.setPadding(0, Ui.dp(host, 7), 0, Ui.dp(host, 7));
            LinearLayout row = Ui.row(host);
            String tag = c.registered ? "服务" : (first ? "最强" : "邻区");
            TextView tech = Ui.text(host, tag + " · " + c.tech, 8.5f,
                    c.registered ? Ui.ACCENT : (first ? Ui.AMBER : Ui.BLUE), true);
            tech.setPadding(Ui.dp(host, 6), Ui.dp(host, 3), Ui.dp(host, 6), Ui.dp(host, 3));
            tech.setBackground(Ui.rounded(host,
                    c.registered ? 0x1638F2C0 : (first ? 0x16FFC266 : 0x125BA8FF), 6));
            row.addView(tech);
            String descText = "PCI " + c.pci + " · " + (c.hasBand() ? c.band : "频段—")
                    + (c.arfcn > 0 ? " · ARFCN " + c.arfcn : "");
            TextView desc = Ui.text(host, descText, 9f, Ui.MUTED, false);
            desc.setSingleLine(true);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            dlp.leftMargin = Ui.dp(host, 7);
            row.addView(desc, dlp);
            TextView dbm = Ui.mono(host, c.dbm == Integer.MIN_VALUE ? "—" : c.dbm + " dBm", 9f,
                    c.dbm == Integer.MIN_VALUE ? Ui.DIM : Ui.cellQualityColor(c.dbm));
            row.addView(dbm);
            block.addView(row);
            StringBuilder extra = new StringBuilder();
            if (c.plmn != null && !c.plmn.isEmpty()) {
                String carrier = NetProbe.carrierNameForPlmn(c.plmn);
                extra.append("PLMN ").append(c.plmn);
                if (!carrier.isEmpty()) extra.append(" · ").append(carrier);
            }
            if (c.tac > 0 && c.tac != Integer.MAX_VALUE) extra.append(" · TAC ").append(c.tac);
            if (c.cellId > 0 && c.cellId != Integer.MAX_VALUE && c.cellId != 0xFFFFFFFFL) {
                extra.append(" · CID ").append(c.cellId);
            }
            if (c.extra != null && !c.extra.isEmpty() && !"SINR —".equals(c.extra)) {
                if (extra.length() > 0) extra.append(" · ");
                extra.append(c.extra);
            }
            if (extra.length() > 0) {
                TextView sub = Ui.mono(host, extra.toString(), 7.8f, Ui.DIM);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = Ui.dp(host, 3);
                block.addView(sub, slp);
            }
            radioCellList.addView(block);
            if (shown < 7) {
                View divider = new View(host);
                divider.setBackgroundColor(0x12FFFFFF);
                LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 1));
                dlp2.topMargin = Ui.dp(host, 2);
                radioCellList.addView(divider, dlp2);
            }
            first = false;
            shown++;
        }
    }

    private LinearLayout factCell(LinearLayout parent, String label) {
        LinearLayout cell = Ui.column(host);
        parent.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(Ui.text(host, label, 9f, Ui.DIM, false));
        TextView value = Ui.text(host, "—", 13f, Ui.TEXT, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(value, topMargin(4));
        return cell;
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
    public void update(NetProbe.Snapshot s, double downloadMbps, double uploadMbps) {
        if (s == null) return;
        boolean phone = NetProbe.hasPhonePermission(host);
        permissionCard.setVisibility(phone ? View.GONE : View.VISIBLE);
        if (!phone) {
            emptyCard.setVisibility(View.GONE);
            locationCard.setVisibility(View.GONE);
            radioCard.setVisibility(View.GONE);
            simContainer.setVisibility(View.GONE);
            return;
        }
        simContainer.setVisibility(View.VISIBLE);
        locationCard.setVisibility(s.locationEnabled ? View.GONE : View.VISIBLE);

        List<NetProbe.CellSim> sims = s.sims == null ? new ArrayList<>() : s.sims;
        StringBuilder signature = new StringBuilder();
        for (NetProbe.CellSim sim : sims) signature.append(sim.subId).append(':').append(sim.slot).append(';');
        String sig = signature.toString();
        if (!sig.equals(lastSignature)) {
            lastSignature = sig;
            rebuildSimCards(sims);
        }
        if (sims.isEmpty()) {
            emptyCard.setVisibility(View.VISIBLE);
            radioCard.setVisibility(View.VISIBLE);
            simContainer.setVisibility(View.GONE);
            updateEmpty(s);
            updateRadio(s.radio, s);
        } else {
            emptyCard.setVisibility(View.GONE);
            radioCard.setVisibility(View.GONE);
            for (NetProbe.CellSim sim : sims) {
                SimHolder holder = holders.get(sim.subId);
                if (holder != null) holder.update(sim, s);
            }
        }
    }

    private void updateEmpty(NetProbe.Snapshot s) {
        boolean absent = s.simState != null && s.simState.contains("未插卡");
        boolean ready = s.simState != null && s.simState.contains("就绪");
        if (absent) {
            emptyTitle.setText("卡槽里没有 SIM 卡");
        } else if (ready) {
            emptyTitle.setText("SIM 卡存在，但未激活");
        } else {
            emptyTitle.setText("未检测到活动 SIM 卡");
        }
        if (absent) {
            emptyBody.setText("卡槽为空，或 SIM 卡未被系统识别。插入卡片后会自动刷新。");
        } else if (ready) {
            emptyBody.setText("系统检测到 SIM 卡已就绪，但没有活动订阅。请在系统设置中启用 SIM 卡，或检查双卡管理。");
        } else {
            emptyBody.setText(s.simError == null || s.simError.isEmpty()
                    ? "卡槽为空、SIM 卡未启用，或当前没有蜂窝服务。插入卡片后会自动刷新。"
                    : s.simError + "。插入并启用 SIM 卡后会自动刷新。");
        }
        emptyState.setText(nz(s.simState));
        emptyAirplane.setText(s.airplaneMode ? "已开启" : "关闭");
        emptySlots.setText(phoneCount > 0 ? phoneCount + " 张" : "—");
        emptyTransport.setText(nz(s.transport));
        emptyAction.setText("打开移动网络设置");
    }

    private void rebuildSimCards(List<NetProbe.CellSim> sims) {
        simContainer.removeAllViews();
        holders.clear();
        for (NetProbe.CellSim sim : sims) {
            SimHolder holder = new SimHolder(sim);
            holders.put(sim.subId, holder);
            simContainer.addView(holder.card);
        }
    }

    private int phoneCount() {
        if (phoneCount >= 0) return phoneCount;
        try {
            TelephonyManager tm = (TelephonyManager) host.getSystemService(android.content.Context.TELEPHONY_SERVICE);
            if (tm == null) phoneCount = 0;
            else if (Build.VERSION.SDK_INT >= 30) phoneCount = tm.getSupportedModemCount();
            else phoneCount = tm.getPhoneCount();
            if (phoneCount <= 0 && tm != null) {
                try {
                    SubscriptionManager sm = (SubscriptionManager) host.getSystemService(
                            android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (sm != null) {
                        List<SubscriptionInfo> accessible = sm.getAccessibleSubscriptionInfoList();
                        if (accessible != null) phoneCount = accessible.size();
                    }
                } catch (Exception ignored) {}
                if (phoneCount <= 0) {
                    try { phoneCount = tm.getPhoneCount(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            phoneCount = 0;
        }
        return phoneCount;
    }

    private class SimHolder {
        final LinearLayout card;
        final TextView simChip, carrier, subline, roleChip, roamChip, bigSignal, quality, networkType, serviceText, dataText;
        final SignalBarsView bars;
        final TrendChartView chart;
        final TextView extraMetrics, signalNote, cellNote, neighborNote;
        final TextView[] metricNames = new TextView[3];
        final TextView[] metricValues = new TextView[3];
        final TextView[] cellKeys = new TextView[6];
        final TextView[] cellValues = new TextView[6];
        final List<NeighborRow> neighborRows = new ArrayList<>();
        final List<Float> history;
        final int subId;

        SimHolder(NetProbe.CellSim initial) {
            subId = initial.subId;
            history = histories.computeIfAbsent(subId, k -> new ArrayList<>());
            card = Ui.card(host);
            LinearLayout head = Ui.row(host);
            simChip = Ui.chip(host, "SIM " + initial.slot, Ui.ACCENT);
            head.addView(simChip);
            carrier = Ui.text(host, nz(initial.carrier), 19f, Ui.TEXT, true);
            carrier.setSingleLine(true);
            carrier.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = Ui.dp(host, 9);
            head.addView(carrier, clp);
            roleChip = Ui.chip(host, "数据卡", Ui.MUTED);
            head.addView(roleChip);
            card.addView(head);
            subline = Ui.mono(host, "正在读取 SIM 信息…", 9.5f, Ui.DIM);
            subline.setSingleLine(true);
            subline.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(subline, topMargin(5));

            LinearLayout signalRow = Ui.row(host);
            bars = new SignalBarsView(host);
            bars.setBars(4);
            bars.set(0, Ui.DIM);
            signalRow.addView(bars, new LinearLayout.LayoutParams(Ui.dp(host, 32), Ui.dp(host, 21)));
            bigSignal = Ui.text(host, "—", 33f, Ui.TEXT, true);
            LinearLayout.LayoutParams bslp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bslp.leftMargin = Ui.dp(host, 10);
            signalRow.addView(bigSignal, bslp);
            TextView dbm = Ui.text(host, "dBm", 10.5f, Ui.MUTED, false);
            LinearLayout.LayoutParams dblp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dblp.leftMargin = Ui.dp(host, 5);
            dblp.bottomMargin = Ui.dp(host, 4);
            signalRow.addView(dbm, dblp);
            quality = Ui.text(host, "—", 11f, Ui.MUTED, true);
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qlp.leftMargin = Ui.dp(host, 8);
            qlp.bottomMargin = Ui.dp(host, 6);
            signalRow.addView(quality, qlp);
            View stretch = new View(host);
            signalRow.addView(stretch, new LinearLayout.LayoutParams(0, 1, 1f));
            LinearLayout typeCol = Ui.column(host);
            typeCol.setGravity(Gravity.END);
            networkType = Ui.text(host, "—", 17f, Ui.PURPLE, true);
            networkType.setGravity(Gravity.END);
            typeCol.addView(networkType);
            serviceText = Ui.text(host, "—", 9f, Ui.DIM, false);
            serviceText.setGravity(Gravity.END);
            typeCol.addView(serviceText, topMargin(2));
            signalRow.addView(typeCol);
            card.addView(signalRow, topMargin(11));

            roamChip = Ui.chip(host, "漫游", Ui.AMBER);
            roamChip.setVisibility(View.GONE);
            LinearLayout chips = Ui.row(host);
            chips.addView(roamChip);
            dataText = Ui.text(host, "—", 9.5f, Ui.MUTED, false);
            LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dtlp.leftMargin = Ui.dp(host, 8);
            chips.addView(dataText, dtlp);
            card.addView(chips, topMargin(5));

            chart = new TrendChartView(host);
            chart.setFixedRange(-130, -60);
            chart.setColor(Ui.ACCENT);
            chart.setEmptyText("暂无蜂窝信号采样");
            LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 70));
            clp2.topMargin = Ui.dp(host, 3);
            card.addView(chart, clp2);

            LinearLayout metrics = Ui.row(host);
            for (int i = 0; i < 3; i++) {
                LinearLayout col = Ui.column(host);
                metricNames[i] = Ui.text(host, i == 0 ? "RSRP" : i == 1 ? "RSRQ" : "SINR", 8.8f, Ui.DIM, false);
                col.addView(metricNames[i]);
                metricValues[i] = Ui.text(host, "—", 13f, Ui.TEXT, true);
                col.addView(metricValues[i], topMargin(4));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) mlp.leftMargin = Ui.dp(host, 8);
                metrics.addView(col, mlp);
            }
            card.addView(metrics, topMargin(9));
            extraMetrics = Ui.small(host, "");
            extraMetrics.setMaxLines(2);
            card.addView(extraMetrics, topMargin(6));
            signalNote = Ui.text(host, "", 10f, Ui.AMBER, false);
            signalNote.setMaxLines(3);
            card.addView(signalNote);

            card.addView(Ui.divider(host));
            LinearLayout cellHead = Ui.row(host);
            cellHead.addView(Ui.title(host, "服务小区"), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            cellHead.addView(Ui.chip(host, "公开 API", Ui.BLUE));
            card.addView(cellHead);
            LinearLayout cellGrid = Ui.column(host);
            LinearLayout.LayoutParams cglp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cglp.topMargin = Ui.dp(host, 8);
            card.addView(cellGrid, cglp);
            String[] cellLabels = {"制式", "PCI", "频段", "ARFCN", "TAC", "小区 ID"};
            for (int row = 0; row < 2; row++) {
                LinearLayout r = Ui.row(host);
                if (row == 1) {
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = Ui.dp(host, 9);
                    cellGrid.addView(r, rlp);
                } else {
                    cellGrid.addView(r);
                }
                for (int col = 0; col < 3; col++) {
                    int index = row * 3 + col;
                    LinearLayout cell = Ui.column(host);
                    cellKeys[index] = Ui.text(host, cellLabels[index], 8.8f, Ui.DIM, false);
                    cell.addView(cellKeys[index]);
                    cellValues[index] = Ui.text(host, "—", 11f, Ui.TEXT, true);
                    cellValues[index].setSingleLine(true);
                    cellValues[index].setEllipsize(TextUtils.TruncateAt.END);
                    cell.addView(cellValues[index], topMargin(4));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (col > 0) lp.leftMargin = Ui.dp(host, 7);
                    r.addView(cell, lp);
                }
            }
            cellNote = Ui.small(host, "");
            cellNote.setMaxLines(3);
            card.addView(cellNote, topMargin(7));

            LinearLayout neighHead = Ui.row(host);
            LinearLayout.LayoutParams nhlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nhlp.topMargin = Ui.dp(host, 12);
            neighHead.addView(Ui.title(host, "邻区"), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            neighHead.addView(Ui.chip(host, "最多 5 条", Ui.MUTED));
            card.addView(neighHead, nhlp);
            LinearLayout neigh = Ui.column(host);
            card.addView(neigh);
            for (int i = 0; i < 5; i++) {
                NeighborRow nr = new NeighborRow();
                neighborRows.add(nr);
                neigh.addView(nr.root);
            }
            neighborNote = Ui.small(host, "未返回邻区信息（系统或运营商限制）");
            card.addView(neighborNote, topMargin(4));

            TextView actions = Ui.outlineButton(host, "打开系统移动网络设置 ↗");
            actions.setOnClickListener(v -> host.openMobileNetworkSettings());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dp(host, 12);
            card.addView(actions, alp);

            card.addView(Ui.divider(host));
            TextView netHead = Ui.title(host, "网络注册");
            card.addView(netHead);
            LinearLayout details = Ui.column(host);
            card.addView(details, topMargin(6));
            addDetail(details, "语音注册", sim -> sim.voiceService);
            addDetail(details, "数据注册", sim -> sim.dataService);
            addDetail(details, "数据连接", sim -> sim.dataState);
            addDetail(details, "默认数据卡", sim -> sim.defaultData ? "是" : "否");
            addDetail(details, "数据开关", sim -> !sim.dataEnabledKnown ? "未知" : sim.dataEnabled ? "已开启" : "已关闭");
            addDetail(details, "运营商代码", sim -> sim.mccMnc);
        }

        private void addDetail(LinearLayout parent, String label, java.util.function.Function<NetProbe.CellSim, String> getter) {
            LinearLayout row = Ui.row(host);
            row.addView(Ui.text(host, label, 10f, Ui.DIM, false), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = Ui.text(host, "—", 10.5f, Ui.MUTED, true);
            value.setGravity(Gravity.END);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(host, 7);
            parent.addView(row, lp);
            detailGetters.add(new Detail(getter, value));
        }

        final List<Detail> detailGetters = new ArrayList<>();

        void update(NetProbe.CellSim sim, NetProbe.Snapshot s) {
            simChip.setText("SIM " + sim.slot);
            carrier.setText(nz(sim.carrier));
            String op = sim.operator == null || sim.operator.isEmpty() ? sim.simOperator : sim.operator;
            subline.setText("SLOT " + sim.slot + " · " + nz(op) + (sim.mccMnc.isEmpty() ? "" : " · " + sim.mccMnc));
            styleChip(simChip, "SIM " + sim.slot, Ui.ACCENT);
            if (sim.defaultData) {
                styleChip(roleChip, "数据卡", Ui.ACCENT);
            } else {
                styleChip(roleChip, "待机", Ui.MUTED);
            }
            roamChip.setVisibility(sim.roaming ? View.VISIBLE : View.GONE);
            dataText.setText(nz(sim.dataState) + (sim.dataEnabledKnown ? " · 数据" + (sim.dataEnabled ? "开" : "关") : ""));
            networkType.setText(nz(sim.networkType));
            int typeColor = sim.fiveG ? Ui.PURPLE : Ui.BLUE;
            networkType.setTextColor(typeColor);
            serviceText.setText(nz(sim.voiceService) + " · " + nz(sim.dataService));
            if (sim.error != null) {
                bigSignal.setText("—");
                quality.setText("读取失败");
                quality.setTextColor(Ui.RED);
                bars.set(0, Ui.DIM);
                signalNote.setText(sim.error);
                signalNote.setVisibility(View.VISIBLE);
                chart.setData(new float[0]);
                for (int i = 0; i < 3; i++) {
                    metricNames[i].setText(i == 0 ? "RSRP" : i == 1 ? "RSRQ" : "SINR");
                    metricValues[i].setText("—");
                }
                extraMetrics.setText("");
                applyCells(sim);
                applyDetails(sim);
                return;
            }
            if (sim.signalDbm != Integer.MIN_VALUE) {
                bigSignal.setText(String.valueOf(sim.signalDbm));
                int color = Ui.cellQualityColor(sim.signalDbm);
                bigSignal.setTextColor(Ui.TEXT);
                quality.setText(Ui.cellQuality(sim.signalDbm));
                quality.setTextColor(color);
                bars.set(sim.signalLevel, color);
                chart.setColor(color);
                append(history, sim.signalDbm);
                chart.setData(toArray(history));
                signalNote.setText(sim.signalNote == null ? "" : sim.signalNote);
                signalNote.setVisibility(sim.signalNote == null || sim.signalNote.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                bigSignal.setText("—");
                quality.setText("无信号");
                quality.setTextColor(Ui.DIM);
                bars.set(0, Ui.DIM);
                chart.setData(new float[0]);
                signalNote.setText(sim.signalNote == null ? "无可用信号读数" : sim.signalNote);
                signalNote.setVisibility(View.VISIBLE);
            }
            for (int i = 0; i < 3; i++) {
                if (i < sim.metrics.size()) {
                    NetProbe.SignalMetric m = sim.metrics.get(i);
                    metricNames[i].setText(m.name);
                    metricValues[i].setText(m.text());
                    metricValues[i].setTextColor(i == 0 ? Ui.TEXT : Ui.MUTED);
                } else {
                    metricNames[i].setText(i == 0 ? "RSRP" : i == 1 ? "RSRQ" : "SINR");
                    metricValues[i].setText("—");
                    metricValues[i].setTextColor(Ui.MUTED);
                }
            }
            StringBuilder extra = new StringBuilder();
            for (int i = 3; i < sim.metrics.size(); i++) {
                NetProbe.SignalMetric m = sim.metrics.get(i);
                if (extra.length() > 0) extra.append(" · ");
                extra.append(m.name).append(" ").append(m.text());
            }
            if (extra.length() == 0) {
                extra.append("信号等级 ").append(sim.signalLevel).append("/4");
                if (sim.tech != null) extra.append(" · ").append(sim.tech);
            }
            extraMetrics.setText(extra.toString());
            applyCells(sim);
            applyDetails(sim);
        }

        private void applyCells(NetProbe.CellSim sim) {
            NetProbe.CellItem primary = null;
            for (NetProbe.CellItem c : sim.cells) {
                if (c.registered) { primary = c; break; }
            }
            if (primary == null && !sim.cells.isEmpty()) primary = sim.cells.get(0);
            if (primary != null) {
                cellValues[0].setText(primary.tech);
                cellValues[1].setText(primary.pci > 0 ? String.valueOf(primary.pci) : "—");
                cellValues[2].setText(primary.hasBand() ? primary.band : "—");
                cellValues[3].setText(primary.arfcn > 0 ? String.valueOf(primary.arfcn) : "—");
                cellValues[4].setText(primary.tac > 0 ? String.valueOf(primary.tac) : "—");
                cellValues[5].setText(primary.cellId > 0 ? String.valueOf(primary.cellId) : "—");
                StringBuilder note = new StringBuilder();
                if (!primary.registered) note.append("系统未标记服务小区，展示最强小区。");
                if (primary.dbm != Integer.MIN_VALUE) note.append(" 主小区信号 ").append(primary.dbm).append(" dBm。");
                if (primary.bandwidthKhz > 0) note.append(" 带宽 ").append(primary.bandwidthKhz / 1000).append(" MHz。");
                cellNote.setText(note.length() == 0 ? "小区标识来自 Android 公开 API，仅供技术参考。" : note.toString());
            } else {
                for (TextView v : cellValues) v.setText("—");
                cellNote.setText(sim.cellsNote == null || sim.cellsNote.isEmpty()
                        ? "未获取到服务小区。请开启定位服务并保持移动网络可用。" : sim.cellsNote);
            }
            cellNote.setVisibility(View.VISIBLE);
            List<NetProbe.CellItem> neighbors = new ArrayList<>();
            for (NetProbe.CellItem c : sim.cells) {
                if (primary == c || c.registered) continue;
                neighbors.add(c);
            }
            for (int i = 0; i < neighborRows.size(); i++) {
                NeighborRow row = neighborRows.get(i);
                if (i < neighbors.size()) {
                    NetProbe.CellItem c = neighbors.get(i);
                    row.root.setVisibility(View.VISIBLE);
                    row.tech.setText(c.tech);
                    row.tech.setTextColor("5G NR".equals(c.tech) ? Ui.PURPLE : Ui.BLUE);
                    row.desc.setText("PCI " + (c.pci > 0 ? c.pci : "—") + " · "
                            + (c.hasBand() ? c.band : "频段—")
                            + (c.arfcn > 0 ? " · ARFCN " + c.arfcn : ""));
                    row.dbm.setText(c.dbm == Integer.MIN_VALUE ? "—" : c.dbm + " dBm");
                    row.dbm.setTextColor(c.dbm == Integer.MIN_VALUE ? Ui.DIM : Ui.cellQualityColor(c.dbm));
                } else {
                    row.root.setVisibility(View.GONE);
                }
            }
            neighborNote.setVisibility(neighbors.isEmpty() ? View.VISIBLE : View.GONE);
            if (neighbors.size() > neighborRows.size()) {
                neighborNote.setVisibility(View.VISIBLE);
                neighborNote.setText("已显示 " + neighborRows.size() + " 个邻区，另有 "
                        + (neighbors.size() - neighborRows.size()) + " 个未显示。");
            }
        }

        private void applyDetails(NetProbe.CellSim sim) {
            for (Detail d : detailGetters) {
                d.value.setText(nz(d.getter.apply(sim)));
            }
        }

        class NeighborRow {
            final LinearLayout root;
            final TextView tech;
            final TextView desc;
            final TextView dbm;

            NeighborRow() {
                root = Ui.row(host);
                root.setPadding(0, Ui.dp(host, 8), 0, Ui.dp(host, 8));
                tech = Ui.text(host, "—", 9f, Ui.BLUE, true);
                tech.setGravity(Gravity.CENTER);
                GradientDrawable bg = Ui.rounded(host, 0x125BA8FF, 7);
                bg.setStroke(Ui.dp(host, 1), 0x305BA8FF);
                tech.setBackground(bg);
                tech.setPadding(Ui.dp(host, 6), Ui.dp(host, 3), Ui.dp(host, 6), Ui.dp(host, 3));
                root.addView(tech);
                desc = Ui.text(host, "—", 9.5f, Ui.MUTED, false);
                desc.setSingleLine(true);
                desc.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                dlp.leftMargin = Ui.dp(host, 8);
                root.addView(desc, dlp);
                dbm = Ui.mono(host, "—", 9.5f, Ui.MUTED);
                root.addView(dbm);
            }
        }
    }

    private static class Detail {
        final java.util.function.Function<NetProbe.CellSim, String> getter;
        final TextView value;
        Detail(java.util.function.Function<NetProbe.CellSim, String> getter, TextView value) {
            this.getter = getter;
            this.value = value;
        }
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

    private void append(List<Float> list, float value) {
        if (value <= -120 || !Float.isFinite(value)) return;
        list.add(value);
        while (list.size() > 80) list.remove(0);
    }

    private float[] toArray(List<Float> values) {
        float[] a = new float[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i);
        return a;
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
}
