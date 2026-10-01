# OpenPRT 進度紀錄

## 目標

匹茲堡公車（Pittsburgh Regional Transit, PRT）乘車資訊 App。先做 Android，iOS 之後再處理。

1. 用手機定位找到使用者位置，列出附近最合適的班次並自動更新
2. 點擊班次：地圖上顯示路線、站牌、公車即時位置與預估抵達時間
3. 選擇目的地：規劃乘車路線，提供時間預估、班次與轉乘資訊

## 計畫概覽

功能清單在 `feature_list.json`，依序實作，一次一個 session。

| 階段 | 功能 | 內容 |
|---|---|---|
| 基礎 | F1 | 專案骨架、lint、測試、CI |
| 資料 | F2–F4 | TrueTime API 用戶端、GTFS 站牌匯入、附近站牌查詢 |
| 附近班次 | F5–F8 | 定位、地圖主畫面、班次排序、班次列表自動更新 |
| 班次詳情 | F9–F10 | 路線折線與站牌、即時公車位置與 ETA |
| 路線規劃 | F11–F15 | 目的地選擇、GTFS 時刻表、RAPTOR 規劃器、方案 UI、方案地圖 |
| 收尾 | F16–F17 | 離線 / 錯誤狀態、GTFS 背景更新、發佈流程 |

F11–F15 的做法取決於使用者對「路線規劃方案」問題的回答（見 `feature_list.json` 的 questions）；
如果改用 Google Directions API 或 OpenTripPlanner，F12–F13 要改寫成對應的 API 用戶端功能。

## 資料來源

- **即時資料**：PRT TrueTime，Clever Devices BusTime API v3（`getpredictions`、`getvehicles`、`getpatterns` 等），需要 API key
- **靜態資料**：PRT GTFS zip（站牌、路線、時刻表）。附近站牌查詢需要它，因為 TrueTime 的 `getstops` 必須指定路線與方向，無法依座標查詢
- 實作 F2 前先確認 API 實際網址與 GTFS 下載網址（PRT 改名後網域可能有變），並把真實回應存成測試 fixture

## 技術選擇（預設，使用者可在審核時調整）

- Kotlin + Jetpack Compose，單一 `app` 模組起步
- 不依賴 Android 的邏輯（API 解析、距離計算、班次排序、RAPTOR）寫成純 Kotlin，方便日後抽到 KMP shared 模組給 iOS
- Room 存 GTFS 資料；OkHttp/Retrofit + kotlinx.serialization 呼叫 API；WorkManager 做背景更新
- 測試：JUnit + MockWebServer + Robolectric（Compose / Room），時間一律注入 `Clock`，協程用虛擬時間
- ktlint + Android Lint；warning 要處理

## 環境（本機已確認）

- Android SDK 在 `~/Android/Sdk`。原本只有 platform `android-35`、build-tools `35.0.0`；
  F1 建置時 AGP 已自動下載 platform `android-37.0` 與 build-tools `36.0.0`
- 本機的 Java 25 **只有 JRE（沒有 javac）**。Gradle 9.8 本身可以在上面跑，
  編譯用的 JDK 21 由 foojay toolchain resolver 自動下載到 `~/.gradle/jdks`
- `local.properties`（不進 git）需要 `sdk.dir=/home/Aquila/Android/Sdk`，之後再加 `PRT_API_KEY=...`（由使用者自己填）

## 建置設定（F1 決定）

- Gradle 9.8.0、AGP 9.4.1（內建 Kotlin，不需 `kotlin-android` plugin）、Kotlin 2.4.20、Compose BOM 2026.09.00
- **compileSdk / targetSdk 37、minSdk 26**。原計畫寫 SDK 35，但目前所有 AndroidX 版本
  （core-ktx 1.19、activity 1.13）都要求 compileSdk ≥ 36/37；為了留在 35 而鎖住一年前的函式庫不划算，
  所以改用 37，README 也據此寫明需求。若使用者堅持 35 要回頭調整
- Lint 設 `warningsAsErrors = true`；ktlint 用 `android_studio` 風格，`.editorconfig` 允許 `@Composable` 用大寫函式名
- Robolectric 4.17 跑 SDK 36+ 需要 `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED`（已設在 `app/build.gradle.kts`）
- Compose ui-test 會帶入舊版 espresso 3.5（呼叫 SDK 36 已移除的 `InputManager.getInstance()`），已明確指定 espresso-core 3.7.0
- Compose 測試用 `androidx.compose.ui.test.junit4.v2.createComposeRule`（舊版已 deprecated）
- 已知、非本專案能修的警告：某個 plugin 呼叫 `Configuration.setVisible`（Gradle 11 移除），以及 Kotlin 編譯器在 JDK 25 上的 `sun.misc.Unsafe` 警告

## TrueTime 用戶端（F2 決定）

- 網址 `https://truetime.rideprt.org/bustime/api/v3/`（舊網域 truetime.portauthority.org 也還能用）；
  2026-10-01 用假 key 實測兩者都回 `Invalid API access key supplied`
- 程式在 `app/src/main/java/org/openprt/app/data/truetime/`：OkHttp 5 + kotlinx.serialization，
  不依賴 Android（只有 `TrueTimeClientFactory.kt` 讀 `BuildConfig`），之後可抽到 KMP
- 所有呼叫回傳 `TrueTimeResult`，錯誤型別：MissingApiKey / Api(messages) / Http / Timeout / Network / MalformedResponse。
  F16 要區分「key 無效」「配額用盡」時，從 `Api.messages` 判斷
- 回應同時有資料與 error（例如多站查詢中部分站無資料）時回傳資料
- 時間欄位以 America/New_York 解析成 `Instant`；vehicle 的 lat/lon/hdg 是字串，Json 設 lenient 同時接受兩種
- `getpredictions` 回應**沒有 pid**；F9 要畫 pattern 時要先用 `getVehicles(vid)` 取 `patternId`
- **測試 fixture 不是真實錄製**：沒有 API key，所以 `app/src/test/resources/truetime/*.json`
  是依 BusTime v3 文件格式手寫的。使用者填入 `PRT_API_KEY` 後，應錄製真實回應比對欄位
  （特別是 directions 的 `id`/`name`、PRT 多資料源 `rtpidatafeed` 是否必填）

## 給下一個 session 的注意事項

- 先載入 `coding-standards` skill：commit 訊息英文一行 `<type>: <description>`、功能與測試同一個 commit、版號只寫在 `gradle.properties`
- verify 指令：`./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug assembleDebug`，F1 完成前會失敗屬正常
- 需要網路或 API key 的測試：本機沒有就自動略過，CI 一定要跑
- 版號：功能寫完未經實機驗收用 PATCH；使用者驗收後才升 MINOR
- CLAUDE.md、`.claude/`、`notes/` 不進 git

## 需要實機驗收的項目（累積清單）

自動化測不到，完成對應功能後由使用者在手機上確認：

- [ ] F2 填入 `PRT_API_KEY` 後實際呼叫各端點成功，並把真實回應換成測試 fixture
- [ ] F5 首次啟動跳出定位權限對話框，允許後取得真實位置
- [ ] F6 地圖顯示匹茲堡，站牌標記位置正確
- [ ] F9 路線折線沿實際道路，上車站醒目標示
- [ ] F10 公車標記移動與實際車輛一致
- [ ] F12 完整 PRT GTFS 匯入耗時與資料庫大小
- [ ] F15 完整流程：定位 → 選目的地 → 規劃 → 看地圖 → 看即時公車
- [ ] F17 從 Release 下載 APK 安裝並啟動

## 狀態

- 2026-10-01：完成規劃，尚未實作任何功能，等待使用者審核與回答問題
- 2026-10-01：**F1 完成**（版號 0.1.0）。專案骨架、ktlint、Lint、Robolectric Compose 測試、GitHub Actions CI、README / CHANGELOG。
  verify 在乾淨副本上通過。CI 尚未在 GitHub 上實際跑過（repo 還沒推送）
  - `feature_list.json` 的 questions 仍未回答；F2 以後照 progress.md 的預設技術選擇進行，minSdk 先用 26
  - 下一步：F2 TrueTime API 用戶端，開工前先確認 API 實際網址並錄製 fixture
- 2026-10-01：**F2 完成**（版號 0.1.1，tag `v0.1.1` 只在本機）。TrueTime API 用戶端六個端點 + 錯誤型別，
  20 個 MockWebServer 測試，README 加上申請與設定 `PRT_API_KEY` 的說明。verify 通過
  - 新增依賴：OkHttp 5.5.0（含 okhttp-coroutines、mockwebserver3）、kotlinx-serialization 1.11.0、coroutines 1.11.0
  - 尚未用真實 key 驗證（見實機驗收清單）
  - 下一步：F3 GTFS 站牌與路線匯入，開工前先確認 PRT GTFS 下載網址（Developer Resources 頁面
    `https://www.rideprt.org/business-center/developer-resources/`）
