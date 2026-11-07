package com.netpulse.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs latency, DNS, reachability and signal checks and produces a 100-point report. */
public final class Diagnostics {
    public static final int PASS = 0;
    public static final int WARN = 1;
    public static final int FAIL = 2;

    private Diagnostics() {}

    public static class Check {
        public final String name;
        public final String detail;
        public final int status;
        public final int weight;
        public final int earned;

        public Check(String name, String detail, int status, int weight, int earned) {
            this.name = name;
            this.detail = detail;
            this.status = status;
            this.weight = weight;
            this.earned = earned;
        }
    }

    public static class PingResult {
        public String host = "";
        public String method = "ICMP";
        public int sent;
        public int received;
        public double minMs;
        public double avgMs;
        public double maxMs;
        public double jitterMs;
        public String error;
        public boolean ok;

        public double lossPercent() {
            if (sent <= 0) return 100;
            return Math.max(0, (sent - received) * 100.0 / sent);
        }

        public String latencyText() {
            if (!ok) return "不可达";
            return String.format(Locale.US, "%.0f ms", avgMs);
        }

        public String detailText() {
            if (!ok) return error == null ? "测试失败" : error;
            return String.format(Locale.US, "%s · 平均 %.1f ms · 抖动 %.1f ms · 丢包 %.0f%%",
                    method, avgMs, jitterMs, lossPercent());
        }
    }

    public static class Report {
        public int score;
        public String grade = "—";
        public final List<Check> checks = new ArrayList<>();
        public final List<String> suggestions = new ArrayList<>();
        public PingResult gatewayPing;
        public PingResult publicPing;
        public HttpResult http;
        public double dnsMs;
        public String dnsDetail = "";
        public PublicInfoService.PublicInfo publicInfo;
        public long timestamp = System.currentTimeMillis();
        public String summary = "";
    }

    public static class HttpResult {
        public boolean ok;
        public int code;
        public long latencyMs;
        public String endpoint = "";
        public String error = "";
    }

    public static Report run(Context context, NetProbe.Snapshot snapshot,
                             PublicInfoService.PublicInfo knownPublic) {
        Report report = new Report();
        report.http = httpReachable();
        report.publicInfo = knownPublic;
        if (report.publicInfo == null || report.publicInfo.ip == null || report.publicInfo.ip.isEmpty()) {
            report.publicInfo = PublicInfoService.fetch();
        }

        Check internet = internetCheck(report.http);
        report.checks.add(internet);

        DnsProbe dns = dnsProbe();
        report.dnsMs = dns.bestMs;
        report.dnsDetail = dns.detail;
        int dnsStatus = dns.success == 2 ? PASS : dns.success == 1 ? WARN : FAIL;
        report.checks.add(new Check("DNS 解析", dns.detail, dnsStatus, 20,
                points(dnsStatus, 20)));

        String gateway = snapshot == null ? null
                : (snapshot.wifi != null && snapshot.wifi.gateway != null
                ? snapshot.wifi.gateway : snapshot.gateway);
        if (gateway == null || gateway.isEmpty()) {
            report.gatewayPing = new PingResult();
            report.gatewayPing.error = "未取得默认网关";
            report.checks.add(new Check("网关链路", "未取得默认网关，无法测试局域网链路",
                    WARN, 15, points(WARN, 15)));
        } else {
            report.gatewayPing = ping(gateway, 4, 1200);
            int status;
            if (!report.gatewayPing.ok) status = FAIL;
            else if (report.gatewayPing.avgMs > 80 || report.gatewayPing.lossPercent() > 0) status = WARN;
            else if (report.gatewayPing.avgMs > 20) status = PASS;
            else status = PASS;
            String detail = "网关 " + gateway + " · " + report.gatewayPing.detailText();
            report.checks.add(new Check("网关链路", detail, status, 15, points(status, 15)));
        }

        SignalProbe signal = signalProbe(snapshot);
        report.checks.add(new Check("信号质量", signal.detail, signal.status, 25,
                points(signal.status, 25)));

        report.publicPing = ping("223.5.5.5", 5, 1600);
        int jitterStatus;
        String jitterDetail;
        if (!report.publicPing.ok) {
            jitterStatus = WARN;
            jitterDetail = "公网延迟测试失败：" + (report.publicPing.error == null ? "网络不可达" : report.publicPing.error);
        } else {
            if (report.publicPing.jitterMs < 20 && report.publicPing.avgMs < 120) jitterStatus = PASS;
            else if (report.publicPing.jitterMs < 60 && report.publicPing.avgMs < 250) jitterStatus = WARN;
            else jitterStatus = WARN;
            jitterDetail = report.publicPing.detailText();
        }
        report.checks.add(new Check("延迟与抖动", jitterDetail, jitterStatus, 15,
                points(jitterStatus, 15)));

        int total = 0;
        for (Check c : report.checks) total += c.earned;
        report.score = total;
        report.grade = total >= 90 ? "优秀" : total >= 75 ? "良好" : total >= 55 ? "一般" : "较差";
        buildSuggestions(report, snapshot, signal);
        if (report.suggestions.isEmpty()) {
            report.suggestions.add("各项指标正常，可以放心使用当前网络。");
        }
        report.summary = summaryText(report, snapshot);
        return report;
    }

    private static Check internetCheck(HttpResult http) {
        if (http.ok) {
            return new Check("互联网连接",
                    String.format(Locale.US, "%s 可达 · HTTP %d · %d ms",
                            shortHost(http.endpoint), http.code, http.latencyMs),
                    PASS, 25, points(PASS, 25));
        }
        return new Check("互联网连接", "外部 HTTP 探测失败：" + http.error, FAIL, 25, 0);
    }

    private static int points(int status, int weight) {
        if (status == PASS) return weight;
        if (status == WARN) return Math.round(weight * 0.55f);
        return 0;
    }

    private static void buildSuggestions(Report r, NetProbe.Snapshot s, SignalProbe sig) {
        if (!r.http.ok) {
            r.suggestions.add("互联网不可达：先确认路由器 WAN 口和光猫指示灯，再检查是否欠费或需要登录认证页面。");
        }
        if (sig.status == FAIL) {
            if (s != null && s.wifi != null && s.wifi.connected) {
                r.suggestions.add("Wi-Fi 信号很弱：靠近路由器、减少墙体遮挡，或切换到 5 GHz / Wi-Fi 6 频段。");
            } else {
                r.suggestions.add("蜂窝信号很弱：尝试移动到窗边或高层位置，并确认是否支持当前运营商的 5G 频段。");
            }
        } else if (sig.status == WARN) {
            r.suggestions.add("信号一般：若经常掉线，可考虑调整路由器位置或使用 Wi-Fi 扩展器。");
        }
        if (r.publicPing != null && r.publicPing.ok && r.publicPing.jitterMs > 50) {
            r.suggestions.add("网络抖动偏高：游戏/视频会议可能卡顿，尽量使用有线或 5 GHz 低干扰信道。");
        }
        if (r.publicPing != null && r.publicPing.ok && r.publicPing.avgMs > 150) {
            r.suggestions.add("公网平均延迟偏高（" + Math.round(r.publicPing.avgMs)
                    + " ms）：实时游戏/会议可能受影响，可尝试更换路由器或联系运营商。");
        }
        if (r.publicPing != null && r.publicPing.ok && r.publicPing.lossPercent() > 0) {
            r.suggestions.add("公网探测出现丢包：检查 Wi-Fi 干扰、运营商线路或路由器负载。");
        }
        if (r.dnsDetail == null || r.dnsDetail.contains("失败")) {
            r.suggestions.add("DNS 解析不稳定：可在路由器或系统设置中改用 223.5.5.5 / 1.1.1.1 等公共 DNS。");
        }
        if (s != null && s.locationEnabled == false) {
            r.suggestions.add("定位服务处于关闭状态：Android 要求开启定位才能显示 Wi-Fi 名称、扫描列表和基站信息。");
        }
        if (s != null && (!s.phonePermission)) {
            r.suggestions.add("尚未授予电话权限：蜂窝页无法读取 SIM 卡和信号详情。");
        }
    }

    private static String summaryText(Report r, NetProbe.Snapshot s) {
        StringBuilder sb = new StringBuilder();
        if (s != null) {
            if (s.wifi != null && s.wifi.connected) {
                sb.append("Wi-Fi ").append(s.wifi.ssid);
                if (s.wifi.rssi > -120) sb.append("（").append(s.wifi.rssi).append(" dBm）");
            } else if (s.transport != null) {
                sb.append(s.transport);
            }
        }
        if (r.http.ok) {
            sb.append(" · 外网可用 · ").append(r.grade);
        } else {
            sb.append(" · 外网不可用");
        }
        return sb.toString();
    }

    private static String shortHost(String endpoint) {
        if (endpoint == null) return "探测地址";
        try {
            return new URL(endpoint).getHost();
        } catch (Exception e) {
            return endpoint;
        }
    }

    public static class DnsProbe {
        int success;
        double bestMs = -1;
        String detail = "";
    }

    private static DnsProbe dnsProbe() {
        DnsProbe p = new DnsProbe();
        String[] domains = {"www.baidu.com", "www.qq.com"};
        StringBuilder detail = new StringBuilder();
        int okCount = 0;
        for (String domain : domains) {
            long start = System.nanoTime();
            try {
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long elapsed = (System.nanoTime() - start) / 1_000_000L;
                okCount++;
                if (p.bestMs < 0 || elapsed < p.bestMs) p.bestMs = elapsed;
                if (detail.length() > 0) detail.append(" · ");
                detail.append(domain).append(" ").append(elapsed).append(" ms");
                if (addresses.length == 0) okCount--;
            } catch (Exception e) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(domain).append(" 失败");
            }
        }
        p.success = okCount >= 2 ? 2 : okCount == 1 ? 1 : 0;
        if (okCount == 0) {
            p.detail = "所有 DNS 查询失败，请检查 DNS 或网络连接";
        } else {
            p.detail = detail.toString();
        }
        return p;
    }

    public static class SignalProbe {
        String detail = "";
        int status = WARN;
    }

    private static SignalProbe signalProbe(NetProbe.Snapshot s) {
        SignalProbe p = new SignalProbe();
        if (s == null) {
            p.detail = "无网络快照";
            return p;
        }
        if (s.wifi != null && s.wifi.connected && s.wifi.rssi > -120) {
            int rssi = s.wifi.rssi;
            p.status = rssi >= -67 ? PASS : rssi >= -78 ? WARN : FAIL;
            p.detail = "Wi-Fi " + s.wifi.ssid + " · " + rssi + " dBm · " + Ui.wifiQuality(rssi);
            return p;
        }
        if (s.sims != null && !s.sims.isEmpty()) {
            NetProbe.CellSim best = null;
            for (NetProbe.CellSim sim : s.sims) {
                if (sim.signalDbm != Integer.MIN_VALUE
                        && (best == null || sim.signalDbm > best.signalDbm)) best = sim;
            }
            if (best != null) {
                int dbm = best.signalDbm;
                p.status = dbm >= -100 ? PASS : dbm >= -110 ? WARN : FAIL;
                p.detail = best.carrier + " · " + best.networkType + " · " + dbm + " dBm";
                return p;
            }
        }
        p.detail = "没有可用的 Wi-Fi 或蜂窝信号数据";
        return p;
    }

    public static HttpResult httpReachable() {
        HttpResult result = new HttpResult();
        String[] endpoints = {
                "https://www.baidu.com/favicon.ico",
                "https://speed.cloudflare.com/"
        };
        String lastError = "未知错误";
        for (String endpoint : endpoints) {
            HttpURLConnection conn = null;
            long start = System.nanoTime();
            try {
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setRequestProperty("User-Agent", "NetPulse/1.0 (Android network inspector)");
                conn.setRequestProperty("Range", "bytes=0-0");
                int code = conn.getResponseCode();
                result.latencyMs = (System.nanoTime() - start) / 1_000_000L;
                result.endpoint = endpoint;
                result.code = code;
                if (code >= 200 && code < 500) {
                    result.ok = true;
                    InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (in != null) {
                        try { in.read(); } catch (Exception ignored) {}
                        try { in.close(); } catch (Exception ignored) {}
                    }
                    return result;
                }
                lastError = "HTTP " + code;
            } catch (Exception e) {
                result.latencyMs = (System.nanoTime() - start) / 1_000_000L;
                lastError = e.getClass().getSimpleName();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        result.error = lastError;
        return result;
    }

    private static final Pattern TIME_PATTERN = Pattern.compile("time[=<]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms");
    private static final Pattern RTT_PATTERN = Pattern.compile(
            "rtt\\s+min/avg/max/(?:mdev|stddev)\\s*=\\s*([0-9.]+)/([0-9.]+)/([0-9.]+)/([0-9.]+)");

    public static PingResult ping(String host, int count, int timeoutMs) {
        PingResult p = new PingResult();
        p.host = host;
        if (host == null || host.isEmpty()) {
            p.error = "没有目标地址";
            return p;
        }
        PingResult nativeResult = nativePing(host, count, timeoutMs);
        if (nativeResult.received > 0) return nativeResult;
        PingResult tcp = tcpPing(host, count, timeoutMs);
        if (nativeResult.error != null) {
            tcp.error = tcp.received > 0 ? null : "ICMP 不可用，" + tcp.error;
        }
        return tcp;
    }

    private static PingResult nativePing(String host, int count, int timeoutMs) {
        PingResult p = new PingResult();
        p.host = host;
        p.method = "ICMP";
        Process process = null;
        try {
            int waitSeconds = Math.max(1, timeoutMs / 1000);
            ProcessBuilder pb = new ProcessBuilder("/system/bin/ping", "-n", "-c", String.valueOf(count),
                    "-W", String.valueOf(waitSeconds), host);
            pb.redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor((long) count * (waitSeconds + 1) * 1000L + 1500,
                    TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                p.error = "ICMP 超时";
                return p;
            }
            String output = readSmall(process.getInputStream(), 16 * 1024);
            int exit = process.exitValue();
            List<Double> samples = new ArrayList<>();
            Matcher m = TIME_PATTERN.matcher(output);
            while (m.find()) samples.add(Double.parseDouble(m.group(1)));
            p.sent = count;
            p.received = samples.size();
            p.ok = !samples.isEmpty();
            if (samples.isEmpty()) {
                p.error = output.contains("Operation not permitted") || output.toLowerCase(Locale.US).contains("permission")
                        ? "系统禁止应用发送 ICMP" : "ICMP 无响应";
                return p;
            }
            double min = Double.MAX_VALUE, max = 0, sum = 0;
            for (double v : samples) {
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
            }
            p.minMs = min;
            p.maxMs = max;
            p.avgMs = sum / samples.size();
            Matcher rtt = RTT_PATTERN.matcher(output);
            if (rtt.find()) {
                try {
                    p.minMs = Double.parseDouble(rtt.group(1));
                    p.avgMs = Double.parseDouble(rtt.group(2));
                    p.maxMs = Double.parseDouble(rtt.group(3));
                    p.jitterMs = Double.parseDouble(rtt.group(4));
                } catch (Exception ignored) {}
            }
            if (p.jitterMs == 0 && samples.size() > 1) {
                double jsum = 0;
                for (int i = 1; i < samples.size(); i++) jsum += Math.abs(samples.get(i) - samples.get(i - 1));
                p.jitterMs = jsum / (samples.size() - 1);
            }
            // A partial response still has useful timing.
            p.ok = true;
            if (exit != 0 && samples.size() == 0) p.ok = false;
            return p;
        } catch (Exception e) {
            p.error = e.getClass().getSimpleName();
            return p;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static PingResult tcpPing(String host, int count, int timeoutMs) {
        PingResult p = new PingResult();
        p.host = host;
        p.method = "TCP";
        p.sent = count;
        List<Double> samples = new ArrayList<>();
        int[] ports = {443, 80};
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                try { Thread.sleep(110); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            boolean got = false;
            String lastError = null;
            for (int port : ports) {
                long start = System.nanoTime();
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    double ms = (System.nanoTime() - start) / 1_000_000.0;
                    samples.add(ms);
                    got = true;
                    break;
                } catch (Exception e) {
                    lastError = e.getClass().getSimpleName();
                } finally {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
            if (!got && lastError != null) p.error = lastError;
        }
        p.received = samples.size();
        p.ok = !samples.isEmpty();
        if (!p.ok) {
            if (p.error == null) p.error = "TCP 端口 443/80 均不可达";
            return p;
        }
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        double jsum = 0;
        double prev = -1;
        for (double v : samples) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            if (prev >= 0) jsum += Math.abs(v - prev);
            prev = v;
        }
        p.minMs = min;
        p.maxMs = max;
        p.avgMs = sum / samples.size();
        p.jitterMs = samples.size() > 1 ? jsum / (samples.size() - 1) : 0;
        p.error = null;
        return p;
    }

    private static String readSmall(InputStream in, int max) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[2048];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            sb.append(buffer, 0, n);
            if (sb.length() > max) break;
        }
        return sb.toString();
    }

    public static String networkSummary(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps == null) return "离线";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "蜂窝";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "以太网";
            return "其他";
        } catch (Exception e) {
            return "未知";
        }
    }
}
