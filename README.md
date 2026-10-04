# OpenPRT

![version](https://img.shields.io/badge/version-0.1.25-blue)

**匹茲堡公車（Pittsburgh Regional Transit, PRT）乘車資訊 App**，先做 Android，iOS 之後再處理。

> 目前是 **0.1.25 開發版**：主畫面是地圖，顯示你的位置與 400 公尺內的公車站牌，移動時跟著更新（拒絕定位時改用 Downtown Pittsburgh）；下方的「Nearby departures」面板列出走得到、趕得上的附近班次，每 30 秒更新；點地圖上的站牌會列出該站接下來的班次（有即時預測用即時時間，沒有 key 或沒有預測時改用時刻表並標示「Scheduled」），左下角的圖示按鈕會打開**地圖圖例**，說明每種顏色與圖示的意思；點一班車會在地圖上畫出它的路線、沿線站牌，標出你要上車的站牌，並顯示這班車的即時位置與還有幾分鐘到站（每 15 秒更新）。地圖上方的搜尋框可以找匹茲堡地區的目的地，或長按地圖直接選點；選好目的地後，下方面板會改列出最多三個乘車方案（總分鐘數、出發 / 抵達時間、轉乘次數、各段路線與步行時間），第一班車有即時預測時改用即時時間並標示「Live」，點一個方案會在地圖上畫出步行（虛線）與公車路線、列出每一段，並可查看那班公車的即時位置。即時班次需要 TrueTime API key，第一次開 App 時會引導你輸入（見下方說明）。外觀用 PRT 的深藍 + 金黃，有**淺色與深色**兩種（預設跟隨手機，右上角半圓圖示可切換），地圖也會跟著變深色。

## 做什麼

- 📍 **附近班次**：依手機定位找出附近站牌，列出最趕得上、等最少的班次並自動更新
- 🚌 **班次詳情**：點擊班次後在地圖上顯示路線、站牌、公車即時位置與預估抵達時間
- 🧭 **路線規劃**：選擇目的地後規劃乘車路線，提供時間預估、班次與轉乘資訊

資料來源：PRT TrueTime 即時 API 與 PRT GTFS 靜態時刻表（[`GTFS.zip`](https://www.rideprt.org/developerresources/GTFS.zip)，公開下載、不需 key）。
地圖用 **[MapLibre](https://maplibre.org/)** 搭配 **[OpenFreeMap](https://openfreemap.org/)** 的 OpenStreetMap 圖磚，免費、**不需要地圖 API key**。
目的地搜尋用 **[Photon](https://photon.komoot.io/)**（OpenStreetMap 地理編碼），同樣免費、不需 key。

> 第一次開啟時 App 會下載 GTFS 站牌與時刻表資料（下載約 22 MB，存進手機後約佔 **75 MB**），之後才會在地圖上顯示附近站牌。

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
3. **第一次開 App 時在歡迎畫面貼上 key**，按 **Save key**；App 會先向 TrueTime 確認 key 有效才儲存
   - 之後要換 key：點主畫面右上角的 **鑰匙圖示**
   - 也可以先按 **Skip for now**：站牌、目的地搜尋與時刻表路線規劃不需要 key，只有即時資料需要

- key 只存在手機上 App 的私有儲存空間（不會備份、不會上傳到 PRT 以外的地方）
- **開發用（選用）**：在 `local.properties` 加上 `PRT_API_KEY=你的key`，debug 版就會內建這個 key、不顯示歡迎畫面；
  App 內輸入的 key 優先。這個檔案**不要 commit，也不要貼到 issue 或對話中**
- 沒有任何 key 也能建置與跑測試

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
