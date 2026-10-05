# OpenPRT

![version](https://img.shields.io/badge/version-0.1.34-blue)

**匹茲堡公車（Pittsburgh Regional Transit, PRT）乘車資訊 App**，先做 Android，iOS 之後再處理。

> 目前是 **0.1.34 開發版**：主畫面是地圖，顯示你的位置與 400 公尺內的公車站牌，移動時跟著更新（拒絕定位時改用 Downtown Pittsburgh）；下方的「Nearby departures」面板列出走得到、趕得上的附近班次（公車與輕軌 T 線），每 30 秒更新；點地圖上的站牌會列出該站接下來的班次（有即時預測用即時時間，沒有 key 或沒有預測時改用時刻表並標示「Scheduled」，點時刻表班次會列出它接下來停靠的站牌與時間），左下角的圖示按鈕會打開**地圖圖例**，說明每種顏色與圖示的意思；點一班車會在地圖上畫出它的路線、沿線站牌，標出你要上車的站牌，並顯示這班車的即時位置與還有幾分鐘到站（每 15 秒更新）。地圖上方的搜尋框可以找匹茲堡地區的目的地，或長按地圖直接選點；搜尋框上方的「From」欄位預設「My location」，也可以搜尋地址或長按地圖改用別的起點，⇅ 按鈕對調起訖點；下方面板會改列出最多三個乘車方案，上方可切換 **Leave now / Depart at / Arrive by**（後兩者可選時刻表有效範圍內的日期與時間，Arrive by 會找出最晚出發、仍能準時抵達的方案並標示「Leave by …」，第一班車即時誤點可能趕不上時會提醒）（總分鐘數、出發 / 抵達時間、轉乘次數、各段路線與步行時間），第一班車有即時預測時改用即時時間並標示「Live」，點一個方案會在地圖上畫出沿街道的步行路線（虛線，並更新步行分鐘）與公車路線、列出每一段，並可查看那班公車的即時位置。即時班次需要 TrueTime API key，第一次開 App 時會引導你輸入（見下方說明）。外觀用 PRT 的深藍 + 金黃，有**淺色與深色**兩種（預設跟隨手機，右上角半圓圖示可切換），地圖也會跟著變深色。

## 做什麼

- 📍 **附近班次**：依手機定位找出附近站牌，列出最趕得上、等最少的班次並自動更新；公車與**輕軌 T 線**（Red / Blue / Silver Line）一起列出
- 🚌 **班次詳情**：點擊班次後在地圖上顯示路線、站牌、公車即時位置與預估抵達時間
- 🧭 **路線規劃**：選擇目的地（起點預設是你的位置，也可以輸入地址）後規劃乘車路線，提供時間預估、班次與轉乘資訊；可指定出發時間或抵達時間

資料來源：PRT TrueTime 即時 API 與 PRT GTFS 靜態時刻表（[開發者資源頁](https://www.rideprt.org/business-resources/web-developer-resources/)上的 `gtfs.zip`，公開下載、不需 key）。
地圖用 **[MapLibre](https://maplibre.org/)** 搭配 **[OpenFreeMap](https://openfreemap.org/)** 的 OpenStreetMap 圖磚，免費、**不需要地圖 API key**。
目的地搜尋用 **[Photon](https://photon.komoot.io/)**（OpenStreetMap 地理編碼），同樣免費、不需 key。
方案的步行路線用 **[FOSSGIS Valhalla](https://valhalla1.openstreetmap.de/)**（OpenStreetMap 步行路線）沿街道畫出，也免費、不需 key；選定方案時會把各步行段的起訖座標送到該服務，查不到或 5 秒內沒回應時改畫直線。

> 第一次開啟時 App 會下載 GTFS 站牌與時刻表資料（下載約 22 MB，存進手機後約佔 **75 MB**），之後才會在地圖上顯示附近站牌。
> 資料超過 7 天時，App 會在**有網路時於背景重新下載**（每天檢查一次）；下載失敗時繼續用手機上原本的資料。

## 下載安裝

1. 到 GitHub 專案的 **[Releases](https://github.com/AquilaWei/OpenPRT/releases)** 頁面，打開最新版本
2. 下載符合手機的 APK：
   - **`OpenPRT-vX.Y.Z-arm64-v8a.apk`**：近幾年的 Android 手機幾乎都是這個（最小）
   - `OpenPRT-vX.Y.Z-armeabi-v7a.apk`：較舊的 32 位元手機
   - `OpenPRT-vX.Y.Z-universal.apk`：不確定時用這個，所有手機都能裝，但檔案約 60 MB（arm64 版約 24 MB）
3. （選用）確認檔案沒有損壞：與同名的 `.sha256` 比對

   ```bash
   sha256sum -c OpenPRT-vX.Y.Z-arm64-v8a.apk.sha256
   ```

4. 在手機上打開 APK，依提示允許「安裝不明來源的應用程式」後安裝

> 手機上若已裝了自己建置的 debug 版，簽章不同無法直接覆蓋，要先解除安裝（會清掉 App 裡輸入的 key 與下載的時刻表）。

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
| `./gradlew assembleRelease` | 依 ABI 分開的 release APK；沒有簽章設定時產出未簽章的 `*-release-unsigned.apk` |
| `scripts/test-release-scripts.sh` | 發佈腳本（release notes 擷取、版號檢查）的測試 |

## 參與開發

- **版號只寫在 `gradle.properties`**（`VERSION_NAME` / `VERSION_CODE`），App 從那裡讀取
- 排版由 ktlint 決定、Lint warning 視為錯誤，送出前先跑上面的完整檢查
- 每次 push / PR 都會由 GitHub Actions（`.github/workflows/ci.yml`）跑同一組檢查
- 版本變更記錄在 [CHANGELOG.md](CHANGELOG.md)

### 發佈新版本

推送 `v*` tag 時，GitHub Actions（`.github/workflows/release.yml`）會跑同一組檢查、建置簽章 APK、
附上 SHA256，並用 CHANGELOG 對應版本的段落當 release notes 建立 GitHub Release。

1. 改 `gradle.properties` 的版號與 README 的版本 badge，CHANGELOG 加上該版段落（`scripts/check-version.sh` 會檢查 badge）
2. 推送 tag（tag 必須等於 `v` + `VERSION_NAME`，否則 workflow 會失敗；下面的指令直接從 `gradle.properties` 讀版號）：

   ```bash
   version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)" && git tag "v$version" && git push origin "v$version"
   ```

**第一次發佈前**，在 repo 的 *Settings → Secrets and variables → Actions* 設定以下 secrets（簽章金鑰**不要 commit**，`.gitignore` 已排除 `*.jks` / `*.keystore`）：

| Secret | 內容 |
|---|---|
| `OPENPRT_KEYSTORE_BASE64` | 簽章金鑰檔的 base64，例如 `base64 -w0 release.jks` 的輸出 |
| `OPENPRT_KEYSTORE_PASSWORD` | 金鑰庫密碼 |
| `OPENPRT_KEY_ALIAS` | 金鑰別名 |
| `OPENPRT_KEY_PASSWORD` | 金鑰密碼 |
| `PRT_API_KEY`（選用） | 內建到 APK 的 TrueTime key。**公開發佈時不要設**：任何人都能從 APK 取出 key，App 會請使用者自己輸入 |

產生簽章金鑰（自己執行，密碼不要貼到任何地方；**金鑰檔要另外備份**，弄丟後就無法發佈能覆蓋安裝的更新）：

```bash
keytool -genkeypair -keystore release.jks -alias openprt -keyalg RSA -keysize 4096 -validity 10000
```
