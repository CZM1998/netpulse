package com.netpulse.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches public IP / ASN information from privacy-light HTTPS endpoints. */
public final class PublicInfoService {
    private PublicInfoService() {}

    public static class PublicInfo {
        public String ip = "";
        public String city = "";
        public String region = "";
        public String country = "";
        public String org = "";
        public String loc = "";
        public String colo = "";
        public String source = "";
        public String error = "";

        public String locationText() {
            if (city != null && !city.isEmpty() && region != null && !region.isEmpty()) {
                return region + " · " + city;
            }
            if (region != null && !region.isEmpty()) return region;
            if (city != null && !city.isEmpty()) return city;
            if (country != null && !country.isEmpty()) return country;
            return "未知位置";
        }

        public String orgText() {
            if (org != null && !org.isEmpty()) return org;
            return "未知运营商 / ASN";
        }
    }

    public static PublicInfo fetch() {
        PublicInfo info = tryIpInfo();
        if (info.ip != null && !info.ip.isEmpty()) return info;
        PublicInfo cf = tryCloudflareTrace(info.error);
        if (cf.ip != null && !cf.ip.isEmpty()) return cf;
        if (cf.error == null || cf.error.isEmpty()) cf.error = "无法获取公网信息";
        return cf;
    }

    private static PublicInfo tryIpInfo() {
        PublicInfo info = new PublicInfo();
        HttpURLConnection conn = null;
        try {
            conn = open("https://ipinfo.io/json?token=", 5000, 6000);
            int code = conn.getResponseCode();
            if (code != 200) {
                info.error = "ipinfo HTTP " + code;
                return info;
            }
            String body = read(conn.getInputStream(), 64 * 1024);
            JSONObject j = new JSONObject(body);
            info.ip = j.optString("ip", "");
            info.city = j.optString("city", "");
            info.region = j.optString("region", "");
            info.country = j.optString("country", "");
            info.org = j.optString("org", "");
            info.loc = j.optString("loc", "");
            info.source = "ipinfo.io";
            return info;
        } catch (Exception e) {
            info.error = "公网信息服务不可达";
            return info;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static PublicInfo tryCloudflareTrace(String priorError) {
        PublicInfo info = new PublicInfo();
        HttpURLConnection conn = null;
        try {
            conn = open("https://www.cloudflare.com/cdn-cgi/trace", 5000, 6000);
            if (conn.getResponseCode() != 200) {
                info.error = priorError;
                return info;
            }
            String body = read(conn.getInputStream(), 32 * 1024);
            for (String line : body.split("\\n")) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq);
                String v = line.substring(eq + 1).trim();
                switch (k) {
                    case "ip": info.ip = v; break;
                    case "loc": info.country = v; if (info.region.isEmpty()) info.region = v; break;
                    case "colo": info.colo = v; break;
                    default: break;
                }
            }
            info.source = "cloudflare.com";
            if (info.colo != null && !info.colo.isEmpty()) {
                info.org = "Cloudflare 边缘节点 " + info.colo;
            }
            return info;
        } catch (Exception e) {
            info.error = priorError == null || priorError.isEmpty() ? "公网信息服务不可达" : priorError;
            return info;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", "NetPulse/1.0 (Android network inspector)");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String read(InputStream in, int maxBytes) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) > 0) {
            sb.append(buf, 0, n);
            if (sb.length() > maxBytes) break;
        }
        return sb.toString();
    }
}
