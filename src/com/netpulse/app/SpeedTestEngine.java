package com.netpulse.app;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-connection throughput test with several public download channels.
 * All test payloads are streamed and discarded in memory; nothing is written to
 * the phone's storage, so there are no test files to clean up afterwards.
 */
public final class SpeedTestEngine {
    private static final String TAG = "NetPulseSpeed";
    private static final String CF_DOWN_URL = "https://speed.cloudflare.com/__down?bytes=50000000";
    private static final String UP_URL = "https://speed.cloudflare.com/__up";
    private static final String PING_HOST = "speed.cloudflare.com";
    private static final String CDN_ISO = "ubuntu-releases/22.04/ubuntu-22.04.5-live-server-amd64.iso";

    public static final class Provider {
        public final String id;
        public final String name;
        public final String shortName;
        public final String downloadUrl;
        public final String host;
        public final boolean cloudflare;
        public final boolean auto;

        private Provider(String id, String name, String shortName, String downloadUrl,
                         String host, boolean cloudflare, boolean auto) {
            this.id = id;
            this.name = name;
            this.shortName = shortName;
            this.downloadUrl = downloadUrl;
            this.host = host;
            this.cloudflare = cloudflare;
            this.auto = auto;
        }
    }

    public static final Provider AUTO = new Provider("auto", "自动选择（推荐）", "自动", "", "", false, true);
    public static final Provider HUAWEI = new Provider("huawei", "华为云 CDN", "华为云",
            "https://mirrors.huaweicloud.com/" + CDN_ISO, "mirrors.huaweicloud.com", false, false);
    public static final Provider NJU = new Provider("nju", "南京大学镜像", "南大",
            "https://mirror.nju.edu.cn/" + CDN_ISO, "mirror.nju.edu.cn", false, false);
    public static final Provider USTC = new Provider("ustc", "中科大镜像", "中科大",
            "https://mirrors.ustc.edu.cn/" + CDN_ISO, "mirrors.ustc.edu.cn", false, false);
    public static final Provider SJTU = new Provider("sjtu", "上海交大镜像", "上交",
            "https://mirrors.sjtug.sjtu.edu.cn/" + CDN_ISO, "mirrors.sjtug.sjtu.edu.cn", false, false);
    public static final Provider CLOUDFLARE = new Provider("cloudflare", "Cloudflare 全球", "Cloudflare",
            CF_DOWN_URL, PING_HOST, true, false);
    public static final Provider[] PROVIDERS = {AUTO, HUAWEI, NJU, USTC, SJTU, CLOUDFLARE};
    private static final Provider[] AUTO_CANDIDATES = {HUAWEI, NJU, USTC, SJTU};
    public static final String UPLOAD_PROVIDER_NAME = "Cloudflare";

    public interface Listener {
        void onPhase(String title, String detail);
        void onProgress(double currentMbps, double downloadAvg, double uploadAvg);
        void onPing(PingStats stats);
        void onComplete(Result result);
        void onError(String message);
        void onCancelled();
    }

    public static class PingStats {
        public int sent;
        public int received;
        public double minMs;
        public double avgMs;
        public double maxMs;
        public double jitterMs;
        public boolean ok;
        public String error;

        public double lossPercent() {
            return sent <= 0 ? 100 : Math.max(0, (sent - received) * 100.0 / sent);
        }
    }

    public static class Result {
        public double pingMs;
        public double jitterMs;
        public double lossPercent;
        public double downloadMbps;
        public double uploadMbps;
        public long downloadBytes;
        public long uploadBytes;
        public long dataBytes;
        public String networkLabel = "";
        public String providerId = "";
        public String providerName = "";
        public String uploadProviderName = UPLOAD_PROVIDER_NAME;
        public String server = "";
        public boolean quick;
        public long timestamp = System.currentTimeMillis();
        public long durationMs;
        public String uploadNote;
    }

    private static class Transfer {
        long bytes;
        long measuredBytes;
        long durationMs;
        double mbps;
        int lastHttpCode;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<HttpURLConnection> connections =
            Collections.synchronizedList(new ArrayList<>());
    private volatile boolean cancelled;
    private volatile boolean running;
    private volatile Thread worker;
    private Listener listener;
    private Provider requestedProvider = AUTO;

    public boolean isRunning() {
        return running;
    }

    public boolean start(boolean quick, String providerId, String networkLabel, Listener listener) {
        if (running) return false;
        this.listener = listener;
        requestedProvider = findProvider(providerId);
        cancelled = false;
        running = true;
        worker = new Thread(() -> execute(quick, networkLabel), "netpulse-speedtest");
        worker.start();
        return true;
    }

    public void cancel() {
        if (!running) return;
        cancelled = true;
        disconnectAll();
        Thread t = worker;
        if (t != null) t.interrupt();
    }

    private void execute(boolean quick, String networkLabel) {
        long startedAt = System.currentTimeMillis();
        Result result = new Result();
        result.quick = quick;
        result.networkLabel = networkLabel == null ? "" : networkLabel;
        boolean postedComplete = false;
        try {
            Log.d(TAG, "speedtest start quick=" + quick + " provider=" + requestedProvider.id);
            Provider provider = requestedProvider;
            if (provider.auto) {
                postPhase("选择测速节点", "自动探测可用 CDN");
                provider = pickAutoProvider();
            }
            if (provider == null) provider = CLOUDFLARE;
            result.providerId = provider.id;
            result.providerName = provider.name;
            result.uploadProviderName = UPLOAD_PROVIDER_NAME;
            result.server = provider.name + " / " + UPLOAD_PROVIDER_NAME;

            postPhase("测量延迟", provider.name + " · TCP 握手");
            PingStats ping = measurePing(provider.host);
            if (cancelled) return;
            result.pingMs = ping.ok ? ping.avgMs : Double.NaN;
            result.jitterMs = ping.ok ? ping.jitterMs : Double.NaN;
            result.lossPercent = ping.lossPercent();
            post(() -> {
                if (listener != null && !cancelled) listener.onPing(ping);
            });

            long duration = quick ? 6000 : 12000;
            postPhase("下载测试", provider.name + " · 4 线程 · " + (duration / 1000) + " 秒");
            Transfer down = transfer(false, duration, provider);
            Log.d(TAG, "download done provider=" + provider.id + " bytes=" + down.bytes
                    + " mbps=" + down.mbps);
            if (cancelled) return;
            if (down.bytes < 10_000) {
                if (down.lastHttpCode == 429) {
                    throw new IOException("下载节点限流（HTTP 429），请更换节点或稍后再试");
                }
                throw new IOException("下载节点 " + provider.name
                        + " 未返回数据，请在「测速节点」中更换通道");
            }
            result.downloadMbps = down.mbps;
            result.downloadBytes = down.bytes;

            postPhase("上传测试", "Cloudflare 上传 · 4 线程 · " + (duration / 1000) + " 秒");
            Transfer up = transfer(true, duration, CLOUDFLARE);
            Log.d(TAG, "upload done bytes=" + up.bytes + " mbps=" + up.mbps);
            if (cancelled) return;
            result.uploadMbps = up.mbps;
            result.uploadBytes = up.bytes;
            if (up.bytes < 10_000) {
                result.uploadMbps = 0;
                result.uploadNote = "上传节点未返回有效数据";
            }

            result.dataBytes = down.bytes + up.bytes;
            result.durationMs = System.currentTimeMillis() - startedAt;
            running = false;
            postedComplete = true;
            post(() -> {
                if (listener != null && !cancelled) listener.onComplete(result);
            });
        } catch (Exception e) {
            running = false;
            Log.e(TAG, "speedtest failed", e);
            if (!cancelled) {
                String message = e.getMessage();
                if (message == null || message.isEmpty()) message = e.getClass().getSimpleName();
                final String finalMessage = message;
                post(() -> {
                    if (listener != null && !cancelled) listener.onError(finalMessage);
                });
            }
        } finally {
            disconnectAll();
            running = false;
            if (cancelled && !postedComplete) {
                post(() -> {
                    if (listener != null) listener.onCancelled();
                });
            }
        }
    }

    public static Provider findProvider(String id) {
        if (id != null) {
            for (Provider p : PROVIDERS) {
                if (p.id.equals(id)) return p;
            }
        }
        return AUTO;
    }

    private Provider pickAutoProvider() {
        ExecutorService pool = Executors.newFixedThreadPool(AUTO_CANDIDATES.length);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (Provider provider : AUTO_CANDIDATES) {
                futures.add(pool.submit((Callable<Long>) () -> probeProvider(provider)));
            }
            Provider best = null;
            long bestMs = Long.MAX_VALUE;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    long ms = futures.get(i).get(2700, TimeUnit.MILLISECONDS);
                    if (ms >= 0 && ms < bestMs) {
                        bestMs = ms;
                        best = AUTO_CANDIDATES[i];
                    }
                } catch (Exception ignored) {}
            }
            if (best == null) best = CLOUDFLARE;
            Log.d(TAG, "auto provider=" + best.id + " probe=" + bestMs + "ms");
            return best;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reads at most 64 KiB (streamed, not saved) to check reachability and latency. */
    private long probeProvider(Provider provider) {
        HttpURLConnection conn = null;
        InputStream in = null;
        long start = SystemClock.elapsedRealtime();
        try {
            conn = (HttpURLConnection) new URL(provider.downloadUrl).openConnection();
            conn.setConnectTimeout(2400);
            conn.setReadTimeout(2400);
            conn.setUseCaches(false);
            conn.setRequestProperty("Range", "bytes=0-65535");
            conn.setRequestProperty("User-Agent", "NetPulse/1.0 probe");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return -1;
            in = conn.getInputStream();
            byte[] buf = new byte[8192];
            int got = 0;
            while (got < 65536) {
                int n = in.read(buf, 0, Math.min(buf.length, 65536 - got));
                if (n < 0) break;
                got += n;
            }
            return got > 0 ? SystemClock.elapsedRealtime() - start : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private PingStats measurePing(String host) {
        PingStats stats = new PingStats();
        stats.sent = 6;
        List<Double> samples = new ArrayList<>();
        InetAddress address = null;
        try {
            address = InetAddress.getByName(host == null || host.isEmpty() ? PING_HOST : host);
        } catch (Exception e) {
            stats.error = "DNS 解析失败";
            return stats;
        }
        for (int i = 0; i < 6; i++) {
            if (cancelled) break;
            Socket socket = new Socket();
            long start = System.nanoTime();
            try {
                socket.connect(new InetSocketAddress(address, 443), 1600);
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                samples.add(ms);
            } catch (Exception e) {
                if (stats.error == null) stats.error = e.getClass().getSimpleName();
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
            if (i < 5) {
                try { Thread.sleep(90); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        stats.received = samples.size();
        stats.ok = !samples.isEmpty();
        if (!stats.ok) {
            if (stats.error == null) stats.error = "TCP 连接失败";
            return stats;
        }
        double min = Double.MAX_VALUE, max = 0, sum = 0, jitter = 0, prev = -1;
        for (double v : samples) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            if (prev >= 0) jitter += Math.abs(v - prev);
            prev = v;
        }
        stats.minMs = min;
        stats.maxMs = max;
        stats.avgMs = sum / samples.size();
        stats.jitterMs = samples.size() > 1 ? jitter / (samples.size() - 1) : 0;
        return stats;
    }

    private Transfer transfer(boolean upload, long durationMs, Provider provider) {
        final long start = SystemClock.elapsedRealtime();
        final long deadline = start + durationMs;
        final AtomicLong count = new AtomicLong();
        final AtomicInteger lastCode = new AtomicInteger();
        final int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            final int id = i;
            pool.execute(() -> {
                if (upload) uploadWorker(count, deadline);
                else downloadWorker(count, deadline, id, lastCode, provider);
            });
        }

        long lastTime = start;
        long lastBytes = 0;
        long warmBytes = 0;
        long warmTime = start;
        boolean warmRecorded = false;
        int rateLimitTicks = 0;
        while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(220); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            long now = SystemClock.elapsedRealtime();
            if (lastCode.get() == 429 && count.get() == 0) {
                rateLimitTicks++;
                if (rateLimitTicks >= 2) break;
            } else {
                rateLimitTicks = 0;
            }
            long total = count.get();
            double deltaSec = Math.max(0.001, (now - lastTime) / 1000.0);
            double liveMbps = (total - lastBytes) * 8.0 / deltaSec / 1_000_000.0;
            lastTime = now;
            lastBytes = total;
            if (!warmRecorded && now - start >= 1300) {
                warmRecorded = true;
                warmBytes = total;
                warmTime = now;
            }
            final double shown = Math.max(0, liveMbps);
            final long totalBytes = total;
            final double downAvg = upload ? 0 : avgMbps(totalBytes, now - start);
            final double upAvg = upload ? avgMbps(totalBytes, now - start) : 0;
            post(() -> {
                if (listener != null && !cancelled) listener.onProgress(shown, downAvg, upAvg);
            });
        }

        disconnectAll();
        pool.shutdownNow();
        try { pool.awaitTermination(700, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        long end = SystemClock.elapsedRealtime();
        long total = count.get();
        long measureStart = warmRecorded ? warmTime : start;
        long measureBytes = Math.max(0, total - (warmRecorded ? warmBytes : 0));
        double sec = Math.max(0.2, (end - measureStart) / 1000.0);
        Transfer t = new Transfer();
        t.bytes = total;
        t.measuredBytes = measureBytes;
        t.durationMs = end - start;
        t.lastHttpCode = lastCode.get();
        t.mbps = measureBytes * 8.0 / sec / 1_000_000.0;
        if (t.mbps < 0.01) t.mbps = 0;
        return t;
    }

    private void downloadWorker(AtomicLong count, long deadline, int id,
                                AtomicInteger lastCode, Provider provider) {
        byte[] buffer = new byte[64 * 1024];
        while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                String url = provider.cloudflare ? CF_DOWN_URL : provider.downloadUrl;
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-store");
                conn.setRequestProperty("Accept-Encoding", "identity");
                conn.setRequestProperty("User-Agent", "NetPulse/1.0 speedtest");
                conn.setInstanceFollowRedirects(true);
                if (!provider.cloudflare) {
                    // Distinct 256 MB windows; payload is streamed, never saved.
                    conn.setRequestProperty("Range", "bytes=" + ((long) id * 256_000_000L) + "-");
                }
                connections.add(conn);
                if (cancelled) break;
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    lastCode.set(code);
                    Log.w(TAG, "download HTTP " + code + " provider=" + provider.id);
                    try { Thread.sleep(code == 429 ? 1200 : 300); } catch (InterruptedException e) { return; }
                    continue;
                }
                in = conn.getInputStream();
                int n;
                while (!cancelled && SystemClock.elapsedRealtime() < deadline
                        && (n = in.read(buffer)) >= 0) {
                    count.addAndGet(n);
                }
            } catch (Exception e) {
                if (!cancelled) Log.w(TAG, "download error: " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " " + e.getMessage()));
                if (cancelled) return;
                try { Thread.sleep(200); } catch (InterruptedException ie) { return; }
            } finally {
                if (in != null) try { in.close(); } catch (Exception ignored) {}
                if (conn != null) {
                    connections.remove(conn);
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private void uploadWorker(AtomicLong count, long deadline) {
        final int requestBytes = 25_000_000;
        byte[] buffer = new byte[64 * 1024];
        while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
            HttpURLConnection conn = null;
            OutputStream out = null;
            try {
                conn = (HttpURLConnection) new URL(UP_URL).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                conn.setRequestProperty("User-Agent", "NetPulse/1.0 speedtest");
                conn.setFixedLengthStreamingMode(requestBytes);
                connections.add(conn);
                if (cancelled) break;
                conn.connect();
                out = conn.getOutputStream();
                int written = 0;
                int flushCounter = 0;
                while (!cancelled && SystemClock.elapsedRealtime() < deadline && written < requestBytes) {
                    int length = Math.min(buffer.length, requestBytes - written);
                    out.write(buffer, 0, length);
                    written += length;
                    count.addAndGet(length);
                    flushCounter++;
                    if (flushCounter % 24 == 0) {
                        out.flush();
                        flushCounter = 0;
                    }
                }
                try { out.flush(); } catch (Exception ignored) {}
                if (written < requestBytes) {
                    if (out != null) try { out.close(); } catch (Exception ignored) {}
                    break;
                }
                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in != null) {
                    try { in.read(); } catch (Exception ignored) {}
                    try { in.close(); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
                if (cancelled) return;
                try { Thread.sleep(120); } catch (InterruptedException e) { return; }
            } finally {
                if (out != null) try { out.close(); } catch (Exception ignored) {}
                if (conn != null) {
                    connections.remove(conn);
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private double avgMbps(long bytes, long elapsedMs) {
        return bytes * 8.0 / Math.max(100, elapsedMs) / 1000.0;
    }

    private void postPhase(String title, String detail) {
        post(() -> {
            if (listener != null && !cancelled) listener.onPhase(title, detail);
        });
    }

    private void post(Runnable r) {
        main.post(r);
    }

    private void disconnectAll() {
        synchronized (connections) {
            for (HttpURLConnection conn : new ArrayList<>(connections)) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
            connections.clear();
        }
    }

    public static String mbpsText(double value) {
        if (!Double.isFinite(value) || value <= 0) return "—";
        return String.format(Locale.US, "%.1f", value);
    }
}
