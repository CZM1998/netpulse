# 网脉 NetPulse

[![Build APK](https://github.com/CZM1998/netpulse/actions/workflows/build-apk.yml/badge.svg)](https://github.com/CZM1998/netpulse/actions/workflows/build-apk.yml)

一个为小米手机实测开发的原生 Android 网络与信号实验室。品牌图标为亮青蓝渐变徽章 + 深色网络脉冲线，应用内与桌面保持一致。无需第三方 UI/网络依赖，直接读取 Android 公开 API，适合做现场 Wi-Fi、双卡、蜂窝信号和测速排查。

- 自动构建：每次推送后由 GitHub Actions 编译并上传已签名 APK（Actions → Build APK → Artifacts）。
- 版本：1.0.0（versionCode 1）
- 包名：`com.netpulse.app`
- 最低系统：Android 10（API 29）；已在 Android 12 / MIUI 14 的 Xiaomi `M2007J3SC` 上验证

## 功能

### 概览
- 当前 Wi-Fi / 蜂窝链路、SSID、实时速率、信号 dBm、协商速率、频宽、公网/本地 IP
- 90 个采样点的实时信号趋势
- 本次运行 / 开机以来 / 蜂窝 / 本应用的流量统计
- 100 分制一键网络体检：外网、DNS、网关、信号、延迟与抖动

### Wi-Fi 分析
- SSID、BSSID、MAC、安全类型、Wi-Fi 协议、信道、频宽、频段、链路速率、TX/RX、IPv4、IPv6、网关、DNS、接口
- 附近热点扫描（需精确定位权限 + 系统定位开启）
- 2.4 GHz 重叠频谱曲线、5 GHz 信道占用柱状图、当前连接高亮
- 2.4 GHz 1/6/11 干扰推荐；5 GHz 同信道分析与建议
- 每个热点可查看原始能力字段

### 蜂窝与 SIM
- 双卡活动订阅、运营商、数据卡/待机、漫游、注册状态、数据连接状态
- RSRP / RSRQ / SINR、5G NR SS-RSRP / SS-RSRQ / SS-SINR
- PCI、TAC、小区 ID、LTE/NR 频段、ARFCN、邻区列表
- 当前设备没有插入活动 SIM 时，会显示明确的空状态和排查提示

### 多通道测速
- 下载通道：
  - 自动选择（推荐）：并行探测并选择最快的可用国内公开镜像
  - 华为云 CDN
  - 南京大学镜像
  - 中科大镜像
  - 上海交大镜像
  - Cloudflare 全球
- 上传通道：Cloudflare
- TCP 延迟、抖动、连接失败率、4 线程实时吞吐曲线
- 快速模式（6 秒下载 + 6 秒上传）与完整模式（12 + 12 秒）
- 支持取消；取消不保存结果
- 最近 8 次测速记录保存在本机，可分享
- **测速数据全程流式读取并丢弃，不写入手机存储，不产生需要清理的测速文件。** 仅测速历史以小体积 JSON 保存在应用 SharedPreferences 中，可在“测速记录 → 清空”删除。

### 工具
- 一键体检（100 分）
- Ping：网关、223.5.5.5、1.1.1.1、speed.cloudflare.com，ICMP 优先，TCP 回退
- DNS：多个域名的解析耗时与返回地址
- 公网 IP / ASN / 城市 / Cloudflare colo，可一键复制
- 权限与定位状态
- 生成并分享完整文本报告

## 本轮新增（按反馈）
- **信道图直接标注**：每个热点在图中显示编号，使用独立颜色；下方图例显示编号、SSID、信道和 dBm。同名的 Mesh AP 会追加 BSSID 尾号，当前 AP 用 ★ 和加粗白边高亮，点击图例项可打开完整热点详情。
- **无 SIM 也能看射频与基站**：蜂窝页现在始终读取默认 Modem 和公开 `CellInfo`。在无活动 SIM 的状态下，本机实测仍能读到 LTE 小区、PLMN 和信号强度。
  - 运营商/PLMN：中国电信，46011
  - 最强小区：LTE，PCI 141，B3，ARFCN 2452，约 -66 dBm
  - 邻区：LTE，ARFCN 1850，约 -121 dBm
  - 同时显示 Modem 2/2 路、语音/数据注册、数据连接/开关、漫游、网络制式、网络国家等信息
  - 当 Android 的 `SignalStrength` 不提供读数时，会明确标注“小区扫描”，并从 CellInfo 推导最强小区信号
- 无 SIM 时部分字段（服务小区标记、RSRQ/完整 SINR、可用运营商列表）受 Android/基带限制；插入并启用 SIM 后自动切换为完整双卡和基站视图。

## 实机测试结果（Xiaomi M2007J3SC / Android 12 / MIUI 14）
- Wi-Fi 6、5 GHz、信道 36、160 MHz、约 -46 dBm、协商 1200 Mbps
- 一键体检：100/100（外网、DNS、网关、Wi-Fi 信号、延迟抖动全部通过）
- 自动选择到华为云 CDN：下载约 434 Mbps / 上传约 143 Mbps / TCP 延迟 45 ms / 抖动 4 ms
- 完整模式：下载约 603 Mbps / 上传约 140 Mbps
- 取消测速流程已验证：立即清理连接、不产生历史记录
- 测速测试使用真实网络流量；测试完成后未在手机或电脑留下测速下载文件

## GitHub Actions 自动构建
推送或提交 PR 后，`.github/workflows/build-apk.yml` 会自动：

1. 安装 JDK 17、Android platform 36 和 build-tools 35
2. 执行 `bash build.sh`
3. 生成 `netpulse.apk` 与 SHA-256 校验文件
4. 上传为 Actions Artifact：`netpulse-apk`

推送 `v*` 标签（如 `v1.0.0`）时，还会自动创建 GitHub Release 并附带 APK。

默认每次使用临时 CI 签名证书，适合直接侧载测试。若要发布稳定更新，在仓库 Settings → Secrets and variables → Actions 中添加：

- `ANDROID_KEYSTORE_BASE64`：keystore 的 Base64 文本
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

未配置 secrets 时，`build.sh` 会在本地自动生成 `netpulse.keystore`，该文件和 APK 均已被 `.gitignore` 排除。

## 本地构建
需要 JDK 11+（CI 使用 17）、Android SDK platform 36 与任一较新的 build-tools。脚本会自动探测 SDK、JDK 和 build-tools：

```bash
bash build.sh
```

脚本会编译资源与 Java、D8 转 dex、zipalign、签名，输出 `netpulse.apk`。

## 隐私与限制
- 所有分析都在本机完成；只有公网 IP 查询、测速、一键体检会发起网络请求。
- 不读取 WiFi 密码，不上传位置、号码或 SIM 标识。
- 部分蜂窝小区字段受 Android 公开 API、MIUI 和运营商限制，不保证每张卡都能返回全部字段。
- 公开 CDN 文件地址可能随时间变化；自动模式会探测可用节点并失败切换，亦可手动选择其他节点或 Cloudflare。
