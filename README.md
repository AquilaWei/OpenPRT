# OpenPRT

![version](https://img.shields.io/badge/version-0.1.6-blue)

**匹茲堡公車（Pittsburgh Regional Transit, PRT）乘車資訊 App**，先做 Android，iOS 之後再處理。

> 目前是 **0.1.6 開發版**：主畫面是地圖，顯示你的位置與 400 公尺內的公車站牌，移動時跟著更新（拒絕定位時改用 Downtown Pittsburgh）；已完成 PRT TrueTime 即時資料的連線元件與附近班次的排序邏輯。班次列表畫面仍在開發中。

## 做什麼

- 📍 **附近班次**：依手機定位找出附近站牌，列出最趕得上、等最少的班次並自動更新
- 🚌 **班次詳情**：點擊班次後在地圖上顯示路線、站牌、公車即時位置與預估抵達時間
- 🧭 **路線規劃**：選擇目的地後規劃乘車路線，提供時間預估、班次與轉乘資訊

資料來源：PRT TrueTime 即時 API 與 PRT GTFS 靜態時刻表（[`GTFS.zip`](https://www.rideprt.org/developerresources/GTFS.zip)，公開下載、不需 key）。
地圖用 **[MapLibre](https://maplibre.org/)** 搭配 **[OpenFreeMap](https://openfreemap.org/)** 的 OpenStreetMap 圖磚，免費、**不需要地圖 API key**。

> 第一次開啟時 App 會下載 GTFS 站牌資料（約 22 MB），之後才會在地圖上顯示附近站牌。

## 需求

- **JDK 21 以上**（只裝了 JRE 或其他版本也可以，Gradle 會自動下載 JDK 21 toolchain）
- **Android SDK**，含 platform **android-37** 與 build-tools 36（已接受授權時，Gradle 會自動下載缺少的套件）

在專案根目錄建立 `local.properties`（不進 git），指定 SDK 路徑：

```properties
sdk.dir=/path/to/Android/Sdk
```

### PRT TrueTime API key

即時班次與公車位置來自 **PRT TrueTime API**（`https://truetime.rideprt.org/bustime/api/v3/`），需要個人 API key：

1. 到 **[PRT TrueTime](https://truetime.rideprt.org/bustime/home.jsp)** 註冊帳號並登入
2. 照 **My Account** 頁面上的說明申請 real-time API key
3. 把 key 加進 `local.properties`（**不要 commit，也不要貼到 issue 或對話中**）：

```properties
PRT_API_KEY=你的key
```

- 沒有設定 key 也能建置與跑測試；App 呼叫 API 時會回報「缺少 API key」錯誤
- key 會編進 APK 的 `BuildConfig`，請只用個人帳號的 key，不要拿有其他權限的金鑰

## 建置與測試

完整檢查（與 CI 相同）：

```bash
./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

| 指令 | 用途 |
|---|---|
| `./gradlew ktlintFormat` | 自動修正 Kotlin 排版 |
| `./gradlew testDebugUnitTest` | 單元測試（含 Robolectric Compose 測試） |
| `./gradlew lintDebug` | Android Lint，warning 一律視為錯誤 |
| `./gradlew assembleDebug` | 產出 `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew installDebug` | 安裝到已連接的手機或模擬器 |

## 參與開發

- **版號只寫在 `gradle.properties`**（`VERSION_NAME` / `VERSION_CODE`），App 從那裡讀取
- 排版由 ktlint 決定、Lint warning 視為錯誤，送出前先跑上面的完整檢查
- 每次 push / PR 都會由 GitHub Actions（`.github/workflows/ci.yml`）跑同一組檢查
- 版本變更記錄在 [CHANGELOG.md](CHANGELOG.md)
