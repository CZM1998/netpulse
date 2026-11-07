package com.netpulse.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.TrafficStats;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Collects a best-effort snapshot using Android's public telephony and WiFi APIs. */
public final class NetProbe {
    public static final String PERM_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final String PERM_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String PERM_PHONE_STATE = Manifest.permission.READ_PHONE_STATE;

    private final Context context;
    private final WifiManager wifiManager;
    private final ConnectivityManager connectivityManager;
    private final TelephonyManager telephonyManager;
    private final SubscriptionManager subscriptionManager;
    private final LocationManager locationManager;

    public NetProbe(Context context) {
        this.context = context.getApplicationContext();
        wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        telephonyManager = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
        subscriptionManager = (SubscriptionManager) this.context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean hasPermission(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFineLocation(Context c) {
        return hasPermission(c, PERM_FINE_LOCATION);
    }

    public static boolean hasPhonePermission(Context c) {
        return hasPermission(c, PERM_PHONE_STATE);
    }

    public static boolean hasNearbyWifiPermission(Context c) {
        return Build.VERSION.SDK_INT < 33
                || hasPermission(c, "android.permission.NEARBY_WIFI_DEVICES");
    }

    public boolean isLocationEnabled() {
        try {
            return locationManager != null && locationManager.isLocationEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public WifiManager wifiManager() {
        return wifiManager;
    }

    public ConnectivityManager connectivityManager() {
        return connectivityManager;
    }

    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.airplaneMode = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        s.locationEnabled = isLocationEnabled();
        s.fineLocation = hasFineLocation(context);
        s.phonePermission = hasPhonePermission(context);
        s.nearbyWifiPermission = hasNearbyWifiPermission(context);
        readConnectivity(s);
        s.wifi = readWifi();
        readSims(s);
        if (s.sims.isEmpty()) s.radio = readRadio(s);
        readTraffic(s);
        return s;
    }

    private void readConnectivity(Snapshot s) {
        try {
            Network active = connectivityManager.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : connectivityManager.getNetworkCapabilities(active);
            if (caps == null) {
                s.transport = "离线";
                s.online = false;
                s.validated = false;
                return;
            }
            s.vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.transport = "Wi-Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s.transport = "蜂窝网络";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.transport = "以太网";
            else if (s.vpn) s.transport = "VPN";
            else s.transport = "其他网络";
            s.validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            s.online = s.validated;
            s.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            s.linkDownKbps = caps.getLinkDownstreamBandwidthKbps();
            s.linkUpKbps = caps.getLinkUpstreamBandwidthKbps();
            if (active != null) {
                LinkProperties lp = connectivityManager.getLinkProperties(active);
                readLinkProperties(s, lp);
            }
        } catch (Exception e) {
            s.transport = "未知";
            s.online = false;
        }
    }

    private void readLinkProperties(Snapshot s, LinkProperties lp) {
        if (lp == null) return;
        s.interfaceName = lp.getInterfaceName();
        s.mtu = lp.getMtu();
        StringBuilder ipv6 = new StringBuilder();
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress addr = la.getAddress();
            if (addr instanceof Inet4Address) {
                s.ipv4 = addr.getHostAddress();
            } else if (!addr.isLinkLocalAddress() && ipv6.length() == 0) {
                ipv6.append(addr.getHostAddress());
            }
        }
        s.ipv6 = ipv6.toString();
        List<String> dns = new ArrayList<>();
        for (InetAddress addr : lp.getDnsServers()) {
            if (!addr.isLinkLocalAddress()) dns.add(addr.getHostAddress());
        }
        s.dns = join(dns, 2);
        for (RouteInfo route : lp.getRoutes()) {
            if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
                s.gateway = route.getGateway().getHostAddress();
                break;
            }
        }
    }

    private WifiData readWifi() {
        WifiData w = new WifiData();
        if (wifiManager == null) {
            w.error = "本机没有 Wi-Fi 服务";
            return w;
        }
        try {
            w.enabled = wifiManager.isWifiEnabled();
        } catch (Exception e) {
            w.enabled = false;
        }
        if (!w.enabled) {
            w.error = "Wi-Fi 已关闭";
            return w;
        }
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null) {
                w.error = "暂无 Wi-Fi 连接";
                return w;
            }
            String rawSsid = info.getSSID();
            w.ssid = cleanSsid(rawSsid);
            w.bssid = info.getBSSID();
            w.rssi = info.getRssi();
            w.frequency = info.getFrequency();
            w.linkSpeedMbps = info.getLinkSpeed();
            w.rxLinkSpeedMbps = info.getRxLinkSpeedMbps();
            w.txLinkSpeedMbps = info.getTxLinkSpeedMbps();
            if (Build.VERSION.SDK_INT >= 30) {
                w.maxTxLinkSpeedMbps = info.getMaxSupportedTxLinkSpeedMbps();
                w.standard = standardName(info.getWifiStandard());
            }
            w.mac = info.getMacAddress();
            w.ssidKnown = rawSsid != null && !rawSsid.contains("<unknown ssid>")
                    && !"<unknown ssid>".equals(rawSsid);
            w.connected = info.getSupplicantState() == android.net.wifi.SupplicantState.COMPLETED
                    && w.ssidKnown && w.frequency > 0;
            w.networkId = info.getNetworkId();

            NetworkCapabilities caps = null;
            try {
                Network active = connectivityManager.getActiveNetwork();
                caps = active == null ? null : connectivityManager.getNetworkCapabilities(active);
            } catch (Exception ignored) {}
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                w.connected = true;
            }

            if (hasFineLocation(context) && w.rssi > -120 && w.rssi != 0) {
                w.level = wifiLevel(w.rssi);
            }

            ScanResult match = findScanResult(w.bssid);
            if (match != null) {
                w.channel = ChannelSpectrumView.channelForFrequency(match.frequency);
                w.widthMhz = widthMhz(match.channelWidth);
                w.security = securityName(match);
                if (w.standard == null && Build.VERSION.SDK_INT >= 30) {
                    w.standard = standardName(match.getWifiStandard());
                }
            } else if (Build.VERSION.SDK_INT >= 31) {
                w.security = securityTypeName(info.getCurrentSecurityType());
            }
            if (w.channel == 0) w.channel = ChannelSpectrumView.channelForFrequency(w.frequency);
            w.band = bandName(w.frequency);
            if (w.connected) {
                try {
                    Network active = connectivityManager.getActiveNetwork();
                    LinkProperties lp = active == null ? null : connectivityManager.getLinkProperties(active);
                    readWifiLinkProperties(w, lp);
                } catch (Exception ignored) {}
            }
            if (!w.ssidKnown) {
                w.securityNote = "Wi-Fi 名称被系统隐藏，请授予精确定位权限并开启定位服务";
            }
        } catch (SecurityException e) {
            w.error = "缺少读取 Wi-Fi 详情的权限";
        } catch (Exception e) {
            w.error = "Wi-Fi 状态读取失败：" + e.getClass().getSimpleName();
        }
        return w;
    }

    private void readWifiLinkProperties(WifiData w, LinkProperties lp) {
        if (lp == null) return;
        w.interfaceName = lp.getInterfaceName();
        w.mtu = lp.getMtu();
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress addr = la.getAddress();
            if (addr instanceof Inet4Address) {
                w.ipv4 = addr.getHostAddress();
            } else if (!addr.isLinkLocalAddress() && (w.ipv6 == null || w.ipv6.isEmpty())) {
                w.ipv6 = addr.getHostAddress();
            }
        }
        List<String> dns = new ArrayList<>();
        for (InetAddress addr : lp.getDnsServers()) {
            if (!addr.isLinkLocalAddress()) dns.add(addr.getHostAddress());
        }
        w.dns = join(dns, 2);
        for (RouteInfo route : lp.getRoutes()) {
            if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
                w.gateway = route.getGateway().getHostAddress();
                break;
            }
        }
    }

    public boolean startWifiScan() {
        if (wifiManager == null || !hasFineLocation(context) || !isLocationEnabled()) return false;
        try {
            return wifiManager.startScan();
        } catch (Exception e) {
            return false;
        }
    }

    public List<WifiAp> scanResults() {
        List<WifiAp> result = new ArrayList<>();
        if (wifiManager == null || !hasFineLocation(context) || !hasNearbyWifiPermission(context)) return result;
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null) return result;
            Map<String, WifiAp> byBssid = new LinkedHashMap<>();
            for (ScanResult r : results) {
                String bssid = r.BSSID == null ? "" : r.BSSID;
                int ch = ChannelSpectrumView.channelForFrequency(r.frequency);
                String ssid = r.SSID == null || r.SSID.isEmpty() ? "(隐藏网络)" : r.SSID;
                WifiAp ap = new WifiAp(ssid, bssid, r.level, r.frequency, ch,
                        widthMhz(r.channelWidth), securityName(r), r.capabilities == null ? "" : r.capabilities);
                if (Build.VERSION.SDK_INT >= 30) ap.standard = standardName(r.getWifiStandard());
                WifiAp prior = byBssid.get(bssid);
                if (prior == null || ap.rssi > prior.rssi) byBssid.put(bssid, ap);
            }
            result.addAll(byBssid.values());
            result.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {}
        return result;
    }

    private ScanResult findScanResult(String bssid) {
        if (bssid == null || !hasFineLocation(context) || !hasNearbyWifiPermission(context)) return null;
        try {
            List<ScanResult> list = wifiManager.getScanResults();
            if (list == null) return null;
            for (ScanResult r : list) {
                if (bssid.equalsIgnoreCase(r.BSSID)) return r;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Reads the default modem radio even when no active SIM is present.
     * Public Android APIs usually expose only service state / stored SIM records
     * in this state; any serving/neighbour cells the modem does report are kept.
     */
    private RadioInfo readRadio(Snapshot s) {
        RadioInfo r = new RadioInfo();
        r.slot = 0;
        r.subId = -1;
        r.carrier = "本机射频";
        if (telephonyManager == null) {
            r.error = "本机没有电话/射频服务";
            return r;
        }
        try {
            r.phoneType = phoneTypeName(telephonyManager.getPhoneType());
            r.simStateLabel = simStateName(telephonyManager.getSimState());
            if (Build.VERSION.SDK_INT >= 30) {
                try { r.supportedModemCount = telephonyManager.getSupportedModemCount(); } catch (Exception ignored) {}
                try { r.activeModemCount = telephonyManager.getActiveModemCount(); } catch (Exception ignored) {}
            }
            try { r.operatorName = str(telephonyManager.getNetworkOperatorName()); } catch (Exception ignored) {}
            try { r.operator = r.operatorName; } catch (Exception ignored) {}
            try { r.simOperator = str(telephonyManager.getSimOperatorName()); } catch (Exception ignored) {}
            try { r.mccMnc = str(telephonyManager.getNetworkOperator()); } catch (Exception ignored) {}
            if (r.mccMnc.isEmpty()) {
                try { r.mccMnc = str(telephonyManager.getSimOperator()); } catch (Exception ignored) {}
            }
            try { r.networkCountry = str(telephonyManager.getNetworkCountryIso()); } catch (Exception ignored) {}
            try { r.roaming = telephonyManager.isNetworkRoaming(); } catch (Exception ignored) {}
            try { r.selectionMode = selectionModeName(telephonyManager.getNetworkSelectionMode()); } catch (Exception ignored) {}
            try { r.dataState = dataStateName(telephonyManager.getDataState()); } catch (Exception ignored) {}
            try {
                r.dataEnabledKnown = true;
                r.dataEnabled = telephonyManager.isDataEnabled();
            } catch (Exception ignored) {}
            int dataType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            int voiceType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            try { dataType = telephonyManager.getDataNetworkType(); } catch (Exception ignored) {}
            try { voiceType = telephonyManager.getVoiceNetworkType(); } catch (Exception ignored) {}
            ServiceState ss = null;
            try { ss = telephonyManager.getServiceState(); } catch (Exception ignored) {}
            if (ss != null) {
                r.voiceService = serviceStateName(ss.getState());
                r.dataService = "无服务";
                r.roaming = ss.getRoaming();
                String op = ss.getOperatorAlphaLong();
                if (op != null && !op.isEmpty()) r.operatorName = op;
                if (op != null && !op.isEmpty()) r.operator = op;
                try {
                    for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
                        if (nri == null) continue;
                        boolean registered = nri.isNetworkRegistered();
                        String stateText = registered ? "已注册"
                                : nri.isNetworkSearching() ? "搜索中" : "无服务";
                        if (nri.getDomain() == NetworkRegistrationInfo.DOMAIN_PS) {
                            r.dataService = stateText;
                            if (dataType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                                dataType = nri.getAccessNetworkTechnology();
                            }
                        } else if (nri.getDomain() == NetworkRegistrationInfo.DOMAIN_CS) {
                            r.voiceService = stateText;
                            if (voiceType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                                voiceType = nri.getAccessNetworkTechnology();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            int shownType = dataType != TelephonyManager.NETWORK_TYPE_UNKNOWN ? dataType : voiceType;
            r.networkTypeCode = shownType;
            r.networkType = networkTypeName(shownType);
            try {
                SignalStrength strength = telephonyManager.getSignalStrength();
                if (strength != null) readSignal(r, strength);
            } catch (Exception ignored) {}
            try {
                List<CellInfo> cells = telephonyManager.getAllCellInfo();
                if (cells != null && !cells.isEmpty()) {
                    readCells(r, cells);
                } else if (!isLocationEnabled()) {
                    r.cellsNote = "定位服务关闭，Android 不会返回小区信息。";
                } else {
                    r.cellsNote = "无活动 SIM 时 Android/基带未向应用返回服务小区或邻区。";
                }
            } catch (Exception e) {
                r.cellsNote = "小区信息读取受限：" + e.getClass().getSimpleName();
            }
            if (r.signalDbm == Integer.MIN_VALUE && r.metrics.isEmpty()) {
                r.signalNote = "无活动 SIM / 未驻留网络，系统未提供可用信号强度读数。";
            }
            if (r.voiceService == null || r.voiceService.isEmpty()) r.voiceService = "—";
            if (r.dataService == null || r.dataService.isEmpty()) r.dataService = "—";
            if (hasPhonePermission(context) && subscriptionManager != null) {
                try {
                    List<SubscriptionInfo> accessible = subscriptionManager.getAccessibleSubscriptionInfoList();
                    if (accessible != null) {
                        for (SubscriptionInfo info : accessible) {
                            String name = safeCarrier(info);
                            String code = joinNonEmpty(info.getMccString(), info.getMncString(), "");
                            String item = name + (code.isEmpty() ? "" : " · " + code);
                            if (!r.accessibleCarriers.contains(item)) r.accessibleCarriers.add(item);
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            r.error = "射频状态读取失败：" + e.getClass().getSimpleName();
        }
        return r;
    }

    private void readSims(Snapshot s) {
        s.simState = simStateName(telephonyManager == null ? TelephonyManager.SIM_STATE_UNKNOWN
                : telephonyManager.getSimState());
        if (subscriptionManager == null) {
            s.simError = "本机没有电话服务";
            return;
        }
        if (!hasPhonePermission(context)) {
            s.simError = "需要电话权限才能读取 SIM 卡与信号";
            return;
        }
        List<SubscriptionInfo> infoList = null;
        try {
            infoList = subscriptionManager.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            s.simError = "系统拒绝读取 SIM 卡信息";
        } catch (Exception e) {
            s.simError = "SIM 卡信息读取失败";
        }
        if (infoList == null) infoList = new ArrayList<>();
        if (infoList.isEmpty()) {
            s.simError = "未检测到活动 SIM 卡（卡槽为空、卡已停用或未注册）";
        }
        int defaultDataSub = SubscriptionManager.getDefaultDataSubscriptionId();
        for (SubscriptionInfo info : infoList) {
            try {
                s.sims.add(readSim(info, info.getSubscriptionId() == defaultDataSub));
            } catch (Exception e) {
                CellSim broken = new CellSim();
                broken.slot = info.getSimSlotIndex() + 1;
                broken.carrier = safeCarrier(info);
                broken.error = "读取失败：" + e.getClass().getSimpleName();
                s.sims.add(broken);
            }
        }
        s.sims.sort(Comparator.comparingInt(a -> a.slot));
    }

    private CellSim readSim(SubscriptionInfo info, boolean defaultData) {
        CellSim sim = new CellSim();
        sim.slot = info.getSimSlotIndex() + 1;
        sim.subId = info.getSubscriptionId();
        sim.carrier = safeCarrier(info);
        sim.displayName = info.getDisplayName() == null ? "" : info.getDisplayName().toString();
        sim.mccMnc = joinNonEmpty(info.getMccString(), info.getMncString(), "");
        sim.defaultData = defaultData;
        TelephonyManager tm = telephonyManager == null ? null
                : telephonyManager.createForSubscriptionId(sim.subId);
        if (tm == null) {
            sim.error = "无法访问该 SIM 卡";
            return sim;
        }
        try {
            ServiceState ss = tm.getServiceState();
            if (ss != null) {
                sim.voiceService = serviceStateName(ss.getState());
                sim.dataService = "未知";
                sim.roaming = ss.getRoaming();
                sim.operator = ss.getOperatorAlphaLong() == null ? tm.getNetworkOperatorName()
                        : ss.getOperatorAlphaLong();
                sim.mccMnc = str(tm.getNetworkOperator());
                if (sim.mccMnc.isEmpty()) sim.mccMnc = str(tm.getSimOperator());
                sim.operatorName = tm.getNetworkOperatorName();
            }
            int dataType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            int voiceType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            try { dataType = tm.getDataNetworkType(); } catch (Exception ignored) {}
            try { voiceType = tm.getVoiceNetworkType(); } catch (Exception ignored) {}
            if (ss != null) {
                try {
                    for (NetworkRegistrationInfo nri : ss.getNetworkRegistrationInfoList()) {
                        if (nri == null) continue;
                        boolean registered = nri.isNetworkRegistered();
                        String state = registered ? "已注册"
                                : nri.isNetworkSearching() ? "搜索中" : "无服务";
                        if (nri.getDomain() == NetworkRegistrationInfo.DOMAIN_PS) {
                            sim.dataService = state;
                            if (dataType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                                dataType = nri.getAccessNetworkTechnology();
                            }
                        } else if (nri.getDomain() == NetworkRegistrationInfo.DOMAIN_CS) {
                            sim.voiceService = state;
                            if (voiceType == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                                voiceType = nri.getAccessNetworkTechnology();
                            }
                        }
                        if (registered && nri.isNetworkRoaming()) sim.roaming = true;
                    }
                } catch (Exception ignored) {}
            }
            sim.simOperator = str(tm.getSimOperatorName());
            int shownType = dataType != TelephonyManager.NETWORK_TYPE_UNKNOWN ? dataType : voiceType;
            sim.networkType = networkTypeName(shownType);
            sim.networkTypeCode = shownType;
            try { sim.dataState = dataStateName(tm.getDataState()); } catch (Exception ignored) {}
            try {
                sim.dataEnabledKnown = true;
                sim.dataEnabled = tm.isDataEnabled();
            } catch (Exception ignored) {}
            SignalStrength strength = tm.getSignalStrength();
            if (strength != null) readSignal(sim, strength);
            List<CellInfo> allCells = null;
            try { allCells = tm.getAllCellInfo(); } catch (Exception ignored) {}
            if (allCells == null || allCells.isEmpty()) {
                if (!isLocationEnabled()) sim.cellsNote = "开启定位服务后可读取邻区信息（系统限制）";
                else sim.cellsNote = "系统暂未返回小区信息";
            } else {
                readCells(sim, allCells);
            }
            if (sim.signalDbm == Integer.MIN_VALUE && sim.metrics.isEmpty()) {
                sim.signalNote = "系统未返回可用的信号强度（可能无服务或此卡不支持）";
            }
            if (sim.networkTypeCode == TelephonyManager.NETWORK_TYPE_LTE && sim.hasNrSignal) {
                sim.networkType = "5G NSA";
                sim.fiveG = true;
            } else if (sim.networkTypeCode == TelephonyManager.NETWORK_TYPE_NR) {
                sim.fiveG = true;
            }
        } catch (SecurityException e) {
            sim.error = "缺少权限，无法读取该卡";
        } catch (Exception e) {
            sim.error = "读取失败：" + e.getClass().getSimpleName();
        }
        return sim;
    }

    private void readSignal(CellSim sim, SignalStrength ss) {
        List<CellSignalStrength> list = ss.getCellSignalStrengths();
        CellSignalStrength primary = null;
        int bestPriority = -1;
        for (CellSignalStrength css : list) {
            int priority = signalPriority(css);
            if (priority > bestPriority) {
                bestPriority = priority;
                primary = css;
            }
            if (css instanceof CellSignalStrengthNr) {
                CellSignalStrengthNr nr = (CellSignalStrengthNr) css;
                addMetric(sim, "SS-RSRP", nr.getSsRsrp(), "dBm");
                addMetric(sim, "SS-RSRQ", nr.getSsRsrq(), "dB");
                addMetric(sim, "SS-SINR", nr.getSsSinr(), "dB");
                if (valid(nr.getCsiRsrp())) addMetric(sim, "CSI-RSRP", nr.getCsiRsrp(), "dBm");
                if (valid(nr.getSsRsrp())) {
                    sim.hasNrSignal = true;
                    sim.nrRsrp = nr.getSsRsrp();
                }
            } else if (css instanceof CellSignalStrengthLte) {
                CellSignalStrengthLte lte = (CellSignalStrengthLte) css;
                addMetric(sim, "RSRP", lte.getRsrp(), "dBm");
                addMetric(sim, "RSRQ", lte.getRsrq(), "dB");
                addMetric(sim, "SINR", lte.getRssnr(), "dB");
                if (valid(lte.getRssi())) addMetric(sim, "RSSI", lte.getRssi(), "dBm");
                if (valid(lte.getCqi())) addMetric(sim, "CQI", lte.getCqi(), "");
            } else if (css instanceof CellSignalStrengthWcdma) {
                CellSignalStrengthWcdma w = (CellSignalStrengthWcdma) css;
                addMetric(sim, "RSCP", w.getDbm(), "dBm");
                addMetric(sim, "Ec/No", w.getEcNo(), "dB");
            } else if (css instanceof CellSignalStrengthGsm) {
                CellSignalStrengthGsm g = (CellSignalStrengthGsm) css;
                addMetric(sim, "RSSI", g.getRssi(), "dBm");
            }
        }
        if (primary != null) {
            sim.signalLevel = Math.max(0, Math.min(4, primary.getLevel()));
            if (primary instanceof CellSignalStrengthNr) {
                CellSignalStrengthNr nr = (CellSignalStrengthNr) primary;
                sim.signalDbm = valid(nr.getSsRsrp()) ? nr.getSsRsrp() : nr.getDbm();
                sim.tech = "5G NR";
            } else if (primary instanceof CellSignalStrengthLte) {
                CellSignalStrengthLte lte = (CellSignalStrengthLte) primary;
                sim.signalDbm = lte.getRsrp();
                sim.tech = "LTE";
            } else if (primary instanceof CellSignalStrengthWcdma) {
                sim.signalDbm = primary.getDbm();
                sim.tech = "WCDMA";
            } else if (primary instanceof CellSignalStrengthGsm) {
                sim.signalDbm = ((CellSignalStrengthGsm) primary).getRssi();
                sim.tech = "GSM";
            } else {
                sim.signalDbm = primary.getDbm();
                sim.tech = "蜂窝";
            }
            if (!valid(sim.signalDbm)) sim.signalDbm = Integer.MIN_VALUE;
        }
    }

    private int signalPriority(CellSignalStrength css) {
        if (css instanceof CellSignalStrengthNr) return 5;
        if (css instanceof CellSignalStrengthLte) return 4;
        if (css instanceof CellSignalStrengthWcdma) return 3;
        if (css instanceof CellSignalStrengthGsm) return 2;
        return 1;
    }

    private void addMetric(CellSim sim, String name, int value, String unit) {
        if (!valid(value)) return;
        sim.metrics.add(new SignalMetric(name, value, unit));
    }

    private void readCells(CellSim sim, List<CellInfo> cells) {
        for (CellInfo ci : cells) {
            try {
                CellItem item = new CellItem();
                item.registered = ci.isRegistered();
                item.dbm = ci.getCellSignalStrength() == null ? Integer.MIN_VALUE
                        : ci.getCellSignalStrength().getDbm();
                item.level = ci.getCellSignalStrength() == null ? 0
                        : ci.getCellSignalStrength().getLevel();
                if (ci instanceof CellInfoNr) {
                    CellInfoNr nrCi = (CellInfoNr) ci;
                    CellIdentityNr id = (CellIdentityNr) nrCi.getCellIdentity();
                    item.tech = "5G NR";
                    item.pci = id.getPci();
                    item.tac = id.getTac();
                    item.cellId = id.getNci();
                    item.band = nrBand(id);
                    item.plmn = plmnOf(id.getMccString(), id.getMncString());
                    if (Build.VERSION.SDK_INT >= 30) {
                        try { item.arfcn = id.getNrarfcn(); } catch (Exception ignored) {}
                        try {
                            int[] bands = id.getBands();
                            if ((bands == null || bands.length == 0) && item.arfcn > 0) {
                                item.band = "NR-ARFCN " + item.arfcn;
                            }
                        } catch (Exception ignored) {}
                    }
                    CellSignalStrength css = nrCi.getCellSignalStrength();
                    if (css instanceof CellSignalStrengthNr) {
                        CellSignalStrengthNr nr = (CellSignalStrengthNr) css;
                        item.dbm = valid(nr.getSsRsrp()) ? nr.getSsRsrp() : nr.getDbm();
                        item.extra = "SINR " + (valid(nr.getSsSinr()) ? nr.getSsSinr() + " dB" : "—");
                    }
                } else if (ci instanceof CellInfoLte) {
                    CellInfoLte lteCi = (CellInfoLte) ci;
                    CellIdentityLte id = lteCi.getCellIdentity();
                    item.tech = "LTE";
                    item.pci = id.getPci();
                    item.tac = id.getTac();
                    item.cellId = id.getCi() & 0xFFFFFFFFL;
                    item.band = lteBand(id);
                    item.plmn = plmnOf(id.getMccString(), id.getMncString());
                    if (Build.VERSION.SDK_INT >= 30) {
                        try { item.arfcn = id.getEarfcn(); } catch (Exception ignored) {}
                        try { item.bandwidthKhz = id.getBandwidth(); } catch (Exception ignored) {}
                    }
                    CellSignalStrengthLte css = lteCi.getCellSignalStrength();
                    if (css != null) {
                        item.dbm = css.getRsrp();
                        item.extra = "SINR " + (valid(css.getRssnr()) ? css.getRssnr() + " dB" : "—");
                    }
                } else if (ci instanceof CellInfoWcdma) {
                    CellInfoWcdma wc = (CellInfoWcdma) ci;
                    CellIdentityWcdma id = wc.getCellIdentity();
                    item.tech = "WCDMA";
                    item.pci = id.getPsc();
                    item.tac = id.getLac();
                    item.cellId = id.getCid();
                    item.arfcn = id.getUarfcn();
                    item.plmn = plmnOf(id.getMccString(), id.getMncString());
                } else if (ci instanceof CellInfoGsm) {
                    CellInfoGsm gsm = (CellInfoGsm) ci;
                    CellIdentityGsm id = gsm.getCellIdentity();
                    item.tech = "GSM";
                    item.pci = id.getPsc();
                    item.tac = id.getLac();
                    item.cellId = id.getCid();
                    item.arfcn = id.getArfcn();
                    item.plmn = plmnOf(id.getMccString(), id.getMncString());
                } else {
                    item.tech = "细胞";
                }
                if (item.pci == Integer.MAX_VALUE || item.pci < 0) item.pci = 0;
                if (item.tac == Integer.MAX_VALUE || item.tac < 0) item.tac = 0;
                if (item.cellId == Integer.MAX_VALUE || item.cellId == 0xFFFFFFFFL) item.cellId = 0;
                if (item.arfcn == Integer.MAX_VALUE || item.arfcn < 0) item.arfcn = 0;
                item.channelHint = item.arfcn;
                if (!valid(item.dbm)) item.dbm = Integer.MIN_VALUE;
                sim.cells.add(item);
            } catch (Exception ignored) {}
        }
        sim.cells.sort((a, b) -> {
            if (a.registered != b.registered) return a.registered ? -1 : 1;
            return Integer.compare(b.dbm, a.dbm);
        });
        if (sim.cells.size() > 12) sim.cells = new ArrayList<>(sim.cells.subList(0, 12));
    }

    private String nrBand(CellIdentityNr id) {
        if (Build.VERSION.SDK_INT < 30) return "n—";
        try {
            int[] bands = id.getBands();
            if (bands == null || bands.length == 0) return "n—";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(2, bands.length); i++) {
                if (i > 0) sb.append("/");
                sb.append("n").append(bands[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "n—";
        }
    }

    private String lteBand(CellIdentityLte id) {
        if (Build.VERSION.SDK_INT < 30) return "B—";
        try {
            int[] bands = id.getBands();
            if (bands == null || bands.length == 0) return "B—";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(2, bands.length); i++) {
                if (i > 0) sb.append("/");
                sb.append("B").append(bands[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "B—";
        }
    }

    private void readTraffic(Snapshot s) {
        s.totalRx = TrafficStats.getTotalRxBytes();
        s.totalTx = TrafficStats.getTotalTxBytes();
        s.mobileRx = TrafficStats.getMobileRxBytes();
        s.mobileTx = TrafficStats.getMobileTxBytes();
        s.appRx = TrafficStats.getUidRxBytes(android.os.Process.myUid());
        s.appTx = TrafficStats.getUidTxBytes(android.os.Process.myUid());
        if (s.totalRx < 0) s.totalRx = 0;
        if (s.totalTx < 0) s.totalTx = 0;
        if (s.mobileRx < 0) s.mobileRx = 0;
        if (s.mobileTx < 0) s.mobileTx = 0;
        if (s.appRx < 0) s.appRx = 0;
        if (s.appTx < 0) s.appTx = 0;
    }

    private String safeCarrier(SubscriptionInfo info) {
        try {
            CharSequence c = info.getCarrierName();
            String s = c == null ? "" : c.toString().trim();
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) {}
        try {
            String display = info.getDisplayName() == null ? "" : info.getDisplayName().toString().trim();
            if (!display.isEmpty()) return display;
        } catch (Exception ignored) {}
        return "SIM " + (info.getSimSlotIndex() + 1);
    }

    private static String plmnOf(String mcc, String mnc) {
        if (mcc == null || mcc.isEmpty() || mnc == null || mnc.isEmpty()) return "";
        return mcc + mnc;
    }

    public static String carrierNameForPlmn(String plmn) {
        if (plmn == null) return "";
        switch (plmn) {
            case "46000": case "46002": case "46004": case "46007": case "46008":
                return "中国移动";
            case "46001": case "46006": case "46009":
                return "中国联通";
            case "46003": case "46005": case "46011":
                return "中国电信";
            case "46015":
                return "中国广电";
            default:
                return plmn.isEmpty() ? "" : "PLMN " + plmn;
        }
    }

    private static boolean valid(int v) {
        return v != Integer.MAX_VALUE && v != Integer.MIN_VALUE && v > -500 && v < 500;
    }

    private static String join(List<String> items, int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(count, items.size()); i++) {
            if (i > 0) b.append(" · ");
            b.append(items.get(i));
        }
        return b.toString();
    }

    private static String joinNonEmpty(String... values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            if (b.length() > 0) b.append(" ");
            b.append(v);
        }
        return b.toString();
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    public static String cleanSsid(String raw) {
        if (raw == null) return "未知网络";
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty() || s.contains("<unknown ssid>")) return "未知网络";
        return s;
    }

    public static int wifiLevel(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -65) return 3;
        if (rssi >= -75) return 2;
        if (rssi >= -85) return 1;
        return 0;
    }

    public static String bandName(int freq) {
        if (freq >= 2400 && freq < 2500) return "2.4 GHz";
        if (freq >= 4900 && freq < 5925) return "5 GHz";
        if (freq >= 5925 && freq <= 7125) return "6 GHz";
        if (freq >= 57_000) return "60 GHz";
        return freq > 0 ? freq + " MHz" : "—";
    }

    public static String standardName(int standard) {
        switch (standard) {
            case ScanResult.WIFI_STANDARD_LEGACY: return "Wi-Fi 4 (802.11g/n?)";
            case ScanResult.WIFI_STANDARD_11N: return "Wi-Fi 4 (802.11n)";
            case ScanResult.WIFI_STANDARD_11AC: return "Wi-Fi 5 (802.11ac)";
            case ScanResult.WIFI_STANDARD_11AX: return "Wi-Fi 6 (802.11ax)";
            case ScanResult.WIFI_STANDARD_11AD: return "WiGig (802.11ad)";
            case ScanResult.WIFI_STANDARD_11BE: return "Wi-Fi 7 (802.11be)";
            default: return "未知协议";
        }
    }

    public static String securityTypeName(int type) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (type == WifiInfo.SECURITY_TYPE_OPEN) return "开放网络";
            if (type == WifiInfo.SECURITY_TYPE_OWE) return "OWE 加密";
            if (type == WifiInfo.SECURITY_TYPE_WEP) return "WEP";
            if (type == WifiInfo.SECURITY_TYPE_PSK) return "WPA2/WPA3-PSK";
            if (type == WifiInfo.SECURITY_TYPE_SAE) return "WPA3-SAE";
            if (type == WifiInfo.SECURITY_TYPE_EAP) return "WPA2 企业级";
            if (type == WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE) return "WPA3 企业级";
            if (type == WifiInfo.SECURITY_TYPE_WAPI_PSK) return "WAPI-PSK";
            if (type == WifiInfo.SECURITY_TYPE_DPP) return "Wi-Fi DPP";
        }
        return "未知加密";
    }

    public static String securityName(ScanResult r) {
        String c = r.capabilities == null ? "" : r.capabilities;
        String upper = c.toUpperCase(Locale.US);
        if (upper.contains("OWE")) return "OWE 加密";
        if (upper.contains("SAE") && upper.contains("WPA2")) return "WPA2/WPA3-SAE";
        if (upper.contains("SAE")) return "WPA3-SAE";
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                int[] types = r.getSecurityTypes();
                if (types != null && types.length > 0) {
                    for (int t : types) {
                        String name = securityTypeName(t);
                        if (!"未知加密".equals(name)) return name;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (upper.contains("WPA2") && upper.contains("802.1X")) return "WPA2 企业级";
        if (upper.contains("WPA2")) return "WPA2";
        if (upper.contains("WPA") && upper.contains("802.1X")) return "WPA 企业级";
        if (upper.contains("WPA")) return "WPA";
        if (upper.contains("WEP")) return "WEP";
        if (upper.contains("EAP")) return "EAP";
        return "开放网络";
    }

    public static String widthName(int widthMhz) {
        if (widthMhz <= 0) return "—";
        return widthMhz + " MHz";
    }

    public static int widthMhz(int channelWidth) {
        switch (channelWidth) {
            case ScanResult.CHANNEL_WIDTH_20MHZ: return 20;
            case ScanResult.CHANNEL_WIDTH_40MHZ: return 40;
            case ScanResult.CHANNEL_WIDTH_80MHZ: return 80;
            case ScanResult.CHANNEL_WIDTH_160MHZ: return 160;
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ: return 160;
            case ScanResult.CHANNEL_WIDTH_320MHZ: return 320;
            default: return 20;
        }
    }

    public static String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G NR";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G HSPA+";
            case TelephonyManager.NETWORK_TYPE_HSDPA: return "3G HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA: return "3G HSUPA";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G UMTS";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD: return "3G EVDO";
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "2G CDMA";
            case TelephonyManager.NETWORK_TYPE_GPRS: return "2G GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G EDGE";
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA: return "3G TD-SCDMA";
            case TelephonyManager.NETWORK_TYPE_IWLAN: return "VoWiFi";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "未知";
            default: return "网络类型 " + type;
        }
    }

    public static String phoneTypeName(int type) {
        switch (type) {
            case TelephonyManager.PHONE_TYPE_GSM: return "GSM / 5G";
            case TelephonyManager.PHONE_TYPE_CDMA: return "CDMA";
            case TelephonyManager.PHONE_TYPE_SIP: return "SIP";
            case TelephonyManager.PHONE_TYPE_NONE: return "无射频";
            default: return "未知类型";
        }
    }

    public static String selectionModeName(int mode) {
        switch (mode) {
            case 1: return "自动选网";
            case 0: return "手动选网";
            default: return "未知";
        }
    }

    public static String serviceStateName(int state) {
        switch (state) {
            case ServiceState.STATE_IN_SERVICE: return "已注册";
            case ServiceState.STATE_OUT_OF_SERVICE: return "无服务";
            case ServiceState.STATE_EMERGENCY_ONLY: return "仅限紧急呼叫";
            case ServiceState.STATE_POWER_OFF: return "射频关闭";
            default: return "未知";
        }
    }

    public static String dataStateName(int state) {
        switch (state) {
            case TelephonyManager.DATA_CONNECTED: return "数据已连接";
            case TelephonyManager.DATA_CONNECTING: return "正在连接";
            case TelephonyManager.DATA_SUSPENDED: return "数据挂起";
            case TelephonyManager.DATA_DISCONNECTED: return "数据断开";
            case TelephonyManager.DATA_DISCONNECTING: return "正在断开";
            default: return "数据未知";
        }
    }

    public static String simStateName(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_ABSENT: return "未插卡";
            case TelephonyManager.SIM_STATE_READY: return "已就绪";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "需要 PIN";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "需要 PUK";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "网络锁定";
            case TelephonyManager.SIM_STATE_NOT_READY: return "未就绪";
            case TelephonyManager.SIM_STATE_PERM_DISABLED: return "永久停用";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR: return "卡 I/O 错误";
            case TelephonyManager.SIM_STATE_CARD_RESTRICTED: return "卡受限";
            default: return "未知";
        }
    }

    public static String formatMbps(double mbps) {
        if (!Double.isFinite(mbps)) return "—";
        if (mbps >= 100) return String.format(Locale.US, "%.0f", mbps);
        if (mbps >= 10) return String.format(Locale.US, "%.1f", mbps);
        return String.format(Locale.US, "%.2f", mbps);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1024) return String.format(Locale.US, "%.2f GB", gb);
        return String.format(Locale.US, "%.2f TB", gb / 1024.0);
    }

    public static String formatDuration(long millis) {
        long s = millis / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d时%02d分", h, m);
        if (m > 0) return String.format(Locale.US, "%d分%02d秒", m, sec);
        return sec + "秒";
    }

    public static class Snapshot {
        public String transport = "未知";
        public boolean online;
        public boolean validated;
        public boolean vpn;
        public boolean metered;
        public boolean airplaneMode;
        public boolean locationEnabled;
        public boolean fineLocation;
        public boolean phonePermission;
        public boolean nearbyWifiPermission;
        public long linkDownKbps;
        public long linkUpKbps;
        public String ipv4;
        public String ipv6;
        public String gateway;
        public String dns;
        public String interfaceName;
        public int mtu;
        public WifiData wifi = new WifiData();
        public List<CellSim> sims = new ArrayList<>();
        public RadioInfo radio = new RadioInfo();
        public String simState = "未知";
        public String simError;
        public long totalRx;
        public long totalTx;
        public long mobileRx;
        public long mobileTx;
        public long appRx;
        public long appTx;
    }

    public static class WifiData {
        public boolean enabled;
        public boolean connected;
        public boolean ssidKnown;
        public String ssid = "未连接";
        public String bssid;
        public String security;
        public String standard;
        public String band;
        public String securityNote;
        public String error;
        public int rssi = -127;
        public int level;
        public int frequency;
        public int channel;
        public int widthMhz;
        public int linkSpeedMbps = -1;
        public int rxLinkSpeedMbps = -1;
        public int txLinkSpeedMbps = -1;
        public int maxTxLinkSpeedMbps = -1;
        public int networkId = -1;
        public String mac;
        public String ipv4;
        public String ipv6;
        public String gateway;
        public String dns;
        public String interfaceName;
        public int mtu;
    }

    public static class WifiAp {
        public final String ssid;
        public final String bssid;
        public final int rssi;
        public final int frequency;
        public final int channel;
        public final int widthMhz;
        public final String security;
        public final String capabilities;
        public String standard;
        public boolean connected;

        public WifiAp(String ssid, String bssid, int rssi, int frequency, int channel,
                      int widthMhz, String security, String capabilities) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.rssi = rssi;
            this.frequency = frequency;
            this.channel = channel;
            this.widthMhz = widthMhz;
            this.security = security;
            this.capabilities = capabilities;
        }
    }

    public static class CellSim {
        public int slot;
        public int subId;
        public String carrier = "SIM 卡";
        public String displayName = "";
        public String operator = "";
        public String operatorName = "";
        public String simOperator = "";
        public String mccMnc = "";
        public String voiceService = "未知";
        public String dataService = "未知";
        public String networkType = "未知";
        public int networkTypeCode;
        public String dataState = "—";
        public boolean dataEnabledKnown;
        public boolean dataEnabled;
        public boolean roaming;
        public boolean defaultData;
        public boolean fiveG;
        public boolean hasNrSignal;
        public int nrRsrp;
        public int signalDbm = Integer.MIN_VALUE;
        public int signalLevel;
        public String tech = "蜂窝";
        public String error;
        public String signalNote;
        public String cellsNote;
        public final List<SignalMetric> metrics = new ArrayList<>();
        public List<CellItem> cells = new ArrayList<>();
    }

    public static class RadioInfo extends CellSim {
        public String phoneType = "—";
        public String selectionMode = "—";
        public String networkCountry = "";
        public String simStateLabel = "—";
        public int supportedModemCount;
        public int activeModemCount;
        public final List<String> accessibleCarriers = new ArrayList<>();
    }

    public static class SignalMetric {
        public final String name;
        public final int value;
        public final String unit;
        public SignalMetric(String name, int value, String unit) {
            this.name = name;
            this.value = value;
            this.unit = unit;
        }
        public String text() {
            return value + (unit == null || unit.isEmpty() ? "" : " " + unit);
        }
    }

    public static class CellItem {
        public String tech = "蜂窝";
        public boolean registered;
        public int pci;
        public int tac;
        public long cellId;
        public int arfcn;
        public int bandwidthKhz;
        public String band = "—";
        public String plmn = "";
        public int dbm = Integer.MIN_VALUE;
        public int level;
        public String extra;
        public int channelHint;
        public boolean hasBand() { return band != null && !"—".equals(band) && !"n—".equals(band) && !"B—".equals(band); }
    }
}
