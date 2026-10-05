# OpenPRT 進度紀錄

## 目標

匹茲堡公車（Pittsburgh Regional Transit, PRT）乘車資訊 App。先做 Android，iOS 之後再處理。

1. 用手機定位找到使用者位置，列出附近最合適的班次並自動更新
2. 點擊班次：地圖上顯示路線、站牌、公車即時位置與預估抵達時間
3. 選擇目的地：規劃乘車路線，提供時間預估、班次與轉乘資訊

## 計畫概覽

### 第三輪規劃（2026-10-03）：剩下的待辦

目標「完成 OpenPRT 剩下的待辦事項」。第二輪的 F14、F15、F16、F20、F21、F25、F26 都已完成（0.1.13–0.1.25），
`feature_list.json` 再改寫成**只列還沒做的 7 項**，順序如下（已完成的說明仍在下方各段落與 git 歷史）：

| 順序 | 功能 | 內容 | 為什麼排這裡 |
|---|---|---|---|
| 1 | F24 | 地圖站牌可點擊 + 圖例 | 前幾個 session 一直寫「下一步 F24」；只動地圖與新面板，不碰規劃 |
| 2 | F22 | 起點可輸入地址、對調起訖 | 使用者已同意；F27 也要改同一塊規劃輸入區，先把起點做好 |
| 3 | F27 | Leave now / Depart at / Arrive by | 使用者 2026-10-03 新提出；需要反向 RAPTOR，是剩下最大的一項，可能要拆兩個 session（見下） |
| 4 | F23 | 步行段沿街道（FOSSGIS Valhalla） | 只改方案地圖的步行線與分鐘，獨立 |
| 5 | F17 | 輕軌 T 線 | 待使用者確認是否保留；附近班次、站牌班次（F24）、詳情都要合併兩個 feed，所以排在 F24 之後 |
| 6 | F18 | 離線 / key 失效 / 配額 / GTFS 過期 + 每週背景更新 | 要涵蓋前面所有畫面的錯誤狀態，所以放在功能都做完之後 |
| 7 | F19 | tag 觸發的 release APK | 2026-10-05 本機部分做完（0.1.32），`passes: false`：等使用者回答 GitHub repo 問題、第一次 release 在 GitHub 跑過、從 Release 安裝並啟動後才算完成 |

- verify 指令不變，2026-10-03 規劃時在本 worktree 跑過（結果見「狀態」最後一筆）
- F27 若一個 session 做不完：先做「RoutePlanner 反向搜尋 + Repository」（前兩條 steps），UI 留到下一個 session；
  `passes` 只在全部 steps 完成才改 true
- F17 / F19 被使用者刪掉時，直接從 `feature_list.json` 移除，不要留 `passes: false` 的空項目
- 完成所有功能後**仍不能升 0.2.0**：依規範要使用者實機驗收（下方累積清單）通過才升 MINOR
- iOS 預設不在這一輪（questions 第一題確認）

### 第二輪規劃（2026-10-02）：目前完成度

**還沒完成**（F14、F15、F20、F21、F26、F25、F16 已於同日完成，接著是 F24）。目標 1（附近班次）、目標 2（班次詳情 + 即時公車）的程式已完成；目標 3（路線規劃）
已能在畫面上列出方案（F15），**還不能在地圖上看方案、也不能從方案點進即時公車**（F16）。另外缺輕軌、離線處理、發佈流程，
而且 F5 以後的實機驗收一項都還沒勾。2026-10-02 在本 worktree 跑 verify 全過（271 個測試）。

`feature_list.json` 已改寫成**只列剩下的功能**，編號接續第一輪（F1–F13 已完成，不再列出），
所以下面各段落對 F1–F13 的說明仍然有效。第一輪原本的 F14–F17 重新拆成：

| 功能 | 內容 | 對應第一輪 |
|---|---|---|
| F20 | App 內輸入 TrueTime API key（首次啟動引導 + 鑰匙按鈕） | 使用者實機測試時要求新增，排在 F16 前 |
| F21 | 外觀重新設計 + 淺色 / 深色主題（含地圖樣式與 App 圖示） | 使用者 2026-10-02 要求，排在 F16 前（F16 的地圖圖層要用主題色） |
| F26 | 地圖上的公車改成公車圖示（依行車方向的箭頭） | 使用者 2026-10-02 實機試用後要求「車子的點改成公車圖示」，排在 F24 前 |
| F25 | 附近班次與班次詳情卡片化 + 方向（Inbound / Outbound）切換 + 站序時間軸 | 使用者 2026-10-02 回報「看不出怎麼切換方向、純文字單調」，排在 F24 前 |
| F24 | 地圖站牌可點擊（顯示該站班次）+ 地圖圖例 | 使用者 2026-10-02 問「站牌能點嗎、顏色代表什麼」後新增，排在 F21 後 |
| F22 | 起點也能輸入地址（From 欄位、對調起訖） | 使用者 2026-10-02 要求 |
| F23 | 步行段沿街道的實際路線（OSM 步行路線服務，失敗退回直線） | 使用者 2026-10-02 要求，排在 F16 後（畫在 F16 的方案地圖上） |
| F14 | 從 Room 建 TransitNetwork、服務日快取、跨午夜、轉乘緩衝、`TripPlanRepository` | 原 F14 的資料層（拆出來） |
| F15 | 路線規劃方案清單 UI + 首段即時時間 | 原 F14 |
| F16 | 方案地圖 + 點乘車段看即時公車 | 原 F15 |
| F17 | 輕軌 T 線（`Light Rail` feed）合併到附近班次與詳情 | 新增（F2 段落記下的缺口） |
| F18 | 離線 / key 無效 / 配額 / GTFS 過期提示 + WorkManager 每週更新 | 原 F16 |
| F19 | tag 觸發的簽章 release APK、SHA256、README badge | 原 F17 |

- F17 / F19 是否保留、是否加繁體中文介面，等使用者回答 questions；回答前照表順序做 F14–F16 不受影響
- verify 指令前面加了 `ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}`：新的 worktree 沒有 `local.properties`，
  也沒有設 `ANDROID_HOME`，不加會找不到 SDK。沒有 `PRT_API_KEY` 也能建置（呼叫時回 MissingApiKey）
- iOS 不在這一輪範圍內

### 第一輪計畫（2026-10-01，F1–F13 已完成）

| 階段 | 功能 | 內容 |
|---|---|---|
| 基礎 | F1 | 專案骨架、lint、測試、CI |
| 資料 | F2–F4 | TrueTime API 用戶端、GTFS 站牌匯入、附近站牌查詢 |
| 附近班次 | F5–F8 | 定位、地圖主畫面、班次排序、班次列表自動更新 |
| 班次詳情 | F9–F10 | 路線折線與站牌、即時公車位置與 ETA |
| 路線規劃 | F11–F13 | 目的地選擇、GTFS 時刻表、RAPTOR 規劃器 |

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
- `local.properties`（不進 git）需要 `sdk.dir=/home/Aquila/Android/Sdk` 與 `PRT_API_KEY=...`。
  **2026-10-01 使用者已提供私人 key，已寫進本 worktree 的 `local.properties`**；key 絕不能進 git、log 或對話輸出
  （用 `$(grep '^PRT_API_KEY=' local.properties | cut -d= -f2)` 帶入 curl，不要 echo）
- adb 在 `~/Android/Sdk/platform-tools/adb`（不在 PATH）

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
- **測試 fixture 大多是手寫的**（依 BusTime v3 文件），只有 `getpredictions_recorded.json` 是 2026-10-01 真實錄製
- **2026-10-01 用真實 key 實測（修正於 0.1.8）**：
  - PRT 是 multifeed site，`getdirections` / `getstops` / `getpredictions` / `getpatterns` **沒有 `rtpidatafeed` 只回錯誤**
    （`getroutes`、`getvehicles` 不需要但也接受）。F2 原本沒送，所以即時資料從來沒成功過。
    現在 `TrueTimeClient` 每個請求都帶 `dataFeed`（預設 `"Port Authority Bus"`）
  - 另一個資料源是 `"Light Rail"`（T 線）。目前只問公車，**附近是輕軌站時列表不會有 T 線班次**，之後要支援得多開一個 client 並合併
  - 缺 feed 時的錯誤訊息含 `\-`（不合法的 JSON escape），現在不會再遇到
  - 時間欄位沒有秒（`20261001 13:03`），既有的 `HH:mm[:ss]` 已能處理
  - **TrueTime `stpid` = GTFS `stop_code`（已確認）**：Steel Plaza 用 `99994` 有預測、用 stop_id `10` 是 No data found
  - 真實 pattern 515389（61D）：309 點、45 站，另有 `dtrid` / `dtrpt` 欄位（已忽略）；用暫時測試確認可解析並找到上車站 7117
  - 跑完的車（例 vid 6619）`getvehicles` 回 `No data found for parameter`

## GTFS 匯入（F3 決定）

- 下載網址 `https://www.rideprt.org/developerresources/GTFS.zip`（Developer Resources 頁面的連結，公開、不需 key）。
  2026-10-01 實測：zip 22 MB，解開 104 MB（stop_times.txt 80 MB、shapes.txt 22 MB），檔案是 **CRLF** 換行
- 真實 feed 實測：6388 個站牌（全部 location_type 0，沒有重複 ID）、102 條路線，JVM 上解析約 0.6 秒（含略過 stop_times）
- 程式在 `app/src/main/java/org/openprt/app/data/gtfs/`：`GtfsCsv.kt`（純 Kotlin CSV 解析）、`GtfsFeed.kt`
  （從 zip 串流讀 stops / routes）、`GtfsDatabase.kt`（Room entity / DAO）、`GtfsImporter.kt`（下載 + 寫入）
- **邊下載邊解析**，整份 feed 解析成功後才在單一 transaction 內「全部刪除再插入」，所以失敗時舊資料不變，
  新 feed 移除的站牌也會消失。F12 加 stop_times 時這個做法要重新評估（80 MB 全讀進記憶體太大，可能要分批寫入暫存表再切換）
- 錯誤型別 `GtfsImportError`：Http / Timeout / Network / MalformedFeed（含非 zip 內容、缺檔案、重複 stop_id）
- Room 2.8.5 + KSP 2.3.12；schema 匯出到 `app/schemas/`（進 git）。目前 version 1，上線前改 schema 可直接改，
  上線後要寫 migration
- F4 注意：stops 還沒有座標索引，F4 做邊界框查詢時要在 `StopEntity` 加 `latitude` 索引並升 schema 版本
- **stop_id 與 stop_code 不同**（例：Steel Plaza stop_id `10`、stop_code `99994`）。TrueTime 的 `stpid`
  對應哪一個尚未確認（沒有 API key），F7 合併兩邊資料前要用真實回應確認
- GTFS 測試 fixture（`app/src/test/resources/gtfs/feed/`）保留真實 feed 的 BOM 與 CRLF，`.gitattributes` 設 `-text` 避免被轉換；
  測試時才組成 zip，不把二進位檔放進 git

## 附近站牌查詢（F4 決定）

- 純 Kotlin 幾何在 `app/src/main/java/org/openprt/app/geo/Geo.kt`：`LatLng`、`haversineMeters`（球體半徑 6371008.8 m）、
  `BoundingBox.around`（球冠的精確經度半寬，保證不漏掉圓內的點；不處理跨極點 / 換日線）。
  Downtown→Oakland 實測 haversine 3780 m，對照 WGS84 Vincenty 3789 m，差 0.24%
- `NearbyStopFinder`（`data/gtfs/`）：DAO `getStopsInBox` 在 SQLite 先用邊界框篩，再以 haversine 精確過濾，
  依距離排序、距離相同時依 stop_id。回傳 `NearbyStop(stop, distanceMeters)`，距離是直線距離
- `stops` 加了 `latitude` 單欄索引，schema 升到 **version 2**（`app/schemas/.../2.json`）。
  `GtfsDatabase.create` 用 `fallbackToDestructiveMigration(dropAllTables = true)`：資料庫只放 GTFS 匯入資料，
  改 schema 時直接重建，**之後不需要寫 migration**（但升級後到下次匯入前沒有站牌，F16 的「資料過期」判斷要涵蓋空資料庫）
- 有測試用 Room query callback 抓 DAO 實際執行的 SQL 再跑 `EXPLAIN QUERY PLAN`，確認走 `index_stops_latitude`；
  已確認拿掉索引時這個測試會失敗
- 查詢沒有過濾 `location_type`（真實 PRT feed 全部是 0）。F6 / F7 若要排除 station（type 1）再加

## 定位（F5 決定）

- 程式在 `app/src/main/java/org/openprt/app/location/`：
  - `LocationProvider.kt`：純 Kotlin 介面 + `LocationResult` / `LocationError`（PermissionMissing / Unavailable / Timeout / Failed）
    與預設座標 `DOWNTOWN_PITTSBURGH`（Market Square 40.4406, -79.9959）
  - `FusedLocationProvider.kt`：`getCurrentLocation(PRIORITY_HIGH_ACCURACY)` 單次定位，`await(CancellationTokenSource)` 讓協程取消時一併取消請求。
    回傳 null（定位關閉）→ Unavailable；`SecurityException` → PermissionMissing；`ApiException` → Failed
  - `LocationViewModel.kt`：狀態 AwaitingPermission → Loading → Located / PermissionDenied / Failed。
    逾時在 ViewModel 用 `withTimeoutOrNull`（預設 10 秒），provider 本身不設逾時；PermissionDenied / Failed 都帶 downtown 座標
- 權限流程在 `MainActivity`：狀態是 AwaitingPermission 時，已授權就直接定位，否則用 `RequestMultiplePermissions` 要 FINE + COARSE，
  任一允許就算授權。旋轉後 ViewModel 狀態已不是 AwaitingPermission，不會重複跳對話框
- 錯誤訊息在 UI 層由 `LocationError` 對應到字串資源，ViewModel 不放字串。介面文字目前只有英文（語言問題仍未回答）
- 主畫面暫時只顯示定位狀態文字（含座標），F6 換成地圖時取代
- F6 注意：「重新定位」按鈕需要在 ViewModel 加 refresh；狀態中各終態的座標欄位名稱都是 `location`，
  F6 可視需要在 `LocationUiState` 加共用屬性。定位目前只取一次，沒有持續更新（F6 的「移動超過 100 m 才重查」需要加 location updates）
- `FusedLocationProvider` 沒有自動化測試（Robolectric 沒有 Play services），列入實機驗收
- 新增依賴：play-services-location 21.4.0、kotlinx-coroutines-play-services 1.11.0、lifecycle-viewmodel-compose / runtime-compose 2.11.0

## 地圖主畫面（F6 決定）

- **地圖 SDK 用 MapLibre Android 13.6.1 + OpenFreeMap `liberty` 樣式**（OSM 圖磚，免費、不需 key、不需帳號）。
  questions 第一題仍未回答，選它是因為不需要使用者先申請 Google Maps key，CI 也不需要 secret。
  地圖只在 `map/StopMap.kt` 一個 composable 裡，要換 Google Maps Compose 只改這個檔案
- 站牌與使用者位置用 GeoJsonSource + CircleLayer 畫（不是 Marker 物件），之後幾百個站牌也不會慢。
  F9 畫路線折線時照同樣方式加 LineLayer
- MapLibre 需要原生函式庫，**Robolectric 跑不起來**：`HomeScreen` 的 `mapContent` 參數讓測試換成空 Box，
  Compose 測試驗的是 testTag `"map"` 的容器與重新定位按鈕。真正的地圖畫面只能實機驗收
- `MapViewModel`：`onLocationChanged` 時，距離上次查詢點 ≥ 100 m 才重查（距離是跟「上次查詢點」比，不是上一個定位點，
  所以多次小移動累積起來也會觸發）。查詢不取消、用 CONFLATED channel 合併期間的新位置，因為第一次查詢可能正在下載 GTFS。
  查詢失敗時保留舊標記、清掉上次查詢點，下一個位置（含重新定位）就會重試
- **第一次啟動自動匯入 GTFS**：`NearbyStopRepository` 發現 `stops` 是空的就先跑 `GtfsImporter`（Mutex 防重複下載）。
  F16 的每週背景更新要和這裡共用同一個 importer / 鎖，或改成都走 WorkManager
- `OpenPrtApplication` 持有唯一的 `GtfsDatabase` / repository（Room 要求每個行程一個實例）；已在 manifest 註冊
- 持續定位：`LocationProvider.locationUpdates()`（Fused：10 秒間隔、20 m 最小位移），`LocationViewModel.followLocation()`
  只在 Located / Failed 狀態才訂閱。`MainActivity` 用 `repeatOnLifecycle(STARTED)` 呼叫，所以 App 在背景時停止定位。
  地圖跟隨每次定位更新移動鏡頭（使用者手動拖曳後，下次更新會被拉回；如果實機覺得干擾，F8 可加「拖曳後停止跟隨」）
- 「重新定位」按鈕呼叫 `relocate()`：狀態回到 AwaitingPermission，由 `MainActivity` 原本的流程重新檢查權限（未授權會再跳對話框）並取新定位
- `LocationUiState` 加了共用的 `location` 屬性（AwaitingPermission / Loading 為 null）
- 主畫面不再顯示座標文字（地圖已顯示位置），`location_located` 字串已移除
- **debug APK 現在是 63 MB**，大部分是 MapLibre 各 ABI 的原生函式庫（未量測加入前的大小）。F17 發佈時要用 ABI split 或 App Bundle
- 測試寫法提醒：`runTest` 的 `advanceUntilIdle` **不會執行 `backgroundScope` 的協程**，要長時間收集的協程用一般 `launch` 並在結尾 `cancel()`
- 順手處理 F5 留下的編譯警告：`await(CancellationTokenSource)` 是 experimental API，已加 `@OptIn`

## 班次排序（F7 決定）

- 程式在 `app/src/main/java/org/openprt/app/departures/DepartureRanker.kt`，純 Kotlin（只依賴 `java.time` 與 TrueTime 的 `Prediction`）
- 輸入是 `WalkableStop(stopId, distanceMeters)` 清單與 `Prediction` 清單，用 `stopId` 字串相等配對；不在清單內的站牌的預測直接忽略。
  **F8 呼叫前要把 GTFS 站牌轉成和 TrueTime `stpid` 相同的 ID**（stop_id 或 stop_code 仍未確認，見 F3 段落），
  ranker 本身不處理對照
- 步行時間 = 直線距離 / 1.2 m/s，**無條件進位到整秒**；速度可由建構子覆寫（必須 > 0）
- 「趕得上」：抵達站牌時間 ≤ 預測時間（剛好同時算趕得上，沒有額外緩衝）；已經過去的預測自然被排除
- 「最合適」的定義：**依公車預測時間由早到晚**（不是依「到站後等待時間」，否則會偏好遠站的晚班車），
  同時間再依步行時間、路線、方向、stop_id 排，確保輸出穩定。同一路線同方向（`route` + `routeDirection`）只留排序後第一筆
- 輸出 `RankedDeparture`：`walkTime`、`timeUntilDeparture`（現在到公車抵達）、`spareTime`（到站後還要等多久），F8 顯示「x 分」用 `timeUntilDeparture`
- 「現在」一律來自注入的 `java.time.Clock`，測試用 `Clock.fixed`
- 沒有考慮 `Prediction.type`（ARRIVAL / DEPARTURE）的差異，也沒有考慮實際步行路徑；若實機覺得太樂觀，可在 F8 加步行時間緩衝

## 附近班次列表（F8 決定）

- 程式在 `departures/NearbyDeparturesViewModel.kt`（狀態與自動更新）與 `departures/DeparturesPanel.kt`（Compose 列表）
- **GTFS → TrueTime 站牌 ID 用 `stop_code`，沒有 code 時退回 `stop_id`**（`StopEntity.trueTimeStopId`）。
  2026-10-01 已用真實 key 確認 stop_code 正確（見 F2 段落）
- `MapUiState` 加了 `walkableStops`（TrueTime ID + 距離）。`MainActivity` 只在 `stopsStatus == Ready` 時轉給
  departures ViewModel，避免站牌還沒載入前就顯示「附近沒有公車」
- 每次更新只問**最近的 10 個站牌**（`MAX_IDS_PER_CALL`），一次 API 呼叫；30 秒一次一天 2880 次，在 BusTime 預設每日額度內。
  400 m 內超過 10 個站牌時（Downtown），較遠站牌的班次不會出現
- `autoRefresh()` 是 suspend 函式，`MainActivity` 用 `repeatOnLifecycle(STARTED)` 呼叫：背景時停止，回前景立刻更新一次。
  站牌改變時 `collectLatest` 重新開始（立刻查新站牌、重新計時 30 秒）
- TrueTime 回 `No data found` 錯誤（所有站都沒有預測）視為空列表，不是失敗。其他錯誤保留舊列表與 `lastUpdated`
- 分鐘數：到站時間**無條件捨去**（跟站牌顯示一致），步行時間**無條件進位**。分鐘數在每次更新時計算，兩次更新之間最多舊 30 秒
- 失敗訊息只分兩種：沒有 API key、其他。F16 再細分離線 / key 無效 / 配額
- 主畫面改用 `BottomSheetScaffold`（peek 240 dp），重新定位按鈕移到內容區右下角
- 測試寫法提醒：`TestLifecycleOwner` 的 `currentState` setter 會 `runBlocking` 在它的 dispatcher 上，
  傳 `StandardTestDispatcher` 會**卡死**，要用 `UnconfinedTestDispatcher(同一個 scheduler)`
- 新增測試依賴：`androidx.lifecycle:lifecycle-runtime-testing`

## 班次詳情（F9 決定）

- 程式在 `app/src/main/java/org/openprt/app/details/`：
  - `RouteShape.kt`：純 Kotlin，`Pattern.toRouteShape(boardingStopId)` 依 seq 排序，折線含所有點（站牌也在路上），
    站牌清單略過 waypoint；上車站用 `stopId` 字串相等找第一次出現（環狀路線經過兩次時取第一次），找不到時為 null
  - `DepartureDetailsViewModel.kt`：`state` 為 null 表示顯示附近列表。`open(departure)` 先 `getVehicles(vid)` 取 `patternId`
    （predictions 沒有 pid）再 `getPatterns(pid)`；「No data found」或回應裡沒有該車 / pattern 視為 `NotFound`（車已跑完），
    其他錯誤為 `Failed`。`close()` 取消載入中的請求。目前沒有重試按鈕，返回再點一次即重試
  - `DepartureDetailsPanel.kt`：bottom sheet 換成詳情（返回箭頭、路線、目的地、上車站與步行分鐘、沿線站牌清單，
    清單一開始捲到上車站，上車站粗體 + 「Board here」）
- `DepartureItem` 加了 `stopId`（TrueTime ID）與 `vehicleId`
- 導覽沒有用 Navigation 函式庫：只是 `HomeScreen` 依 `detailsState` 切換 sheet 內容與地圖圖層，系統返回鍵用 `BackHandler`。
  所以返回時 `LocationViewModel` 完全不受影響、不會重新定位；附近班次在背景照常每 30 秒更新，回到列表時是新的
- 地圖：詳情時隱藏附近站牌，改畫路線（深藍 LineLayer）、沿線站牌（白底小圓）、上車站（橘色大圓），使用者藍點在最上層。
  鏡頭 fit 整條路線（四周 48 dp 邊距），此時**不再跟隨定位**；關閉詳情後回到跟隨。
  bottom sheet 會蓋住路線下半部（邊距沒有扣掉 sheet 高度），實機若覺得被遮太多再調
- `orEmptyWhenNoData()` 從 departures 移到 `TrueTimeResult.kt` 共用
- F10 注意：詳情狀態目前只有「點擊當下」的 `DepartureItem` 快照（分鐘數不會更新）。F10 要在 `DepartureDetailsViewModel`
  加 15 秒輪詢（`getVehicles` + `getPredictions`），`TripSource` 需要加 predictions；`Vehicle` 已有 `distanceAlongPatternFeet`，
  可對照 `PatternStop.distanceAlongPatternFeet` 判斷「已過站」

## 即時公車位置與 ETA（F10 決定）

- `DepartureDetailsViewModel` 改成和附近列表同樣的模式：`open()` / `close()` 只改選擇，實際請求在 suspend 的
  `autoRefresh()`，`MainActivity` 用 `repeatOnLifecycle(STARTED)` 呼叫，**背景時不輪詢**、回前景立刻更新。
  選擇用 identity 比較的 `Selection` 包起來，關掉再打開同一班車也會立刻重新開始；寫入狀態前檢查選擇沒變，舊請求不會蓋掉新狀態
- 每 15 秒：`getVehicles(vid)` + `getPredictions(上車站)` 兩次呼叫（詳情開著時每分鐘 8 次，加上列表 2 次）。
  路線只載入一次；**Failed / NotFound 的路線會在下次更新自動重試**（不再需要返回再點）
- 狀態 `LiveBus(position, arrival, lastUpdated, error)`：`Arrival` 為 Loading / Expected(minutes, delayed) / Departed。
  分鐘數 = `prdtm - clock` 無條件捨去、不小於 0，和列表一致
- 「已離站」：車輛的 `distanceAlongPatternFeet` 大於上車站在 pattern 上的距離（`RouteShape.boardingDistanceFeet`），
  或 predictions 裡沒有這台車在這站的預測（含 No data found）。不是黏著狀態，每次更新重新判斷。
  環狀路線經過上車站兩次時只看第一次
- 車輛不再回報時 `position` 為 null（地圖不畫公車），到站狀態仍由 predictions 決定
- 部分失敗時各欄位各自沿用舊值，`error` 取第一個失敗；`lastUpdated` 只在兩個呼叫都成功時更新
- 地圖：公車是綠色大圓點（`bus-layer`，在上車站之上、使用者藍點之下），沒有畫方向箭頭；鏡頭不跟隨公車
- 時間顯示用 `FormatStyle.MEDIUM`（含秒），因為更新間隔小於一分鐘

## 目的地選擇（F11 決定）

- **地理編碼用 Photon**（`https://photon.komoot.io/api/`，komoot 架的 OSM 地理編碼）：免費、不需 key，專為邊打字邊搜尋設計。
  沒選 Nominatim 是因為它的使用政策**禁止 client 端自動完成**；沒選 Android `Geocoder` 是因為結果品質依裝置而異、無法在 JVM 測。
  請求帶 `User-Agent: OpenPRT/<版號>`、`lang=en`、`limit=10`、`bbox`（Photon 順序是 minLon,minLat,maxLon,maxLat）。
  Photon 是公共服務、只有 fair use 限制，若之後使用量大或被限流，要換自架 Photon 或付費服務（只改 `PhotonGeocoder`）
- 程式在 `app/src/main/java/org/openprt/app/destination/`：`Geocoder.kt`（介面、`Place`、`GeocodeResult` / `GeocodeError`：
  Http / Timeout / Network / MalformedResponse）、`PhotonGeocoder.kt`、`DestinationViewModel.kt`、`DestinationSearch.kt`（Compose）
- 地點名稱：有 `name` 用 name，沒有就用「門牌 街名」，兩者都沒有的結果略過。說明 = 地址（有 name 時）、locality、city。
  2026-10-01 實測：搜尋地址時 Photon 常把同門牌的公車站或建築物排前面（例「5000 forbes ave」第一筆是公車站）
- 匹茲堡地區 = `geo/Geo.kt` 的 `PITTSBURGH_AREA`（Allegheny County 外擴取整：lat 40.19–40.68、lon −80.37 – −79.68）。
  `bbox` 只是給 Photon 的提示，**ViewModel 會再過濾一次**，任何 Geocoder 實作的區外結果都不會顯示。長按地圖**不限區域**
- `DestinationViewModel` 用 `viewModelScope` + CONFLATED channel + `collectLatest`：打字 debounce 300 ms，新的輸入取消進行中的請求；
  `retry()` 不 debounce；選地點 / 長按 / 清空輸入會送 null 取消搜尋。結果寫入前再檢查「還在等這個 query」，避免取消訊號還沒送到時舊結果蓋掉新狀態
- 狀態 `DestinationUiState(query, search: Idle/Searching/Results/Failed, destination: Destination(name?, location)?)`。
  長按產生的目的地 `name` 為 null，畫面顯示「Pinned spot (lat, lon)」（沒有反向地理編碼）。選定後搜尋框清空，下方顯示「To: …」與清除按鈕
- `HomeScreen` 多了 `destinationState` 與 `destinationActions: DestinationActions`（介面，ViewModel 實作），避免再加五個 lambda 參數
- 地圖：目的地是紅色大圓點（`destination-layer`，在上車站之上、公車之下）；長按用 `addOnMapLongClickListener`（`rememberUpdatedState` 保持最新 callback）。
  有目的地且不在詳情模式時，鏡頭 fit「目前中心 + 目的地」，每次定位更新都會重新 fit（取代原本的 zoom 16 跟隨）
- **F14 要接的地方**：規劃的起點用 `locationState.location`、終點用 `destinationState.destination.location`。
  目前目的地只存在 ViewModel 記憶體（旋轉保留、行程被殺就消失）

## GTFS 時刻表（F12 決定）

- 新表：`trips`、`stop_times`、`calendar`、`calendar_dates`（`GtfsDatabase.kt`），schema 升到 **version 3**（舊版升級後 stops 被清空，第一次查站牌時會自動重新匯入）。
  日期用 `GtfsConverters` 存成 epoch day；`stop_times` 主鍵 (tripId, stopSequence)（也供「某班次依序的站」`getStopTimesOfTrip`），
  另有 (stopId, departureSeconds) 索引供「某站某時間後的發車」
- **匯入流程改寫**：`GtfsImporter(database, downloadDir)` 先把 zip 下載到 `cacheDir` 暫存檔（下載中不碰資料庫，完成後刪除），
  再用 `ZipFile` 逐表**串流解析、每 1000 筆寫入一次**，整個「deleteAll + 全部重新插入」包在 `database.withTransaction` 裡，
  任何格式錯誤或重複主鍵都 rollback，舊資料不變。F3 的「先全部解析進記憶體再寫入」與 `GtfsFeed` / `GtfsStop` / `GtfsRoute` 已移除，
  `GtfsFeed.kt` 現在只放 row → entity 的解析函式。`dao.replaceAll` 已移除（測試改用 `insertStops`）
- 必要檔案：stops / routes / trips / stop_times，calendar 與 calendar_dates **至少一個**
- 真實 PRT feed 觀察（2026-10-01）：stop_times 100 萬筆、trips 18817、**所有列都有 arrival/departure 時間**（不需要內插，程式也不支援空白時間，遇到會整份拒絕）、
  arrival 一律等於 departure、25467 筆超過 24:00:00、`pickup_type=1` 7641 筆（終點站）、`drop_off_type=1` 8195 筆（起點站）。
  calendar 只有 8 個 service：平日 / 週六 / 週日 / 一個只在國慶日跑的週六班表，calendar_dates 用來停駛（例：勞動節停平日班）
- `stop_times` 存 `pickupAllowed` / `dropOffAllowed`（type 1 = 不行，2 / 3 視為可以），F13 RAPTOR 上下車要用；`departuresAfter` 已排除不能上車的列
- 查詢：`GtfsTimetable.departuresAfter(stopId, serviceDate, afterSeconds, limit = 20)`，回傳 `ScheduledDeparture`（`departureTime` 轉成 `Instant`）。
  `activeServiceIds(date, calendars, calendarDates)` 是純 Kotlin；`serviceTime(date, seconds)` 照 GTFS 規定從「當天中午減 12 小時」起算（夏令時間結束那天 00:00:00 是當地 01:00）。
  時區常數 `PRT_TIME_ZONE`（TrueTime DTO 另有一個 private 的同值常數）
- **F13 注意**：查詢一次只看一個 service day。凌晨查詢要另外問「前一個 service day、秒數 +86400 之後」，前一天的深夜班次才不會漏掉。
  RAPTOR 需要依路線分組的 trip pattern，目前沒有這種查詢，F13 可能要在匯入後建索引表或啟動時建記憶體結構
- **量測（JVM，Robolectric 本機 SQLite，2026-10-01）**：完整 PRT feed（22.5 MB zip）匯入 **3.9 秒**（不含網路下載，MockWebServer 供檔），
  資料庫 **74.7 MB**（checkpoint 後，WAL 0），stop 17347 平日 10:00 後的發車查詢 12 ms，`EXPLAIN QUERY PLAN` 確認走 `index_stop_times_stopId_departureSeconds`。
  **模擬器量測失敗**：本機 AVD `ge_test`（android-35 google_apis x86_64）在本 session 的環境中一啟動就 segfault（exit 139，swiftshader 與 guest GPU 都一樣），
  所以手機上的匯入時間尚未量測，列入實機驗收。量測用的是暫時測試，未進 git
- 75 MB 偏大：大部分是 stop_times（tripId / stopId 字串 + 兩個索引）。若手機空間或匯入時間有問題，可把 tripId / stopId 換成整數鍵
- Fixture：`app/src/test/resources/gtfs/feed/` 新增 trips / stop_times / calendar / calendar_dates（LF 換行、沒有 BOM，跟真實 feed 這四個檔一樣）。
  情境：平日 WK（T1 / T2 / 深夜 T4 跑到 25:10）、週六 SA（T3），2026-09-07 勞動節停 WK、加開 SA

## 路線規劃引擎（F13 決定）

- 程式在 `app/src/main/java/org/openprt/app/planner/`，純 Kotlin（只依賴 `geo/` 與 `DEFAULT_WALKING_SPEED_METERS_PER_SECOND`），JVM 測試即可：
  - `TransitNetwork.kt`：**一個服務日**的記憶體網路。輸入 `TransitStop` + `ScheduledTrip(TripStop…)`，建構時把班次依「routeId + 站序」分成 pattern
    （pattern 內依首站發車排序，**假設同 pattern 不超車**），並用緯度排序掃描預先算好 400 m 內的步行轉乘
  - `RoutePlanner.kt`：標準 RAPTOR（每輪 = 多搭一段車）。起訖步行上限 800 m、直線距離 / 1.2 m/s 無條件進位、`maxRides` 預設 3。
    只有比「搭車段數更少的方案」**嚴格更早**抵達才收，所以結果是（抵達時間, 搭車段數）的 Pareto 集合，同時抵達時偏好轉乘少的；最多 3 個（每種段數一個），依段數由少到多
  - `Itinerary.kt`：`WalkLeg`（from / to 為 null 代表起點 / 終點）、`RideLeg`、`PlanResult.Found / NoRoute(NO_STOP_NEAR_ORIGIN | NO_STOP_NEAR_DESTINATION | NO_CONNECTION)`
- 時間全部是「服務日開始後的秒數」（和 GTFS 一樣可超過 24h），轉成 `Instant` 用 F12 的 `serviceTime`
- 第一段步行設計成「剛好在公車發車時走到站」，所以 `Itinerary.departureSeconds` 可能晚於查詢時間；轉乘步行與最後步行從下車時開始
- 不規劃純步行方案（起訖很近時也一定要搭車）；方案不會以兩段連續步行結尾（只從「搭車到達」的站算最後步行）
- **轉乘沒有緩衝**（F14 已加上 60 秒，見 F14 段落）：下車那一秒就能上下一班。真實 feed 上 Mt Lebanon → Pitt 出現「多轉一次只早 1 分鐘、轉乘時間 1 分鐘」的方案，
  F14 實機看結果時若覺得不可靠，可在 `scanPattern` 上車判斷加最小轉乘秒數（同站與步行轉乘都要加）
- **量測（暫時測試，未進 git，2026-10-02，JVM）**：真實 PRT feed 2026-10-01（週四）5588 班次 → 257 個 pattern，建網路 92 ms；
  Market Sq → CMU 24 ms（69 直達）、Squirrel Hill → North Shore 7 ms（61C + Blue Line）、Mt Lebanon → Pitt 4 ms（Red Line + 61A）。
  手機上的時間尚未量
- **F14 要接的地方**：
  - 還沒有從 Room 建 `TransitNetwork` 的程式。需要「某服務日的 active trips + 它們的 stop_times」查詢（`activeServiceIds` 已有），
    一天約 5600 班、30 萬筆 stop_times，建議在背景執行緒建好後依服務日快取
  - 跨午夜：凌晨查詢要另外用前一個服務日的網路（秒數 +86400）查一次，合併結果
  - 首段公車的即時時間要另外用 TrueTime 查（RideLeg 有 routeId / from.stopId；TrueTime stpid = stop_code，`TransitStop.stopId` 目前是 GTFS stop_id，要對照）

## 規劃資料層（F14 決定）

- `RoutePlanner` 加 `minTransferSeconds`（預設 `DEFAULT_MIN_TRANSFER_SECONDS = 60`）：每站另存 `boardable`（可以上車的時間）。
  搭車到站 = 到站 + 60 秒；步行轉乘 = max(走到的時間, 下車 + 60 秒)，所以步行超過 60 秒時不再多加；
  從起點走到的站沒有緩衝（「直達不受影響」）
- `TransitStop` 加 `trueTimeStopId`（預設 = stopId）。`StopEntity.trueTimeStopId`（code ?: stop_id）從 `departures/` 移到 `GtfsDatabase.kt`，
  F15 查首段即時預測用 `rideLeg.from.trueTimeStopId`
- `data/gtfs/TransitNetworkSource.kt`：`fun interface TransitNetworkSource`（測試用計數 fake 包真實實作）與 `RoomTransitNetworkSource`。
  沒有站牌（還沒匯入）回 null；當天沒有服務回「沒有班次的網路」（結果是 NoRoute NO_CONNECTION，F18 的「GTFS 過期」要另外判斷）。
  只有一個站、或經過 stops.txt 沒有的站的班次會被略過，不讓整天規劃失敗
- **查詢效能（真實 feed、JVM、2026-10-02）**：原本用 `trips JOIN stop_times` 會掃整個 1M 筆 stop_times，一天 31 萬筆要 4.5 秒；
  改成 `stop_times WHERE tripId IN (SELECT … FROM trips WHERE serviceId IN …)` 走主鍵 + 另外查 trips，**第一次規劃約 1.4 秒**（含建網路 ~150 ms），
  之後每次 3–7 ms。手機上會更慢，F15 量（可考慮在選目的地前預先建網路）
- `data/gtfs/TripPlanRepository.kt`：`plan(origin, destination, departAt: Instant)` 回傳 `TripPlanResult`：`Found(plans)` / `NoRoute(reason)` / `NoTimetable`。
  `TripPlan(serviceDate, itinerary)` 的秒數要用 `timeOf(seconds)` 轉 `Instant`（不能直接加在今天上）
  - 依服務日快取 `RoutePlanner`，Mutex 保護（同時兩個請求只建一次）。每次請求只保留這次需要的服務日，所以最多兩個網路在記憶體
  - 跨午夜：前一個服務日開始後 30 小時內（當地清晨 6 點前）也查前一天（真實 feed 最晚 26:43）。兩天結果合併後用同樣的 Pareto 規則（段數少優先，段數多的要嚴格更早到）
  - 真實 feed 驗證：00:30 Mt Lebanon → Pitt 自動選到隔天早班車；Market Sq → CMU 00:30 用前一天的 61C 深夜班
  - 搜尋在 `Dispatchers.Default` 執行；GTFS 更新後快取不會失效（F18 處理）
- `OpenPrtApplication.tripPlanRepository` 已建好，F15 的 ViewModel 直接用

## 規劃方案清單（F15 決定）

- 程式在 `app/src/main/java/org/openprt/app/trip/`：`TripPlanViewModel.kt`（狀態、`TripOption`、`toOption`）與 `TripPlansPanel.kt`（Compose）
- `TripPlanRepository` 實作新的 `fun interface TripPlanSource`（在 `TripPlanRepository.kt`），ViewModel 測試用 fake
- 狀態 `TripPlanUiState?`：null = 沒有目的地（sheet 顯示附近班次）；`Planning` / `Results(options)` / `NoRoute(reason)` / `NoTimetable`（有「Try again」按鈕 → `retry()`）
- **何時規劃**：`MainActivity` 把 `destinationState.destination?.location` 與 `locationState.location` 分別交給 `onDestinationChanged` / `onLocationChanged`。
  只有「目的地改變」或「目的地已設、位置從 null 變成已知」（第一次定位、按重新定位後）才規劃；**走動時不重新規劃**，方案清單不會一直跳。
  出發時間 = 注入的 `Clock` 的現在。清除或換目的地時 `cancel` 進行中的 job（都在 Main，取消後不會再寫入狀態）
- 定位被拒 / 失敗時用的是 Downtown 預設座標當起點（`LocationUiState` 的 location），沒有另外提示
- **即時首班車**：方案到了以後一次 `getpredictions`（所有方案首段上車站的 `trueTimeStopId` 去重，最多 3 個站，一次呼叫）。
  配對規則：同路線（`Prediction.route == RideLeg.routeId`，PRT 的 GTFS route_id 等於 TrueTime rt）、同站、
  使用者「現在出發走得到」（預測 ≥ 現在 + 首段步行時間）、與時刻表上車時間相差 ≤ 15 分鐘，取最接近時刻表的一筆。
  採用時：上車時間 = 預測、出發時間 = 預測 − 首段步行；**抵達時間仍是時刻表**（後面幾段沒有即時資料），總分鐘數 = 抵達 − 出發，
  所以公車誤點時總分鐘數會變短，若實機覺得誤導，直達方案可改成抵達也加上誤點分鐘。
  預測失敗（含沒有 key）時退回時刻表，不讓規劃失敗；No data found 視為沒有預測。即時時間**只在規劃當下查一次**，不會自動更新
- 輕軌首段：TrueTime 公車 feed 沒有輕軌站，配不到 → 顯示 scheduled（F17 再處理）
- 列表：每列 = 總分鐘數（粗體）、「出發 – 抵達」、各段（Walk n min › 61C › …，FlowRow 讓長行程換行）、轉乘次數（`plurals`）、
  「61C leaves <站> at <時間> · Live / (scheduled)」。步行分鐘無條件進位，0 秒的步行（起點就在站牌）不列
- 地圖這一版沒有變（仍顯示附近站牌與目的地紅點），F16 才畫方案；點方案目前沒有反應。`TripOption.plan` 保留給 F16
- 第一次規劃的耗時（JVM 1.4 秒）期間顯示「Planning your trip…」；手機上的時間列入實機驗收
- 測試：`TripPlanViewModelTest`（16）、`TripOptionTest`（12）、`TripPlansPanelTest`（10）、`HomeScreenTest` +1。
  Compose 測試不比對時間字串（JDK 的 `FormatStyle.SHORT` 在 AM/PM 前用的空白字元會因版本不同）

## App 內 API key（F20 決定）

- 使用者 2026-10-02 實機測試時要求：不該要在電腦上寫 `local.properties` 才能用即時資料。新增 F20 排在 F16 前（feature_list.json 原有功能不變）
- `data/settings/ApiKeySettings.kt`：SharedPreferences 檔 `truetime`（`api_key`、`onboarding_done`），manifest 已 `allowBackup=false`，不會備份。
  沒用 EncryptedSharedPreferences（已 deprecated），也沒加 DataStore 依賴；key 只是個人的 TrueTime key，App 私有儲存就夠
  - App 內存的 key 優先於 `BuildConfig.PRT_API_KEY`（`local.properties` 現在只是開發用預設值，有它就不顯示歡迎畫面）
  - `needsOnboarding` = 沒有任何 key 且沒按過 Skip
- `TrueTimeClient` 的 `apiKey` 改成 `() -> String`，**每次請求讀目前的 key**：存 key 後下一次請求就生效，不用重建 client 或重開 App。
  `TrueTimeClient.fromSettings(settings)` 取代 `fromBuildConfig()`。附近班次最多 30 秒後才會用新 key 更新（沒有立即觸發）
- `data/truetime/ApiKeyChecker.kt`：用一次 `getroutes` 驗證。Api 錯誤訊息含「key」→ Rejected（顯示 TrueTime 原文），
  其他 Api 錯誤（例如配額）/ 網路 / 逾時 / HTTP → Unreachable，畫面提供「Save without checking」
- `settings/ApiKeyViewModel.kt` + `ApiKeyScreen.kt`：首次啟動是「Welcome to OpenPRT」+「Skip for now」（略過會記住，下次不再出現）；
  從主畫面右上角鑰匙圖示打開時是「TrueTime API key」+「Cancel」（取消不改已存的 key）。系統返回鍵 = Skip / Cancel。
  「Open PRT TrueTime」用 `LocalUriHandler` 開 `https://truetime.rideprt.org/bustime/home.jsp`
- `MainActivity`：key 畫面顯示時整個取代 `HomeScreen`，但定位 / 站牌 / 班次的 ViewModel 照常在背景載入；
  **定位權限對話框等 key 畫面關掉才跳**，避免兩個畫面疊在一起
- 實機（Galaxy S23，SM-S9180，Android 16）安裝後確認首次啟動出現歡迎畫面（截圖）；輸入真實 key 的流程待使用者驗收
- 新增 37 個測試（全部 366 個）

## 外觀與深色主題（F21 決定）

- 方向（使用者選的建議）：PRT 深藍 `#17365F` + 金黃 `#FFC72C`，中性色用藍灰（取代 Material 預設的紫粉色調）
- `ui/theme/Color.kt`：完整寫死 light / dark `ColorScheme`（所有 surfaceContainer 也寫），`OpenPrtColors`（`LocalOpenPrtColors`）放
  Material 沒有的品牌色：top bar（淺色是深藍、深色是一般深色表面）、金黃 accent（定位 FAB）。即時 = `tertiary`（綠），誤點 = `error`
- `ui/theme/Theme.kt`：`OpenPrtTheme(mode)`、`ThemeMode`（SYSTEM / LIGHT / DARK）、`ThemeMode.isDark(systemDark)`
- `data/settings/AppearanceSettings.kt`：SharedPreferences 檔 `appearance`，未知值讀成 SYSTEM。切換入口是 top bar 的半圓（contrast）圖示 → 下拉選單
- `map/MapPalette.kt`：地圖樣式網址與所有標記顏色（字串，MapLibre 用），`mapPalette(dark)`；深色用 OpenFreeMap `dark` 樣式。
  **F24 的圖例要用這份**。
  **深色地圖改用 `fiord`（0.1.17）**：使用者實機回報 `dark` 樣式太暗（背景 rgb(12,12,12)、道路 #181818，幾乎分不出來）；
  `fiord` 背景 `#45516E`、道路 hsl(224,22%,45%)、路名 hsl(223,31%,61%)，實機截圖確認道路、建築、路名都看得清楚。
  路線與站牌在深色改成 `#D5E3FF`（比 fiord 的道路亮很多），站牌外圈 `#111318``StopMap` 新增 `palette` 參數，palette 變了就 `setStyle` 重新載入（會清掉圖層，所以重加圖層後由各 LaunchedEffect 重填資料）
- `ui/RouteBadge.kt`：路線編號色塊（primary 底），用在附近班次、班次詳情、方案清單的每一段
- 系統列：`MainActivity` 依主題呼叫 `enableEdgeToEdge`；首頁 top bar 在淺色也是深藍，所以狀態列圖示一律白色，只有 API key 畫面（淺色）用深色圖示。
  視窗底色 `values` / `values-night` 的 `Theme.OpenPRT`，避免啟動時閃白（只跟系統深色模式，App 內手動選的不影響這一瞬間）
- App 圖示：深藍底 + 金黃圖釘 + 深藍公車；`ic_launcher_monochrome`（evenOdd 挖空公車）給 Android 13 主題圖示
- 對比測試 `ThemeContrastTest`：兩套主題 9 組前景 / 背景都 ≥ 4.5:1
- 實機（Galaxy S23，系統深色模式）確認：深色地圖、金黃 FAB、路線色塊、即時班次正常；從選單切 Light 後 top bar 深藍、地圖換淺色、路線深藍、上車站金黃；
  測完已把 App 設回 System default。**手機的系統深色模式沒有動**
- 新增 37 個測試（全部 403 個），lint 0 issue

## 公車圖示（F26 決定）

- 公車用兩個 SymbolLayer 疊在同一個 `bus` source：下層 `bus-heading-layer` 是公車色的小三角形，
  依 Feature 的 `heading` 屬性（TrueTime `hdg`，北為 0、順時針）旋轉、對齊地圖；上層 `bus-layer` 是圓形公車徽章（白外圈 + 公車圖形），**保持正立**
  （整個公車圖形跟著轉，往南時會倒過來，較難辨認）
- 圖示由程式畫成 bitmap（`map/BusMarker.kt`），顏色取 `MapPalette.bus` / `busGlyph` / `markerOutline`，主題換了 style 重載時重新 `addImage`
- `res/drawable/ic_bus.xml` 是 Material Symbols `directions_bus`（Apache 2.0），F25 的時間軸與 F24 的圖例沿用
- `MapPalette.busGlyph`：淺色白（#188038 底）、深色深綠 `#0D3B1E`（#81C995 底，白色太淡）；測試要求圖形對比 ≥ 3:1（WCAG 非文字）
- 新增 4 個測試（全部 407 個），verify 通過。手機當時斷線，之後和 F25 一起裝上（0.1.19），實機截圖確認公車徽章出現在路線上

## 站牌圖示（0.1.34，使用者 2026-10-05 實機回饋）

- 使用者裝了 v0.1.33 後回報「公車站牌只是一個點，應該改成簡單直覺的圖標」；不在 `feature_list.json`，照回饋直接做
- 附近站牌改成圓角方形站牌（`map/StopSign.kt` 程式畫 bitmap，沿用 `ic_bus` 圖形，和其他地圖 App 的公車站圖示同一個樣子）；方形是為了和圓形的公車徽章分開。
  上車站、點選的站、方案的上車站改成較大的金色站牌（30 dp，附近站牌 22 dp）。**路線沿途的站與方案下車站維持圓點**：每站都畫站牌會蓋住路線
- 新增 `MapPalette.stopGlyph` / `boardingStopGlyph`：淺色白 / 深藍 `#17365F`（白色在金色上只有約 2:1），深色都是 `#111318`；
  4 個對比 ≥ 3:1 的測試。圖例新增 `STOP_SIGN` / `LARGE_STOP_SIGN`，測試比對圖例與地圖同色
- 站牌圖示全部畫出（`iconAllowOverlap` + `iconIgnorePlacement`），因為每個都能點；點擊判斷不變（48 dp 觸控範圍比圖示大）
- 用本機 release 金鑰建置 0.1.34 的 arm64 APK，`adb install -r` 覆蓋手機上的 Release 版（簽章相同，資料保留），深色主題截圖確認站牌圖示與點選後的金色站牌正常

## 起點與終點標記（0.1.35，使用者 2026-10-05 實機回饋）

- 使用者回報：起點改成 Cathedral of Learning 後地圖只看到終點與自己的位置，看不到起點；要一個起點顏色圖示，終點紅色可以但圖示要換
- 起點：`StopMap` 新參數 `origin`（`DestinationUiState.origin`，My location 時是 null），畫成紫色（淺色 `#8E24AA`、深色 `#CE93D8`，和其他標記都不同色）
  圓點加白框、白色中心，用兩個 CircleLayer，不用 bitmap
- 終點：紅色水滴形圖釘（`map/PlaceMarkers.kt` 的 `pinBitmap`），`iconAnchor` 在底部，針尖對準地點
- 鏡頭：沒有方案時框住 `cameraPoints(center, origin, destination)`，有選起點時用起點取代使用者位置（5 個測試）
- 圖例新增 `RINGED_CENTER_DOT`（起點）與 `PIN`（目的地）；共 786 個測試，verify 通過。
  裝到手機，深色主題實際操作 Cathedral of Learning → CMU，截圖確認紫點、紅色圖釘與鏡頭範圍正確

## Apple 簡約風（0.1.37，使用者 2026-10-05 要求）

- 使用者要「參考蘋果簡約風重新設計」，並把 README 改成英文（面向匹茲堡使用者）。取代 F21 的 PRT 深藍 + 金黃：
  只留**一個藍色重點色**（淺色 `#0066CC`、深色 `#4DA3FF`，是 iOS 系統藍調暗到 AA），金色只剩「上車站」（地圖上車站、時間軸、Board here）
- 顏色在 `ui/theme/Color.kt`：iOS grouped 背景（淺色 `#F2F2F7` 底 + 白卡片、深色黑底 + `#1C1C1E` 卡片），次要文字 `#636366` / `#AEAEB2`，
  `surfaceTint` 等於 surface，浮起來的元件靠陰影不靠染色；`ThemeContrastTest` 的 28 組對比都過
- `ui/theme/Type.kt`：標題 SemiBold、字距略收；圓角 6 / 10 / 12 / 16 / 24 dp。字型仍用手機的（SF Pro 不能隨 Android App 散布）
- 元件：卡片拿掉外框；上方列白底（深色黑底）黑字；地圖兩顆按鈕改成白色圓形、藍色圖示；搜尋框白卡片加陰影；
  Leave now / Depart at / Arrive by 改成 iOS 分段控制（灰色軌道、選中的是白色、沒有勾勾）
- 狀態列圖示改成跟主題（`SystemBarStyle.auto`）：原本上方列是深藍所以一律白色圖示，改白底後看不到
- 卡片內距試過 16 dp，Robolectric 預設小螢幕上班次詳情的「Board here」被擠出畫面（2 個測試失敗），維持 12 dp
- 站牌圖示在 zoom 12–15 之間從 0.5 倍放大到原尺寸，規劃方案縮小地圖時不再疊成一團
- CHANGELOG 從 0.1.37 起用英文寫（release notes 給匹茲堡使用者看），舊段落維持中文

## 卡片化介面與方向切換（F25 決定）

- 共用元件 `ui/Cards.kt`：`InfoCard`（surface 底 + outlineVariant 外框，兩種主題都分得開）、`MinutesPill`（primaryContainer）、
  `StatusChip`（Live = tertiaryContainer 加圓點、Scheduled = surfaceVariant、Delayed = errorContainer）、`IconText`
- 附近班次：`groupByRoute`（`departures/DepartureGroups.kt`）每條路線一張卡片，組間依最早班次、組內依方向名稱排序（方向位置固定，不會隨時間跳動）。
  路線徽章放在固定 72dp 寬的欄位，各卡片的文字對齊。附近班次都是 TrueTime 預測，所以不是 Delayed 就標 Live
- 方向切換：班次詳情的 `SingleChoiceSegmentedButtonRow`，兩個方向依名稱固定順序。反方向的班次取自附近班次（`oppositeDirectionOf`），
  切換就是 `DepartureDetailsViewModel.open(反方向)`，所以上車站也換成該方向最近的站；附近沒有反方向時按鈕停用並顯示說明。
  反方向名稱用 `oppositeDirectionName`（INBOUND↔OUTBOUND 等），不認得的方向名稱（例如 LOOP）只顯示一個方向標籤
- 公車在站序上的位置：用 TrueTime 的 `pdist`（車輛沿 pattern 的距離）和各站的沿線距離比較（`RouteShape.progressOf`），
  比原計畫的「最近的站」可靠（不會被平行道路或環狀路線誤導）。停在站上的公車算「還沒過」那一站。`stopsAway` 包含上車站本身
- 時間軸：公車列插在已過的站之後，已過的站 alpha 0.45 並有 stateDescription「Passed」（測試用、也給 TalkBack）。
  開啟位置：公車在上車站前 6 列內時從公車上一列開始，否則從上車站上兩列；`remember(shape, progress == null)`，公車移動時清單不跳
- 班次詳情收合時 bottom sheet 高度 300dp（附近班次仍是 240dp），收合時看得到抵達分鐘數
- 方案清單（F15）也換成卡片，加 Live / Scheduled 標籤
- 實機（Galaxy S23，深色、大字型）截圖確認：卡片、方向切換（61C Inbound ↔ Outbound 後上車站與時間軸都換掉）、「N stops away」、公車在時間軸與地圖上
- 新增 43 個測試（全部 450 個），verify 通過（lint 0 issue）

## 方案地圖（F16 決定）

- 使用者 2026-10-02 實機測試搜尋「Carnegie Mellon University」後回報「找到路線但方案點不開、不能顯示在地圖上」，F16 移到 F24 前面
- 公車段的線：GTFS shapes 沒有匯入，改用該 trip 在上車站與下車站之間經過的站牌連線（`GtfsDao.getStopsOfTrip` JOIN stops，
  `RoomRideStopsSource` + `sliceBetween`；環狀路線若先經過下車站，取上車後的下一次）。只新增查詢，不改 schema，所以不需要 migration
- `TripMapLayers`（`trip/TripMapLayers.kt`）：步行段（藍色虛線，`palette.user`）、公車段（路線色）、上車站（金色大點）、下車站（白點）。
  選方案時先畫直線，讀到站牌後換成沿站牌的線；相機只依第一個與最後一個點決定，所以換線時不會跳動
- `TripPlanViewModel` 實作 `TripPlanActions`（select / closeSelection / openRide / onRideOpened / retry）。
  方案從規劃時的起點畫，不是使用者現在的位置；返回清單不重新規劃
- 「Live bus」：查該段上車站的 TrueTime 預測，挑同路線、趕得上、與時刻表差 15 分鐘內最接近的一班（與 F15 共用 `closestPrediction`），
  組成 `DepartureItem` 交給 `DepartureDetailsViewModel.open`（MainActivity 的 LaunchedEffect），之後呼叫 `onRideOpened` 回到 Idle；
  沒有預測或 TrueTime 失敗都顯示「時間取自時刻表」。從班次詳情按返回會回到方案詳情
- 地圖相機：`StopMap(overlayPadding)` 以 HomeScreen 量到的搜尋框高度當上方留白，修正目的地紅點被搜尋框蓋住。
  **地圖只到 bottom sheet 上緣**（BottomSheetScaffold 的內容不在 sheet 下方），所以底部不用留白；一開始加了 sheet 高度，畫面縮到看不到東西
- 方案詳情時 sheet 收合高度也用 300dp
- 方案卡片的「Live」只留右上標籤；`trip_first_bus_live` 字串移除
- 實機（Galaxy S23）確認：搜尋 Carnegie Mellon University → 點方案 → 地圖畫出 64 路線與上車站；6:33 的班次按「Live bus」顯示僅時刻表
- 新增 26 個測試（全部 476 個），verify 通過（lint 0 issue）

## 站牌可點擊與地圖圖例（F24 決定）

- **地圖上所有 `StopMarker.stopId` 現在都是 TrueTime ID**（`stop_code`，沒有時退回 `stop_id`）：附近站牌原本放 GTFS `stop_id`，
  路線站牌（pattern）放 TrueTime `stpid`，兩邊不一致；改成一致後點哪一種站牌都能直接查 TrueTime。面板的「Stop #…」就是這個號碼（站牌上印的）
- 點擊判斷 `map/StopHitTest.kt` 的 `stopAt`：螢幕像素距離，半徑 24 dp（48 dp 觸控目標的一半），取最近的；範圍外回傳 null 且 click listener
  回傳 false（不吃掉事件）。長按是另一個 listener，不受影響。可點的站牌 = 目前畫出的附近站牌 + 班次詳情的路線站牌；方案（trip）的上下車站不能點
- `stop/StopDeparturesViewModel`：和詳情相同的 `Selection` + `autoRefresh()` 模式（`MainActivity` 用 `repeatOnLifecycle(STARTED)`），每 30 秒一次 `getpredictions`（只在面板開著時，多一次呼叫）。
  即時預測**有任何一班**就只顯示即時（最多 10 班）；TrueTime 失敗、沒有 key、或成功但沒有該站預測（含 No data found）時改查時刻表，
  `StopTimesSource.Scheduled(liveError)` 記下原因，面板顯示「No live times (原因)」或「No live predictions…」。
  時刻表班次沒有車輛可追，點了改開「班次時刻」：`ScheduledRun`（trip_id + 服務日 + stop_sequence）交給 `RoomScheduledTripSource`
  （`GtfsDao.getTripStopTimesFrom`，走主鍵）列出這班車從該站起的後續站與預定時間（第一站用 departure、其餘用 arrival），標「Scheduled」；
  返回（箭頭或系統返回，`StopDeparturesViewModel.back`）先回到站牌列表再關站牌。30 秒更新不會關掉開著的班次時刻（reviewer 2026-10-04 要求補上）
- 時刻表：`data/gtfs/StopSchedule.kt` 的 `RoomStopScheduleSource`，用 `GtfsDao.getStopsByTrueTimeId`（掃 stops 表，幾千筆，一次點擊一次）找 GTFS 站牌，
  查今天與**前一個服務日**（跨午夜的班次）再合併排序；路線名稱取 `routes.shortName`。`GtfsTimetable` 第一次在 App 內使用
- 步行分鐘：選站牌時用使用者位置到站牌的直線距離（和附近班次一樣 1.2 m/s 無條件進位），沒有位置時是 0
- 導覽順序（sheet 內容）：班次詳情 > 站牌 > 方案 > 附近班次。從站牌點進詳情，返回（箭頭或系統返回）回到站牌；站牌的返回回到方案或附近班次。
  在詳情中點路線站牌會先關掉詳情再開站牌。詳情返回箭頭的說明文字仍是「Back to nearby departures」（從站牌進入時不精確，未改）
- 選中的站牌在地圖上用金色大圓（和上車站同樣式，`selected-stop-layer`），詳情開著時不畫
- 圖例：`map/MapLegend.kt` 的 `mapLegend(palette)` 從同一份 `MapPalette` 取色（`MapLegendTest` 逐項寫死兩種主題的預期色），
  `MapLegendDialog` 用 Canvas 畫圓點 / 線 / 虛線、公車用 `ic_bus`。按鈕是地圖左下角的小 FAB（`ic_legend`，Material Icons info_outline）。
  Robolectric 的小螢幕放不下八列，所以內容可捲動（大字型的手機也需要）
- 手機這次顯示 `unauthorized`（USB 偵錯授權還沒按允許），**沒有裝到手機**，地圖點擊只能實機驗收
- 新增 47 個測試（全部 559 個），verify 通過（lint 0 issue）

## 起點地址與對調（F22 決定）

- 兩端都由 `DestinationViewModel` 管：`DestinationUiState.origin`（null = My location）、`destination`、`editing`（`Endpoint.ORIGIN` / `DESTINATION`，
  搜尋與長按填哪一端）。`editOrigin()` 進入「選起點模式」，直到選了地點、長按地圖或按 ✕（`cancelOriginEdit`）才結束；
  失焦**不會**結束（長按地圖時搜尋框會失焦，不能因此把長按當成選目的地）
- `TripPlanViewModel.onEndpointsChanged(origin, destination)`：`MainActivity` 用一個 `LaunchedEffect(origin, destination)` 一次送兩端，對調只規劃一次。
  有選起點時用它的座標，定位更新完全不影響規劃；清除起點（null）時以目前位置重新規劃（沒有位置時等第一個定位）
- 對調（`swapEndpoints`）：起點是 My location 時，新目的地是**按下當時**的位置（`Destination.wasUserLocation = true`，顯示「My location (pinned)」），
  新起點是原目的地；再對調一次時這個「固定的位置」不會變成起點，而是回到 My location（跟著定位）。還不知道位置時對調不做事
- From 列**一直都在**（review 後改）：F22 要求它在目的地搜尋框上方，所以還沒選目的地也能先選起點（搜尋或長按地圖），⇅ 等有目的地才出現。
  搜尋區約 121dp（原本約 72dp，測試上限 130dp）；`HomeScreenTest` 中搜尋框下方狀態訊息的 4 個測試改用手機尺寸（`w360dp-h780dp`），
  Robolectric 預設 470dp 高的螢幕放不下
- 「離起點太遠」的 NoRoute 文字改成「of the starting point」
- 尚未做：地圖上沒有起點標記；只有目的地時的相機縮放仍以你的位置與目的地為準（選了方案後會縮放到整個行程，包含起點）
- 新增 26 個 F22 測試與 16 個時刻表班次測試（全部 601 個），verify 通過（lint 0 issue）

## 指定出發 / 抵達時間（F27 決定）

- 反向搜尋：`TransitNetwork.mirrored`（第一次用時才建、跟著網路一起快取）把每個 trip 倒過來、時間取負、上下車權限互換，
  轉乘不變（步行雙向）。`RoutePlanner.planArrivingBy` 在鏡像網路上從目的地往回跑**同一個** RAPTOR，再把結果翻回正向（`Itinerary.mirrored`），
  所以轉乘緩衝與 Pareto（少轉乘優先、多轉一次只有出發更晚才保留）規則和正向完全一樣；最後一班車下車後直接步行，不加緩衝
- `TripPlanSource.plan(origin, destination, time: TripTime)`：`TripTime.DepartAt` / `ArriveBy`。Repository 跨服務日的合併也照 Arrive by 改成「出發越晚越好」。
  凌晨的 Arrive by 會找前一服務日的深夜班次（例：週四 06:00 前抵達，會找到週三 24:40 那班）
- `TripPlanViewModel.time`（`TripTimeUiState`：mode、at、dates）獨立於目的地；切換模式或時間都會重新規劃（清掉已選方案）。
  Depart at / Arrive by 的預設時間是現在**往上取到下一個整分**（第三次 review 後；原本往下截，會列出幾十秒前開走的車）。日期範圍由 `RoomTimetableDatesSource` 從 calendar / calendar_dates 讀（第一次選非 Leave now 時才讀）
- 出發時間在**一小時以後**（`LIVE_HORIZON`）的方案不查 TrueTime；Arrive by 方案的首班車即時誤點、推算抵達晚於期限時 `TripOption.late = true`，卡片顯示紅字提醒
- 畫面：`trip/TripTimeControls.kt`（三段 SegmentedButton + 日期 / 時間按鈕，Material3 `DatePickerDialog` 只能選時刻表範圍內的日子，時間用 `TimePicker`）。
  日期時間以手機時區顯示與解讀（和方案時間一致）。Arrive by 卡片最上面是「Leave by …」
- NO_CONNECTION 文字改成「No buses in the timetable connect these places at this time.」
- 跨到隔天（review 後補上）：Depart at / Leave now 在當天與前一服務日都是 NO_CONNECTION 時，改查**下一個服務日**（例：週日 10:00 出發 → 週一 07:00 的 T1，`TripPlan.serviceDate` 是週一）。只在找不到時才查，因為隔天的車不會比今天還在跑的車更早到；快取仍最多兩個網路（fallback 只留隔天那個）。Arrive by 不需要：隔天的班次都晚於期限
- 0.1.28 的 `VERSION_CODE` 原本沿用 0.1.27 的 26，改成 27
- 新增 41 個測試，review 後再加 2 個隔天回歸測試（全部 644 個），verify 通過（lint 0 issue）。上一個 session 未提交的 Repository 測試
  `plan_arriveByBeforeTheFirstTrip_returnsNoConnection` 原本用週四 06:00，但週三深夜班次確實趕得上，改成週一 06:00（週日沒有班次）
- 第二次 review 後的修正：
  - Arrive by 的反向搜尋會找到**出發時間早於現在**的方案（例：現在 9:55、期限 10:00，9:40 出發那班）。`TripPlanViewModel` 只在 Arrive by 時把
    `departureTime < now` 的方案拿掉（用含即時誤點的出發時間）；全部拿掉時顯示 NO_CONNECTION。過濾放在 ViewModel 而不是 Repository，因為只有它知道「現在」
  - 日期按鈕在時刻表日期讀到之前停用；`DatePickerDialog` 的 OK 只有選到範圍內的日子才能按（目前日期超出範圍時預設不選）。
    時間按鈕仍保留目前的日期，所以「今天」本身不在時刻表範圍內時，只改時間仍會送出今天（第三次 review 後已修正，見下）
  - 新增 9 個測試（全部 653 個），verify 通過；其中 6 個回歸測試確認在修正前的程式下會失敗
- 第三次 review 後的修正（三個 `fix:` commit，版號仍是未推送的 0.1.28）：
  - **日期範圍改由 `TripPlanViewModel` 把關**：新增建構參數 `zone`（預設手機時區，和畫面一致），`setTime` 與預設時間都經過 `withinTimetable`，
    超出時刻表範圍的日子換成最近的有效日、保留時刻。日期讀到之前選的時間在讀完後也會被拉回範圍內並重新規劃一次。
    放在 ViewModel 而不是畫面，是因為時間按鈕、日期按鈕、預設值三個入口都要守，只有 ViewModel 全看得到
  - **跨日時間顯示日期**：`TripClockFormat`（`TripPlansPanel.kt`）在時間不是「今天」時前面加 `EEE, MMM d`（`DateFormat.getBestDateTimePattern`，跟語系走），
    方案卡片（出發、抵達、Leave by、首班車、遲到提醒）與方案詳情都用它。「今天」由 `TripPlansPanel` / `TripDetailsPanel` 的 `today` 參數決定，預設手機今天的日期
  - 新增 5 個測試（全部 658 個），verify 通過（lint 0 issue）；其中 4 個在拿掉修正的程式下會失敗，另一個（今天的方案不加日期）是防止日期加過頭的守門測試
- 第四次 review 後的修正：方案詳情中點「Live bus」時，**上車時間在一小時以後的乘車段**直接顯示「時間取自時刻表」（`RideLookup.ScheduledOnly`），
  不再查 TrueTime。判斷用該乘車段的預定上車時間（不是整個方案的出發時間），所以一小時內出發、但轉乘段在一小時後的方案，後段也不查。
  新增 2 個測試（全部 660 個），兩個都在修正前的程式下會失敗

## 步行街道路線（F23 決定）

- 服務：**FOSSGIS Valhalla** `https://valhalla1.openstreetmap.de/route`，`costing=pedestrian`、`directions_type=none`（只要折線與秒數），GET `?json=…`。
  回應 `trip.legs[].shape` 是精度 **6 位**的 encoded polyline，`trip.summary.time` 是秒（小數，往上取整）
- **使用政策**（2026-10-04 讀 https://routing.openstreetmap.de/about.html，完整版在 FOSSGIS 網站、德文）：要帶有效 User-Agent、**每秒最多 1 次**、
  不可大量使用或爬取、要顯示 OSM 出處。做法：每個請求帶 `User-Agent: OpenPRT/<版號>`；`ValhallaWalkRouter` 用 Mutex 讓同一個實例的請求間隔至少 1 秒；
  `CachingWalkRouter`（LRU 64 筆、只存成功的街道路線）讓同一段路（起訖座標相同）只查一次；只對**選定的方案**查，不對整份方案清單查。
  OSM 出處已在 OpenFreeMap 地圖的 attribution 裡
- 程式：`walk/WalkRouter.kt`（`WalkPath.Streets` / `Straight`、`WalkRouter`、`CachingWalkRouter`）、`walk/ValhallaWalkRouter.kt`。
  失敗、HTTP 錯誤、格式錯誤、逾時（預設 **5 秒** call timeout）一律回 `Straight`，方案照常顯示。街道路線前後接上真正的起訖點（服務會把起訖點吸附到路上）
- `TripPlanViewModel.select`：先讀乘車段站牌，再**逐段**查步行（一段查到就更新地圖），`SelectedTrip.walks` 依步行段順序存結果；
  `toMapLayers` 多一個 `walkPaths` 參數，沒有路線的步行段畫直線。長度為 0 的步行段（起點就在站牌）不查
- 步行分鐘：方案詳情（`TripDetailsPanel`）用 `SelectedTrip.minutesOf` 顯示街道路線的分鐘
- **方案時間跟著街道路線走**（review 修正）：每查到一段，`TripOption.withWalks` 用街道秒數重算**選定方案**的步行分鐘、出發（Leave by）、抵達與總分鐘，
  同時換掉清單裡同一個方案的卡片；其他沒點過的方案仍是規劃器的直線估算（只對選定方案查服務）。重算只看 plan、第一班車的 `boardingTime` 與步行秒數，重複套用結果相同
  - 第一段步行：出發時間 = 上車時間 − 街道秒數。變長且出發時間已早於現在 → `missesBus`
  - 轉乘步行：多出的秒數超過下一班車前的等待 → `missesBus`；抵達時間照第一班車誤點同樣的規則往後推（`delayAtEnd`）
  - 最後一段步行：抵達時間加上多出（或減去省下）的秒數；超過 Arrive by 期限就是 `late`
  - 卡片與詳情共用 `TripWarning`：`missesBus` 顯示「may miss a bus」；`late` 且有步行變長時怪步行，否則照舊怪第一班車誤點
  - `updateSelected` 改用 `plan` 比對選定方案，因為重算會換掉 `TripOption` 本身
- Fixture `valhalla/route_cmu_to_craig.json`：2026-10-04 真實錄製（Forbes Ave 近 CMU → Craig St，38 點、446.797 秒）
- 新增 24 個測試（全部 684 個），verify 通過（lint 0 issue）

## 輕軌 T 線（F17 決定）

- 使用者沒回答 questions 的輕軌題（非互動 session），照建議選項「要，照順序做 F17」實作；若使用者之後決定不要，revert `44a6ef4` 即可
- GTFS 本來就有輕軌：`routes.txt` 的 `RED` / `BLUE` / `SLVR`（route_type 2），車站是一般站牌、stop_code 是 999xx（例：Steel Plaza stop_id `10`、code `99994`），
  所以附近站牌與站牌面板**已經包含輕軌站**，不用改 GTFS 匯入。缺的只是 TrueTime 那邊沒問 `Light Rail` feed
- `TrueTimeModels.kt` 新增 `enum DataFeed(apiName)`：`BUS`（"Port Authority Bus"）、`LIGHT_RAIL`（"Light Rail"）。`TrueTimeClient` 的 `dataFeed: String` 換成 `feed: DataFeed`，
  `BUS_DATA_FEED` 常數拿掉。`Prediction.feed` 由 client 填入（預設 BUS），`DepartureItem.feed` 從 prediction 帶過來（附近班次、站牌面板、方案的「Live bus」三處）
- `departures/MergedPredictionSource.kt`：同時（`async`）問每個 feed、合併結果。規則：
  - 各 feed 的 `No data found` 當空列表
  - 至少一個 feed 有預測 → 成功，失敗的 feed 被忽略（公車或輕軌暫時壞掉，另一邊照常顯示）
  - 沒有任何預測且有 feed 失敗 → 回第一個失敗（順序是 `DataFeed.entries`，所以公車的錯誤優先）。這樣沒有 key、沒有網路仍顯示錯誤，而不是「附近沒有班次」
  - 已知取捨：公車 feed 暫時失敗但輕軌有預測時，那 30 秒內列表只剩輕軌，且不顯示錯誤
- `MainActivity`：每個 `DataFeed` 一個 `TrueTimeClient`（共用 App 內的 key）。附近班次、站牌面板、方案首班車即時時間都用合併後的 source；
  班次詳情用 `tripSourceOf(clients)`：`TripSource` 的三個方法多了 `feed` 參數，`DepartureDetailsViewModel` 用 `departure.feed` 問對的 feed
- **API 呼叫次數**（每個 predictions 呼叫變兩個請求）：
  - 附近班次：每 30 秒 1 → **2** 個請求；App 整天開著 2880 → **5760** 次 / 天
  - 站牌面板（開著時）：同樣每 30 秒 2 個；方案首班車：每次規劃 2 個（原 1 個）
  - 班次詳情：不變（每 15 秒 getvehicles + getpredictions，只問該班車的 feed）
  - BusTime 預設每日額度是 **10,000 次 / key**（未向 PRT 確認是否不同），一般使用（一天開幾十分鐘）遠低於額度；
    附近班次整天開著是 5760 次；若同時一直開著站牌面板會再加 5760 次而超過額度（沒查證兩者是否同時更新）。若實機發現配額不夠，
    可改成只在附近有輕軌站時才問輕軌 feed（需要從 GTFS 判斷哪些站是輕軌站）
- **未確認（沒有 key）**：輕軌的 TrueTime `stpid` 是否也等於 GTFS stop_code、輕軌 `rt` 是否是 `RED` / `BLUE` / `SLVR`（方案首班車用 `rt == route_id` 配對）。列入實機驗收
- 新增 16 個測試（全部 721 個）：`MergedPredictionSourceTest`（9）、附近班次合併兩個 feed（1）、詳情問對的 feed（2）、client 送 / 標記 Light Rail（2）、
  站牌面板與方案 Live bus 帶著 feed（2）。新測試用到新的 `feed` API，舊程式下無法編譯（等同失敗）

## 可靠性與離線狀態（F18 決定）

- **錯誤訊息**：`TrueTimeResult.kt` 新增 `ApiProblem` 與 `TrueTimeError.Api.problem`，從 TrueTime 的 `msg` 文字判斷：
  含「key」→ `INVALID_KEY`（與 `ApiKeyChecker` 原本的規則相同，改成共用）；含「transaction」→ `QUOTA_EXCEEDED`（BusTime 文件的訊息是
  「Transaction limit for current day has been exceeded.」，**未用真實 key 實測**）；其他 → `OTHER`，照舊引用 TrueTime 原文
  - 附近班次（`DeparturesPanel.FailureText`）：Network → 「You're offline.」、key 無效 → 換 key 的說明、配額 → 明天恢復；後面照舊接「Showing departures from …」
  - 班次詳情：Network 且有舊資料 → 「You're offline. Showing data from …」；`trueTimeErrorReason` 加 key 無效與配額兩種原因（詳情路線、站牌面板共用）
  - 保留上次資料本來就有（`NearbyDeparturesViewModel`、`DepartureDetailsViewModel` 失敗時只改 status / error），這次補上離線的 ViewModel 與畫面測試
- **GTFS 更新**：`data/gtfs/GtfsUpdater.kt`
  - `GtfsImportLog`：最後一次成功匯入的時間存在 SharedPreferences（`gtfs` 檔），**不放 Room**，避免改 schema 讓所有人重新下載。
    0.1.31 以前的安裝沒有紀錄 → 視為過期，第一次背景檢查就會重新下載一次
  - `GtfsUpdater`：唯一的匯入入口，`importIfEmpty()`（附近站牌，原本 `NearbyStopRepository` 的鎖搬過來）與 `updateIfOlderThan(7 天)`（背景）共用一個 `Mutex`。
    兩者同時觸發時，後拿到鎖的那個看到資料已在 / 剛記錄過時間，就不下載。只有成功才記錄時間
  - `GtfsUpdateWorker`：**每天**一次的 `PeriodicWorkRequest`（unique、`KEEP`、`NetworkType.CONNECTED`），資料滿 7 天才真的下載，
    所以資料最多比 7 天再舊約一天。選每天檢查而不是每 7 天排一次：週期 7 天時，剛匯入完的那次檢查會略過，最舊會到 14 天。
    結果：成功 / 不需要 → success；Network / Timeout → retry（WorkManager 退避）；HTTP 錯誤 / 壞掉的 feed → failure（隔天再試，不重複下載 22 MB）
  - 約束只有「需要網路」，**沒有限 Wi-Fi**：22 MB 可能用到行動數據，但只限 Wi-Fi 的話沒 Wi-Fi 的人永遠不會更新。若使用者在意可改 `UNMETERED`
  - WorkManager 改成第一次用到時才初始化（manifest 移除 `WorkManagerInitializer`，`OpenPrtApplication` 實作 `Configuration.Provider` 並給 `GtfsUpdateWorkerFactory`），
    worker 才拿得到 App 的 `GtfsUpdater`（同一把鎖）。排程放在 `MainActivity.onCreate`，不放 `Application.onCreate`，Robolectric 測試才不會啟動 WorkManager
- **規劃快取失效**：`TripPlanRepository` 多一個 `feedVersion: () -> Any?`（App 傳 `gtfsUpdater.lastImport.value`），值變了就丟掉所有已建的 planner
- 新增依賴：WorkManager 2.12.0（`work-runtime-ktx`、測試用 `work-testing`）
- 新增 24 個測試（全部 745 個）：`GtfsUpdaterTest`（7）、`GtfsUpdateWorkerTest`（7，排程週期、需要網路、重複排程只一個、過期下載、未過期不下載、HTTP 失敗、連不上 retry）、
  `ApiProblemTest`（3）、附近班次畫面（3）、詳情畫面（2）、詳情 ViewModel 離線（1）、規劃快取失效（1）。
  「同時觸發只下載一次」與「更新後重建 network」兩個測試在拿掉鎖 / 拿掉 `planners.clear()` 後確認會失敗
- **review 修正（2026-10-05）**：
  - **空表擋下**：`GtfsImporter` 在 stops / routes / trips / stop_times 任一表 0 列，或 calendar + calendar_dates 合計 0 列時丟 `GtfsFormatException`，
    transaction 回滾，原本的時刻表不動（只有標頭的 feed 原本會被當成功並清空資料）
  - **過期提示**：`MapViewModel` 在每次查站牌後與每次匯入成功後讀時刻表最後一天，早於今天（PRT 時區）時 `MapUiState.timetableEndedOn` 設成那天，
    首頁狀態訊息顯示「The bus timetable on this phone ended on …」
  - **空資料庫 + 下載失敗**：`GtfsUpdater.lastImportFailed`（只放記憶體，匯入開始時清掉、失敗時設定）→ `TripPlanRepository(importFailed = …)` →
    `TripPlanResult.NoTimetable(importFailed)`；方案面板改說「Couldn't download the bus timetable…」，還在下載時仍說「hasn't finished」
  - **日期範圍重讀**：`GtfsUpdater.lastImport` 改成 `StateFlow<Instant?>`。`TripPlanViewModel(timetableUpdates = …)` 每次有新值時，
    若已讀過日期或目前不是 Leave now 就重讀；畫面停在「沒有時刻表」時也自動重新規劃
  - 新增 18 個測試（全部 763 個）；空表、日期重讀、過期警示消失、NoTimetable 重新規劃這幾個測試在拿掉修正後確認會失敗
- **第二次 review 修正（2026-10-05）**：面板開著時第一次下載失敗，原本會一直停在「hasn't finished」（失敗只改了一個變數，ViewModel 只聽成功）。
  `GtfsUpdater.lastImportFailed` 改成 `StateFlow<Boolean>`，`TripPlanViewModel(importFailures = …)` 收到新值時，畫面若是 `NoTimetable` 就只換
  `importFailed`（失敗 → 下載失敗；重新開始下載 → 還在下載），不重新規劃。新增 3 個測試（全部 766 個），兩個切換測試在拿掉修正後確認會失敗
- **第三次 review 修正（2026-10-05）**：規劃器建網路時連續讀日曆、站牌、班次、停靠時間，背景更新可能在中間提交，組出新舊混合的方案；
  快取版本又是另外讀的 SharedPreferences 匯入時間。改成：
  - 資料庫新增 `imports` 表（`GtfsImportEntity`，AUTOINCREMENT id），匯入在同一個 transaction 寫一列；schema 3 → 4，
    已裝的手機升級後資料表被清掉，**會重新下載一次時刻表**（`importIfEmpty`）
  - `TransitNetworkSource.networks(daysToBuild)`：`RoomTransitNetworkSource(database)` 在一個 `withTransaction` 裡先讀 import id，
    再讀 `daysToBuild(importId)` 挑出的所有日子，回傳 `TimetableNetworks(importId, networks)`；匯入與這些讀取互相等待，不會交錯
  - `TripPlanRepository` 拿掉 `feedVersion`，改用讀到的 import id 標記快取：id 變了就清掉所有 planner，這次要的每一天都從新資料讀，
    一次規劃不會混用兩份時刻表
  - 新增 2 個測試（全部 768 個）：讀到一半時另一條執行緒匯入新 feed，方案仍是開始讀的那份（拿掉 transaction 後確認會失敗）；
    那次匯入完成後下一次規劃改用新 feed。原本用假 `feedVersion` 的測試改成真的重新匯入
- **第四次 review 修正（2026-10-05）**：站牌時刻表與可選日期也是連續好幾個查詢，背景更新在中間提交會混用新舊資料
  （班次暫時消失、路線名稱或日期範圍錯）。改成：
  - `RoomStopScheduleSource(database)` 一次查詢（站牌、三個服務日的日曆與班次、路線名稱）放在同一個 `withTransaction`
  - `GtfsTimetable(database).departuresAfter` 的日曆與班次查詢也包在 transaction 裡（單獨呼叫時同樣一致；巢狀時沿用外層的）
  - `RoomTimetableDatesSource(database)` 起日、迄日同一個 transaction 讀；`OpenPrtApplication.gtfsDatabase` 改成公開給 `MainActivity` 用
  - 新增 3 個測試（全部 771 個）：讀到一半時另一條執行緒匯入新 feed（日期往後一週／T1 提早一小時／路線改名），結果仍是開始讀的那份；
    三個都在拿掉 transaction 後確認會失敗

## 發佈流程（F19 決定）

- GitHub repo 的問題（questions 第三題）一直沒有回答；照建議選項「使用者自己建 repo 並設定 secrets」做，**不建 repo、不推送**。
  repo 端的檔案都能在本機驗證，只有實際在 GitHub 上跑一次 release 要等使用者建好 repo
- `.github/workflows/release.yml`：只在推送 `v*` tag 時觸發。順序：`scripts/check-version.sh "$GITHUB_REF_NAME"`（tag 與 README badge 都要等於
  `VERSION_NAME`）→ `scripts/test-release-scripts.sh` → 與 CI 相同的 verify → 擷取 release notes → 還原金鑰 → `assembleRelease` →
  改名 `OpenPRT-vX.Y.Z-<abi>.apk`、`apksigner verify`、每個 APK 一個 `.sha256` → `gh release create --notes-file`
- 簽章：`app/build.gradle.kts` 只從環境變數讀（`OPENPRT_KEYSTORE_FILE`、`OPENPRT_KEYSTORE_PASSWORD`、`OPENPRT_KEY_ALIAS`、`OPENPRT_KEY_PASSWORD`）；
  沒設 `OPENPRT_KEYSTORE_FILE` 就不建 signing config，`assembleRelease` 產出未簽章 APK、照樣成功。workflow 從 secret
  `OPENPRT_KEYSTORE_BASE64` 解碼到 `$RUNNER_TEMP`，secret 沒設時直接失敗，不會發佈未簽章 APK
- `PRT_API_KEY`：`local.properties` 優先，再讀環境變數（workflow 從同名 secret 傳入）。**公開發佈不要設這個 secret**：APK 裡的 key 任何人都取得到，
  還會讓所有人共用使用者的每日配額；沒設時 App 照 F20 請使用者自己輸入。README 有寫
- 大小：依 ABI 分割（arm64-v8a 約 24 MB、armeabi-v7a 約 20 MB、x86_64 約 24 MB）加 universal（約 60 MB）；debug APK 是 65 MB，主要是四份 MapLibre 原生庫。
  分割只在任務名稱含 `Release` 時開啟（`gradle.startParameter.taskNames`），`assembleDebug` / `installDebug` 仍是單一 `app-debug.apk`。
  **沒開 R8 minify**：沒實機測過 Room / kotlinx.serialization / MapLibre 的 keep 規則，怕發佈版閃退；之後要再壓大小可以評估
- `scripts/changelog-section.sh VERSION [CHANGELOG]`：印出 `## [VERSION]` 到下一個 `## ` 之間的內容（去掉前後空行）；找不到或是空段落時失敗。
  `## [0.1.3]` 不會被 `0.1.31` 誤配（比對含 `]`）
- `scripts/test-release-scripts.sh`：9 個測試（擷取中間 / 最後一段、空段落、不存在的版本、badge 與 tag 一致 / 不一致），CI 與 release workflow 都會跑。
  把比對改成不含 `]` 時確認「空段落」測試會失敗
- 本機驗證過：沒有 secret 的 `assembleRelease` 成功（未簽章）；用 /tmp 裡臨時產生的金鑰設好環境變數後產出 4 個簽章 APK，
  照 workflow 的改名、`apksigner verify`、`sha256sum` 步驟跑過，`sha256sum -c` 全部 OK。**release workflow 本身沒有在 GitHub 上跑過**
- 簽章後的 release 版與 debug 版簽章不同，手機上已有 debug 版時要先解除安裝（會清掉 App 內 key 與時刻表），README 有寫

## 給下一個 session 的注意事項

- 先載入 `coding-standards` skill：commit 訊息英文一行 `<type>: <description>`、功能與測試同一個 commit、版號只寫在 `gradle.properties`
- verify 指令：`ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} ./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug assembleDebug`
- 需要網路或 API key 的測試：本機沒有就自動略過，CI 一定要跑
- 裝到手機：`ANDROID_HOME=$HOME/Android/Sdk ./gradlew --no-daemon installDebug`。手機的 USB 要關掉網路共享、開 USB 偵錯；
  本機（含主 checkout）目前**沒有 local.properties**，key 由使用者在 App 內輸入
- 版號：功能寫完未經實機驗收用 PATCH；使用者驗收後才升 MINOR
- CLAUDE.md、`.claude/`、`notes/` 不進 git
- 使用者實機測試時所在的地址**不可寫進 repo**（測試資料、CHANGELOG、這份紀錄、commit 訊息）；範例一律用公開地標（CMU、Pitt 的 Cathedral of Learning）。
  2026-10-03 曾用 `git filter-branch` 把誤 commit 的地址從歷史清掉

## 需要實機驗收的項目（累積清單）

自動化測不到，完成對應功能後由使用者在手機上確認：

- [x] F2 填入 `PRT_API_KEY` 後實際呼叫各端點成功（2026-10-01 用 curl 實測，發現並修正 rtpidatafeed 問題；fixture 只錄了 predictions）
- [x] F3 在手機上執行一次 GTFS 匯入（目前還沒有接到畫面或背景工作，F4/F16 接上後再驗）
- [x] F5 首次啟動跳出定位權限對話框，允許後主畫面顯示真實座標；拒絕時顯示 Downtown 提示；關閉手機定位時顯示「location is turned off」
- [x] F6 首次啟動後自動下載站牌資料，地圖顯示匹茲堡與你的位置（藍點），附近站牌（深藍圓點）位置與實際站牌吻合；
  走動時地圖跟著移動；按「重新定位」回到目前位置；圖磚右下角有 OSM 標示
- [x] F8 填入 `PRT_API_KEY` 後下方面板出現附近班次，路線 / 分鐘數與站牌電子看板一致（同時驗證 stop_code 是否就是 TrueTime 的 stpid）；
  面板可往上拉開並捲動；收合時地圖中心（你的位置）沒有被面板遮住；切到背景再回來會立刻更新
- [x] F9 點一班車：地圖縮放到整條路線，折線沿實際道路，上車站是橘色大圓點；面板列出沿線站牌並捲到「Board here」；
  按返回箭頭或手機返回鍵回到列表，地圖回到你的位置
- [x] F10 詳情中的綠色公車圓點與實際車輛位置一致、每 15 秒移動；「Arrives at your stop in x min」與站牌看板一致；
  公車開過上車站後顯示「This bus has left your stop.」；切到背景再回來立刻更新
- [x] F11 在搜尋框輸入「carnegie mellon」等地點，停止打字後出現匹茲堡的結果；點一筆後地圖出現紅點並縮放到你和目的地；
  長按地圖任一處也會設成目的地（「To: Pinned spot …」）；按 ✕ 清除；關掉網路搜尋時出現錯誤與 Retry，開網路後按 Retry 有結果
- [x] F12 完整 PRT GTFS 匯入耗時與資料庫大小：JVM 上匯入 3.9 秒、資料庫 74.7 MB（見 F12 段落）；
  手機上要量第一次啟動到附近站牌出現的時間（含下載），以及「設定 → 應用程式 → OpenPRT → 儲存空間」的資料大小。
  已裝過舊版的手機更新後會自動重新下載一次
- [x] F21 系統切深色時 App 與地圖一起變深色；右上角半圓圖示選 Light / Dark 立即生效且重開後保留；
  桌面上新圖示（金黃圖釘 + 公車）清楚，開「主題圖示」時顯示單色版本；深色下各畫面文字看得清楚
- [x] F20 首次啟動出現「Welcome to OpenPRT」（2026-10-02 已在 Galaxy S23 上看到）；「Open PRT TrueTime」打開申請網頁；
  貼上真實 key 按 Save key 後回到地圖，30 秒內附近班次出現；輸入亂打的 key 顯示「TrueTime didn't accept this key: …」；
  右上角鑰匙圖示可重新打開、Cancel 不影響已存的 key；重開 App 不再出現歡迎畫面
- [x] F15 選目的地後數秒內出現方案、時間合理（和 Google Maps / Transit App 比對一兩個行程）；量手機上第一次規劃的時間（JVM 約 1.4 秒，見 F14 段落）；
  有即時預測的首班車顯示「· Live」且時間與站牌看板一致；按 ✕ 清除目的地回到附近班次
- [x] F25 附近班次卡片清楚好讀、同一路線兩個方向在同一張卡片；班次詳情的 Inbound / Outbound 切換直覺、切換後上車站與時間正確；
  「N stops away」與時間軸上的公車位置合理；淺色主題下卡片也分得清楚
- [x] F26 點一班車後，地圖上的公車是圓形公車圖示（不是綠點），旁邊小箭頭指向行進方向，淺色與深色主題都看得清楚
- [x] F16 完整流程：定位 → 選目的地 → 規劃 → 點方案看地圖（步行虛線、公車線、上下車站） → 按「Live bus」看即時公車 → 返回回到方案；
  快要開的班次（15 分鐘內）應該能打開即時詳情，較晚的班次顯示「時間取自時刻表」
- [x] 0.1.24 設計改進：站名與方向文字是一般大小寫（沒有「INBOUND-」）；方案卡片的「›」看得出可以點；
  方案詳情不用拉面板就看得到「Live bus」；選好目的地後上方只剩一列「To: …」，點它可重新搜尋、✕ 清除
- [x] 0.1.25：方案卡片顯示「N min trip」；班次詳情中公車開到身邊時公車圖示在藍點上方、藍點光暈仍看得到
- [x] F24 點附近站牌（和班次詳情路線上的站牌）出現該站班次：站名、「Stop #」與站牌上的號碼一致；有 key 時標 Live 且和站牌看板一致，
  點一班打開即時詳情、返回回到站牌；移除 key 或關網路時改成 Scheduled 並說明原因；點站牌以外的地方不會誤觸，長按仍可選目的地；
  左下角圖例的顏色、圖示與地圖上看到的一致（淺色與深色都看）
- [x] 0.1.27 時刻表班次：站牌面板中標 Scheduled 的班次可以點，列出接下來的站與時間、和站牌上的時刻表一致；返回回到站牌列表，再返回關掉站牌
- [x] F22 輸入兩個地址規劃出方案：選目的地後點「From: My location」，搜尋一個地址（例如 Cathedral of Learning）選起點，方案從那裡出發、走路時不會重新規劃；
  選起點模式下長按地圖也能設起點；✕ 回到 My location 並重新規劃；⇅ 對調後方案反過來，起點是 My location 時目的地顯示「My location (pinned)」
- [x] 第二次 review 修正：剛開 App（沒選目的地）時搜尋框上方就有「From: My location」，可先選起點再選目的地；
  Arrive by 選幾分鐘後的期限時不會出現已經開走的方案；剛切到 Depart at 的一瞬間日期按鈕是灰的，讀完後才可按，日期選擇器只能選範圍內的日子
- [x] 第三次 review 修正：Leave now 在末班車後的方案卡片顯示隔天日期（例如「Fri, Oct 2 6:56 AM」），今天的方案只有時間；
  在 10:50:30 左右切到 Depart at，時間按鈕顯示 10:51；時刻表更新、今天不在新範圍時（不易遇到，可略過）預設日期是範圍第一天
- [x] F27 Arrive by：選目的地（例如 CMU），切到 Arrive by、選明天上午的日期時間，卡片顯示「Leave by …」，
  和 Google Maps 的「抵達時間」結果比對（同一班車或相近的出發時間）；Depart at 選一小時後的時間，方案只標 Scheduled；
  點開該方案按「Live bus」立刻顯示時刻表時間、不轉圈；日期選擇器只能選時刻表範圍內的日子；切回 Leave now 回到現在的方案
- [x] 0.1.28 站牌今天末班車開走後（深夜），站牌面板列出明天的班次並標出星期
- [x] F23 點一個要走一段路的方案（例如從 Cathedral of Learning 到 CMU）：步行虛線沿著人行道 / 街道，而不是穿過建築物；
  詳情的步行分鐘和 Google Maps 步行時間相近；關掉網路後點另一個方案，步行段改畫直線、方案照常顯示；
  步行比原估算長時，詳情上方與返回清單後那張卡片的出發 / 抵達時間（Arrive by 的「Leave by」）一起變；剛好要出門的方案步行變長時出現「may miss a bus」紅字
- [x] F17 站在輕軌站附近（例如 Steel Plaza、Station Square）：附近班次出現 RED / BLUE / SLVR，分鐘數與月台看板一致；
  點一班輕軌，地圖畫出輕軌路線、列車位置每 15 秒移動、到站分鐘合理；點輕軌站站牌，面板標 Live 且列出輕軌班次；
  規劃一個第一段坐輕軌的方案（例如 Downtown → South Hills Village），卡片的首班車標「Live」（若一直是 scheduled，代表輕軌 rt 和 GTFS route_id 不同）
- [x] F18 關掉網路（飛航模式）30 秒內，附近班次上方出現「You're offline. Showing departures from …」且列表還在；打開一班車的詳情時關網路，出現「You're offline. Showing data from …」；
  開網路後自動恢復。在 App 內輸入亂打的 key 存檔（Save without checking），附近班次出現換 key 的說明。
  背景更新：`adb shell dumpsys jobscheduler | grep -A5 openprt` 看得到每天一次、需要網路的工作；
  想立刻測可用 `adb shell cmd jobscheduler run -f org.openprt.app <job id>`（0.1.30 以前裝的手機沒有匯入時間，第一次執行就會重新下載）
  時刻表過期提示無法在手機上自然重現（PRT 的 feed 通常涵蓋到未來），可把手機日期調到時刻表最後一天之後再開 App，首頁應出現「The bus timetable on this phone ended on …」；
  清除 App 資料後開飛航模式啟動，再設定目的地，方案面板應說「Couldn't download the bus timetable…」；
  從 0.1.31 以前的版本升級安裝後第一次開 App 會重新下載時刻表（schema 4），下載完附近班次與規劃正常；
  用上面的 `jobscheduler run -f` 觸發背景更新後立刻規劃幾次，方案正常、不會閃退
- [x] （新 F19，2026-10-05 使用者在手機上裝 Release 的 arm64-v8a 版並測試正常）從 Release 下載 APK 安裝並啟動。先照 README「發佈新版本」建 GitHub repo、設定四個簽章 secrets（`PRT_API_KEY` 不要設），
  推送 main 與 tag 後確認 Release workflow 綠燈、Release 頁面有 4 個 APK 與 4 個 `.sha256`、notes 是 CHANGELOG 該版段落；
  手機先解除安裝 debug 版，下載 `arm64-v8a` 版安裝，啟動後出現輸入 key 的歡迎畫面、地圖與附近站牌正常

- [x] 0.1.34 站牌圖示：附近站牌是深藍（深色主題淺藍）方形站牌、點了變大的金色站牌，淺色主題也看得清楚；圖例與地圖一致；
  市中心站牌密集時仍點得到想點的那一個
- [x] 0.1.35 起點與終點標記：From 選了地址（例如 Cathedral of Learning）後地圖出現紫色圓點、選了目的地後是紅色圖釘，畫面同時框住兩者；
  淺色主題也看得清楚；圖例有「Starting point you chose」與圖釘
- [x] 0.1.36 清除起點或目的地：規劃好方案後按任一邊的 ✕，回到一開始的畫面（From: My location、Where to?、附近班次）
- [x] 0.1.37 Apple 簡約風：淺色 / 深色主題的主畫面、附近班次、站牌面板、班次詳情、方案列表與方案詳情、API key 畫面看起來一致清爽，
  文字都讀得清楚；淺色主題頂部的時間與電量看得到；縮小地圖時站牌不會疊成一團

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
- 2026-10-01：**F3 完成**（版號 0.1.2，tag `v0.1.2` 只在本機）。GTFS CSV 解析器、stops / routes 匯入 Room、
  下載失敗保留舊資料。新增 28 個測試（全部 49 個），verify 通過；也用真實 PRT GTFS.zip 手動跑過解析（未進測試）
  - 新增依賴：Room 2.8.5、KSP 2.3.12
  - 匯入功能還沒有呼叫點（沒有 UI、沒有 WorkManager），F4 起再接上
  - 下一步：F4 附近站牌查詢（邊界框 + haversine），記得加座標索引
- 2026-10-01：**F4 完成**（版號 0.1.3，tag `v0.1.3` 只在本機）。haversine / 邊界框、`NearbyStopFinder`、
  DAO 邊界框查詢與座標索引（schema v2）。新增 12 個測試，verify 通過
  - 不需要實機驗收（純邏輯 + Room，全部自動化）
  - 下一步：F5 定位（LocationProvider 抽象 + 權限流程），需要加 play-services-location 依賴
- 2026-10-01：**F5 完成**（版號 0.1.4，tag `v0.1.4` 只在本機）。`LocationProvider` 抽象、Fused 實作、權限流程、
  拒絕 / 逾時 / 失敗退回 Downtown 並在畫面提示。新增 13 個測試（全部 74 個），verify 通過
  - 需要實機驗收（見清單 F5），Fused 實作只能在實機或有 Play services 的模擬器上驗
  - 下一步：F6 地圖主畫面。開工前要先確定地圖 SDK（questions 第一題仍未回答；Google Maps 需要使用者提供 Maps API key）
- 2026-10-01：**F6 完成**（版號 0.1.5，tag `v0.1.5` 只在本機）。MapLibre 地圖主畫面、使用者位置與附近站牌標記、
  100 m 才重查站牌、持續定位（只在前景）、重新定位按鈕、首次啟動自動匯入 GTFS。新增 22 個測試（全部 96 個），verify 通過
  - 地圖 SDK 自行選了 MapLibre + OpenFreeMap（questions 第一題未回答），若使用者要 Google Maps 只需改 `StopMap.kt`
  - 需要實機驗收（見清單 F6），地圖本身無法在 Robolectric 測
  - 新增依賴：org.maplibre.gl:android-sdk 13.6.1
  - 下一步：F7 班次排序邏輯（純 Kotlin）。開工前注意 progress.md F3 段落：TrueTime `stpid` 對應 GTFS 的 stop_id 還是 stop_code 尚未確認
- 2026-10-01：**F7 完成**（版號 0.1.6，tag `v0.1.6` 只在本機）。`DepartureRanker`：步行時間、排除趕不上的班次、
  同路線同方向只留最早能搭上的站牌、穩定排序、注入 Clock。新增 15 個測試（全部 111 個），verify 通過
  - 不需要實機驗收（純邏輯，全部自動化）
  - 下一步：F8 附近班次列表 UI（bottom sheet、30 秒自動更新）。需要先決定 GTFS 站牌 → TrueTime `stpid` 的對照方式，
    沒有 API key 時只能先假設（建議先用 stop_code，並在 progress.md 記下待驗證）
- 2026-10-01：**F8 完成**（版號 0.1.7，tag `v0.1.7` 只在本機）。附近班次 bottom sheet：路線、方向、目的地、站牌、步行分鐘、
  到站分鐘、誤點標示；前景每 30 秒更新、背景停止；失敗保留舊列表並顯示最後更新時間；空狀態與缺 API key 提示。
  新增 26 個測試（全部 137 個），verify 通過
  - 站牌 ID 對照暫定用 stop_code（未驗證，見 F8 段落）；需要實機驗收（見清單 F8）
  - 下一步：F9 班次詳情。注意 `getpredictions` 沒有 pid，要先用 `getVehicles(vid)` 取 `patternId`；
    `DepartureItem` 目前沒有 vehicleId，F9 需要加上
- 2026-10-01：使用者提供 PRT API key（只放 `local.properties`）。實測發現 **TrueTime 用戶端從來沒成功取得資料**：
  PRT 要求 `rtpidatafeed` 參數。先以 `fix:` commit 修正（含回歸測試，已確認舊程式下失敗）並錄製一份真實 predictions fixture；
  也確認 TrueTime `stpid` = GTFS `stop_code`
- 2026-10-01：**F9 完成**（版號 0.1.8，含上述修正，tag `v0.1.8` 只在本機）。點班次後顯示路線折線、沿線站牌、上車站標示，
  返回列表不重新定位。新增 30 個測試（全部 167 個），verify 通過；也用真實 pattern 回應暫時測試過轉換（未進 git）
  - 需要實機驗收（見清單 F9、F8）。debug APK 會帶入 local.properties 的 key，可以直接裝到手機測
  - 下一步：F10 即時公車位置與 ETA（見 F9 段落的 F10 注意）
- 2026-10-01：**F10 完成**（版號 0.1.9，tag `v0.1.9` 只在本機）。班次詳情每 15 秒更新公車位置與到上車站的分鐘數，
  地圖顯示公車標記，公車過站或預測消失時顯示已離站，失敗時保留舊資料並顯示時間；路線載入失敗自動重試。
  新增 25 個測試（全部 192 個），verify 通過
  - 需要實機驗收（見清單 F10、F9、F8）
  - 下一步：F11 目的地選擇（地理編碼 + 長按地圖）。需要決定地理編碼來源：Android `Geocoder`（免費但結果品質不一）
    或 Nominatim / Photon（OSM，需遵守使用政策），建議先用 Photon 或 Nominatim 並限制在匹茲堡邊界框
- 2026-10-01：**F11 完成**（版號 0.1.10，tag `v0.1.10` 只在本機）。目的地搜尋（Photon，debounce 300 ms，限匹茲堡地區）、
  長按地圖選點、目的地紅點與清除、搜尋失敗可重試。新增 35 個測試（全部 227 個），verify 通過；Photon fixture 為 2026-10-01 真實錄製
  - 需要實機驗收（見清單 F11）
  - 下一步：F12 GTFS 時刻表匯入（trips、stop_times、calendar、calendar_dates）。stop_times.txt 解開 80 MB，
    不能整份讀進記憶體再一次寫入（見 F3 段落），要邊解析邊分批寫入暫存表再切換
- 2026-10-01：**F12 完成**（版號 0.1.11，tag `v0.1.11` 只在本機）。GTFS trips / stop_times / calendar / calendar_dates 匯入 Room（schema v3），
  匯入改成「下載到暫存檔 → 單一 transaction 內串流分批寫入」；`GtfsTimetable.departuresAfter` 依站牌、服務日、時間查發車（含 calendar_dates 例外、超過 24:00 的時間）。
  新增 28 個測試（全部 255 個），verify 通過
  - 匯入耗時 / 資料庫大小只在 JVM 量到（3.9 秒、74.7 MB）；模擬器在本環境啟動即 segfault，手機量測列入實機驗收清單
  - 下一步：F13 RAPTOR 規劃器（純 Kotlin）。注意 F12 段落的「F13 注意」：跨午夜要查前一個 service day，以及 RAPTOR 需要的 route pattern 結構
- 2026-10-02：**F13 完成**（版號 0.1.12，tag `v0.1.12` 只在本機）。純 Kotlin RAPTOR 路線規劃器：步行接駁、多段乘車、步行轉乘、
  以抵達時間與搭車段數取 Pareto 最佳（最多 3 個方案）、找不到時回傳 NoRoute 與原因。新增 16 個測試（全部 271 個），verify 通過；
  另用真實 PRT feed 暫時測試過結果與速度（見 F13 段落）
  - 不需要實機驗收（純邏輯，全部自動化）；畫面上還看不到，F14 才接上
  - 下一步：F14 路線規劃結果 UI。要先從 Room 建 `TransitNetwork`（見 F13 段落的「F14 要接的地方」）
- 2026-10-02：**第二輪規劃**。檢查結果：App 尚未完成（見「計畫概覽」）。`feature_list.json` 改寫成剩下的 F14–F19，
  verify 加上 `ANDROID_HOME` 預設值。等使用者審核與回答 questions（輕軌、GitHub repo / 發佈、介面語言、實機驗收時程）
  - 下一步：F14 從 Room 建規劃網路（見 F13 段落的「F14 要接的地方」）
- 2026-10-02：**F14 完成**（版號 0.1.13，tag `v0.1.13` 只在本機）。從 Room 建規劃網路並依服務日快取、凌晨合併前一服務日深夜班次、
  60 秒最小轉乘緩衝、`TripPlanRepository` 與 `NoTimetable` 錯誤、乘車段上車站帶 TrueTime stpid。新增 19 個測試（全部 290 個），verify 通過；
  另用真實 PRT feed 暫時測試量了速度與結果（見 F14 段落，未進 git）
  - 不需要實機驗收（資料層，全部自動化）；手機上的第一次規劃時間併入 F15 的驗收項目
  - 下一步：F15 路線規劃方案清單 UI（`OpenPrtApplication.tripPlanRepository` 已可用）
- 2026-10-02：**F15 完成**（版號 0.1.14，tag `v0.1.14` 只在本機）。選目的地後自動規劃，bottom sheet 列出方案（總分鐘數、出發 / 抵達、轉乘次數、
  各段路線與步行分鐘），首班車有 TrueTime 預測時改用即時時間並標示 Live、預測失敗退回時刻表；三種 NoRoute 與 NoTimetable 各有說明，
  清除目的地回到附近班次並取消規劃。新增 39 個測試（全部 329 個），verify 通過（lint 0 issue）
  - 需要實機驗收（見清單 F15）。debug APK 會帶入 local.properties 的 key
  - 下一步：F16 方案地圖（`TripOption.plan` 已保留完整 itinerary；乘車段折線可先用站點連線，GTFS shapes 尚未匯入）
- 2026-10-02：使用者接上手機（Galaxy S23）安裝 0.1.14，要求「App 一開始要有引導讓我輸入 API key」。新增 **F20** 並完成（版號 0.1.15，tag `v0.1.15` 只在本機）：
  首次啟動歡迎畫面、PRT 申請連結、輸入後用 TrueTime 驗證再存、可略過、主畫面鑰匙圖示可更換、key 存在 App 私有儲存且立即生效。
  新增 37 個測試（全部 366 個），verify 通過（lint 0 issue）；已裝到手機並確認歡迎畫面出現
  - 需要實機驗收（見清單 F20、F15 及更早的項目）
  - 下一步：等使用者實機測試回饋；之後是 F16 方案地圖
- 2026-10-02：使用者問「能不能輸入地址規劃公車 + 步行路線」與「UI / 圖示重新設計、要有淺色與深色版」。
  現況：目的地搜尋已可輸入地址並列出公車方案（F11 + F15），但起點只能是目前位置、步行只是直線估算、方案還不能畫在地圖上（F16）；
  外觀是 Material 預設紫色、只有淺色。新增 **F21（重新設計 + 深色主題）、F22（起點地址）、F23（步行街道路線）**，
  順序 F21 → F22 → F16 → F23 → F17 → F18 → F19。feature_list.json 的 questions 加了四題（起點、步行路線服務、視覺方向、深色切換方式），
  使用者尚未回答，各功能描述裡寫的是建議預設
  - 下一步：等使用者回答新 questions 後開始 F21（若未回答，F21 照建議預設：PRT 深藍 + 金黃、跟隨系統 + 手動切換）
- 2026-10-02：使用者回答新 questions：**四題都照建議**（起點可輸入地址、步行用 FOSSGIS Valhalla、PRT 深藍 + 金黃、跟隨系統 + 手動切換），
  答案記在 feature_list.json 的 `answer` 欄位。另外問「站牌能不能點、顏色代表什麼、有沒有圖例」：目前都沒有（只有長按設目的地），
  新增 **F24（站牌可點擊 + 圖例）** 排在 F21 後。順序：F21 → F24 → F22 → F16 → F23 → F17 → F18 → F19
- 2026-10-02：**F21 完成**（版號 0.1.16，tag `v0.1.16` 只在本機）。PRT 深藍 + 金黃配色、淺色 / 深色主題（跟隨系統 + App 內切換並記住）、
  地圖樣式與標記顏色隨主題、路線編號色塊、新 App 圖示（含單色主題圖示）。新增 37 個測試（全部 403 個），verify 通過（lint 0 issue）；
  已裝到手機並截圖確認深色與淺色（見 F21 段落）。使用者已在 App 內存了 key（截圖中附近班次有即時資料）
  - 下一步：F24 站牌可點擊 + 地圖圖例（顏色用 `mapPalette`）
- 2026-10-02：使用者回報「暗的模式太暗，地圖完全看不清楚」。深色地圖從 OpenFreeMap `dark` 改成 `fiord`，路線 / 站牌改更亮的淡藍（`fix:`，版號 0.1.17，tag `v0.1.17` 只在本機）。
  verify 通過，已裝到手機並截圖確認
- 2026-10-02：使用者實機試用後回報「方向切換看不出來、抵達時間與站序只有文字太單調，應該做成一塊塊卡片」與「公車的點改成公車圖示」，
  並問有沒有 UI 設計可用的 skill（有：`design:design-critique`、`design:accessibility-review`）。新增 **F26（公車圖示）、F25（卡片化 + 方向切換）**，
  順序 F26 → F25 → F24 → F22 → F16 → F23 → F17 → F18 → F19
- 2026-10-02：**F26 完成**（版號 0.1.18，tag `v0.1.18` 只在本機）。公車徽章 + 行進方向箭頭，見 F26 段落。verify 通過（407 個測試）；
  手機 adb 斷線，尚未安裝
  - 需要實機驗收（見清單 F26）
  - 下一步：F25 卡片化介面 + 方向切換
- 2026-10-02：**F25 完成**（版號 0.1.19，tag `v0.1.19` 只在本機）。附近班次與班次詳情卡片化、方向切換、站序時間軸、方案清單卡片，見 F25 段落。
  verify 通過（450 個測試），已裝到手機（含 F26）並截圖確認
  - 需要實機驗收（見清單 F25、F26）
  - 下一步：F24 站牌可點擊 + 地圖圖例（圖例用 `ic_bus` 與 `mapPalette`）
- 2026-10-02：使用者實機問「為什麼無法 load the route」：連開 64、61D 兩個方向都正常，無法重現（錯誤代表 getvehicles 或 getpatterns 失敗，15 秒後自動重試）。
  畫面目前不顯示失敗原因，建議之後改成顯示（未做）
- 2026-10-02：**F16 完成**（版號 0.1.20，tag `v0.1.20` 只在本機），排到 F24 前。方案點開後地圖畫出行程、逐段列出、Live bus 打開即時詳情，
  順便修正目的地被搜尋框蓋住與 Live 重複。verify 通過（476 個測試），已裝到手機並截圖確認
  - 需要實機驗收（見清單 F16）
  - 待辦小項：路線載入失敗時顯示原因；目的地名稱顯示地址（目前是 Photon 的名稱，如「Cathedral of Learning」）
  - 下一步：F24 站牌可點擊 + 地圖圖例
- 2026-10-03：實機測試後的修正（版號 0.1.21–0.1.23，tag 只在本機）：
  - 0.1.21：班次詳情失敗時顯示原因（`ui/trueTimeErrorReason`）；目的地標籤加門牌地址（`Place.address` 只在有門牌號時才有）；0 分鐘顯示「Now」
  - 0.1.22：使用者在 Carnegie Mellon University 測到「-3 min、抵達早於出發」：首班車即時預測晚於時刻表時，抵達時間沒跟著延後。
    改成延誤先被轉乘等候時間吸收，剩下的才延後抵達（`Itinerary.delayAtEnd`）；回歸測試在舊程式下確認失敗
  - 0.1.23：搜「first baptist church」時 Oakland 那間排第 7：Photon 加 `lat`/`lon` 位置偏好（使用者位置，未知時 Downtown）；
    選目的地時搜尋結果清單還開著，地圖縮放把清單高度也當成要避開的範圍，縮成整個郡 → 上方留白改變時（有目的地才算）重新縮放
  - 已在 S23 上用 Carnegie Mellon University → First Baptist Church（71A，16 分鐘，Live）確認
  - design-critique 結果的三個優先建議（方案卡片加「›」與 Live bus 位置、站名轉一般大小寫並整理車頭方向、縮小上方搜尋區）尚未做
  - 下一步：上述三項建議或 F24，等使用者決定
- 2026-10-03：**design-critique 三項建議完成**（版號 0.1.24，tag `v0.1.24` 只在本機）：
  - 站名與車頭方向：`ui/DisplayNames.kt` 的 `displayName`（全大寫才轉；縮寫、序數、Mc、O' 另外處理）與 `displayHeadsign`（去掉 INBOUND- 等前綴），只在顯示層套用
  - 方案卡片加 `ic_chevron_right`；方案詳情的 Live bus 移到路線編號那一列，上下車時間放右側一欄（`StopAndTime`）
  - 搜尋區：無外框 `TextField`、提示「Where to?」；有目的地時改顯示 `DestinationBar`（點了重新搜尋），選好目的地時整塊約 72dp（原本約 148dp）
  - verify 通過（512 個測試），新測試都確認在舊程式下會失敗；已裝到 S23 截圖確認
  - 尚未處理的小問題：「18 min」行程總長與倒數分鐘長得像；公車圖示會蓋住使用者藍點（0.1.25 已修）
  - 下一步：F24 站牌可點擊 + 地圖圖例
- 2026-10-03：小問題修正（版號 0.1.25，tag `v0.1.25` 只在本機）：
  - 行程總長改成「N min trip」、字級降為 titleLarge（`TripSummary`），測試在舊程式下確認失敗
  - 地圖圖層改為使用者在公車之下，並加半透明光暈 `user-halo-layer`（26dp，比公車圖示 18dp 大）；地圖渲染無法自動測，列入實機驗收
  - 使用者提出「指定出發 / 抵達時間規劃」，記成 **F27**（feature_list.json 最後一項，反向 RAPTOR 的設計見 plan）
  - verify 通過，已裝到 S23 截圖確認
  - 下一步：F24 站牌可點擊 + 地圖圖例
- 2026-10-03：**第三輪規劃**。`feature_list.json` 只留剩下的 F24 → F22 → F27 → F23 → F17 → F18 → F19，
  questions 列出仍未回答的五題（iOS 範圍、輕軌、GitHub repo / 發佈、介面語言、實機驗收時程），已回答的四題保留 `answer`。
  在本 worktree（0.1.25）跑 verify：通過，512 個測試，lint 0 issue
  - 下一步：F24 站牌可點擊 + 地圖圖例（顏色用 `mapPalette`，圖示用 `ic_bus`）
- 2026-10-03：**F24 完成**（版號 0.1.26，tag `v0.1.26` 只在本機）。地圖站牌可點擊（即時 / 時刻表班次、點班次進詳情、返回回到站牌）+ 地圖圖例，見 F24 段落。
  開工前在本 worktree 跑 verify 通過（512 個測試）；完成後 verify 通過（559 個測試，lint 0 issue）
  - 手機 adb 顯示 unauthorized，沒有安裝；需要實機驗收（見清單 F24）
  - 下一步：F22 起點可輸入地址、對調起訖
- 2026-10-04：reviewer 要求修正後：
  - **F24 補上時刻表班次的詳情**（`fix:`）：站牌面板中 Scheduled 的班次點了會列出這班車從該站起的後續站與預定時間（GTFS），返回回到站牌列表，見 F24 段落。
    reviewer 說的沒錯：F24 的 steps 寫「點班次可進入詳情」，原本時刻表班次不能點
  - **F22 完成**：起點可以搜尋地址或長按地圖、清除回到 My location、⇅ 對調，見 F22 段落
  - 版號 0.1.27（tag `v0.1.27` 只在本機）。開工前 verify 通過（559 個測試），完成後 verify 通過（601 個測試，lint 0 issue）
  - README（功能描述、版本 badge）與 CHANGELOG 已更新
  - 沒有裝到手機（上次 adb unauthorized，本次未再試）；需要實機驗收（見清單 0.1.27、F22、F24）
  - F17、F19 是否保留仍等使用者回答 questions（不能由 session 自己決定刪掉）
  - 下一步：F27 Leave now / Depart at / Arrive by（會改到 F22 剛改過的規劃輸入區：`TripPlanViewModel.onEndpointsChanged` 與 From/To 搜尋區）
- 2026-10-04：**F27 完成**（版號 0.1.28，tag `v0.1.28` 只在本機）。Leave now / Depart at / Arrive by、反向 RAPTOR、Leave by 與遲到提醒，見 F27 段落。
  - reviewer 第三點（站牌末班車後空白）已在 `08bf2af` 修正：時刻表也查下一服務日，並有跨日測試 `departures_afterTodaysLastTrip_listsNextServiceDaysFirstTrip`；CHANGELOG 補記在 0.1.28
  - reviewer 第一點要求一次做完 F27、F23、F18：本 session 規則是一次一項，只做 F27；F23、F18 照順序留給之後的 session
  - reviewer 第二點：F17 / F19 的取捨用 AskUserQuestion 問了使用者，**沒有回答**（非互動 session），所以兩項都保留、不刪
  - 開工時工作樹有上一個 session 未提交的 F27 資料層與 ViewModel，檢查後沿用並補上畫面、測試與一個錯誤的測試
  - 開工前 verify 失敗只因上述未提交程式的 ktlint 排序；完成後 verify 通過（642 個測試，lint 0 issue）
  - adb 沒有裝置，沒有安裝；需要實機驗收（見清單 F27、0.1.28、F22、F24）
  - 下一步：F23 步行段沿街道（FOSSGIS Valhalla）
- 2026-10-04：**第三次 review 修正**（F27 的指定時間邊界）：日期限定在時刻表範圍內（預設值與只改時間都是）、跨日的方案時間顯示日期、
  Depart at 預設往上取整分。見 F27 段落最後一項。verify 通過（658 個測試，lint 0 issue）；沒有裝到手機
  - 下一步不變：實機驗收清單，然後 F23
- 2026-10-04：**第四次 review 修正**：方案詳情裡一小時以後才上車的乘車段按「Live bus」不再查 TrueTime，直接顯示時刻表時間。verify 通過（660 個測試，lint 0 issue）；沒有裝到手機
  - 下一步不變：實機驗收清單，然後 F23
- 2026-10-04：本機 tag `v0.1.28` 原本停在 `4b8e0d2`（少了之後的修正），移到記錄這一行的 commit，也就是 0.1.28 最後通過 verify 的狀態。0.1.28 沒推送過，所以不升版號
- 2026-10-04：**F23 完成**（版號 0.1.29，tag `v0.1.29` 只在本機）。選定方案的步行段改向 FOSSGIS Valhalla 查街道路線，地圖虛線沿街道、詳情顯示實際步行分鐘，
  失敗或 5 秒逾時退回直線，見 F23 段落。開工前 verify 通過（660 個測試）；完成後 verify 通過（684 個測試，lint 0 issue）
  - adb 沒有裝置，沒有安裝；需要實機驗收（見清單 F23 以及 F27、0.1.28、F22、F24）
  - F17、F19 仍等使用者回答 questions
  - 下一步：F17 輕軌 T 線（若使用者決定刪掉就從 `feature_list.json` 移除），否則 F18
- 2026-10-04：**F23 review 修正**：街道路線查到後，選定方案的步行分鐘、Leave by、出發 / 抵達與總分鐘都改用街道時間（詳情與清單卡片），
  來不及趕上第一班車或轉乘時提醒「may miss a bus」，Arrive by 因步行晚到時提醒，見 F23 段落。verify 通過（705 個測試，lint 0 issue）；沒有裝到手機
  - 0.1.29 沒推送過，所以不升版號；本機 tag `v0.1.29` 移到記錄這一行的 commit
  - 下一步不變：實機驗收清單，然後 F17（等使用者回答）或 F18
- 2026-10-04：**F17 完成**（版號 0.1.30，tag `v0.1.30` 只在本機）。附近班次、站牌面板、方案首班車同時問公車與輕軌兩個 TrueTime feed 並合併，
  一個 feed 失敗或沒資料時另一個照常顯示；輕軌班次的詳情改問 Light Rail feed，見 F17 段落（含 API 呼叫次數）。
  使用者沒回答輕軌題，照建議選項做。開工前 verify 通過（705 個測試）；完成後 verify 通過（721 個測試，lint 0 issue）
  - adb 沒有試，沒有裝到手機；需要實機驗收（見清單 F17 以及 F23、F27、0.1.28、F22、F24）
  - F19 仍等使用者回答 questions
  - 下一步：F18 可靠性與離線狀態
- 2026-10-04：**F18 完成**（版號 0.1.31，tag `v0.1.31` 只在本機）。離線、key 無效、每日配額用完各有說明並保留上次資料；GTFS 匯入時間記錄、
  每天檢查、滿 7 天在有網路時背景重新下載（WorkManager），與首次匯入共用 `GtfsUpdater` 的鎖；更新後規劃快取失效，見 F18 段落。
  開工前 verify 通過；完成後 verify 通過（745 個測試，lint 0 issue）
  - 沒有裝到手機；需要實機驗收（見清單 F18 以及 F17、F23、F27、0.1.28、F22、F24）
  - 「時刻表過期」的提示畫面沒做（見 F18 段落最後一項）
  - 下一步：F19 發佈流程，仍等使用者回答 GitHub repo 的問題
- 2026-10-05：**F18 review 修正**：只有標頭的 feed 不再清空時刻表；時刻表過期時首頁提示；空資料庫且下載失敗時方案面板說下載失敗；
  背景更新後 Depart at / Arrive by 的可選日期重讀。見 F18 段落「review 修正」。verify 通過（763 個測試，lint 0 issue）；沒有裝到手機
  - 0.1.31 沒推送過，所以不升版號；本機 tag `v0.1.31` 移到記錄這一行的 commit
  - 下一步：F19 發佈流程，仍等使用者回答 GitHub repo 的問題
- 2026-10-05：**F18 第二次 review 修正**：方案面板開著時第一次下載失敗，訊息會從「還沒下載完」換成「下載失敗」，重新下載時換回來。
  見 F18 段落「第二次 review 修正」。verify 通過（766 個測試，lint 0 issue）；沒有裝到手機
  - 0.1.31 沒推送過，所以不升版號；本機 tag `v0.1.31` 移到記錄這一行的 commit
  - 下一步：F19 發佈流程，仍等使用者回答 GitHub repo 的問題
- 2026-10-05：**F18 第三次 review 修正**：規劃器一次建網路的所有查詢與 import id 放在同一個資料庫 transaction，背景更新不會讓方案混用新舊時刻表；
  快取改用資料庫裡的 import id 判斷版本。見 F18 段落「第三次 review 修正」。verify 通過（768 個測試，lint 0 issue）；沒有裝到手機
  - schema 升到 4，已裝的手機升級後會重新下載一次時刻表
  - 0.1.31 沒推送過，所以不升版號；本機 tag `v0.1.31` 移到記錄這一行的 commit
  - 下一步：F19 發佈流程，仍等使用者回答 GitHub repo 的問題
- 2026-10-05：**F18 第四次 review 修正**：站牌時刻表（`RoomStopScheduleSource`、`GtfsTimetable`）與可選日期（`RoomTimetableDatesSource`）
  的多次查詢放進同一個資料庫 transaction，背景更新不會讓它們混用新舊時刻表。見 F18 段落「第四次 review 修正」。
  verify 通過（771 個測試，lint 0 issue）；沒有裝到手機
  - 0.1.31 沒推送過，所以不升版號；本機 tag `v0.1.31` 移到記錄這一行的 commit
  - 下一步：F19 發佈流程，仍等使用者回答 GitHub repo 的問題
- 2026-10-05：**F19 完成**（版號 0.1.32，tag `v0.1.32` 只在本機）。推送 `v*` tag 時的 release workflow、依 ABI 分割的簽章 APK、SHA256、
  CHANGELOG 擷取的 release notes、README 下載方式與 badge 檢查，見「發佈流程（F19 決定）」。開工前 verify 通過；完成後 verify 通過
  （771 個測試，lint 0 issue），`scripts/test-release-scripts.sh` 9 個測試通過
  - GitHub repo 問題仍沒回答：照建議選項做，沒有建 repo、沒有推送；release workflow 沒在 GitHub 上跑過
  - `feature_list.json` 的功能全部 `passes: true`；仍**不能升 0.2.0**，要等使用者照實機驗收清單（F18、F17、F23、F27、0.1.28、F22、F24、F19）驗收
  - 下一步：使用者建 GitHub repo、設定 secrets、推送 main 與 `v0.1.32`，再做實機驗收
- 2026-10-05：**F19 改回 `passes: false`**（reviewer 要求）。沒有 GitHub remote、沒有 Release、workflow 沒在 GitHub 跑過，
  F19 最後一條 step「從 GitHub Release 下載 APK 安裝後可正常啟動」也沒驗收，所以不能算完成
  - README「下載安裝」與 CHANGELOG 0.1.32 改寫成「還不能下載、請自己建置」；第一次發佈並驗證後再把這兩段的提醒拿掉
  - README 與 `release.yml` 註解裡的 tag 指令原本寫死 `v0.1.31`（照做會被版號檢查擋下），改成從 `gradle.properties` 的 `VERSION_NAME` 讀
  - 沒升版號（0.1.32 沒推送過）；本機 tag `v0.1.32` 移到記錄這一行的 commit，release notes 才會是改過的 CHANGELOG
  - 下一步：等使用者回答 GitHub repo 問題；建好 repo、設定 secrets、推送 main 與 `v0.1.32` 後確認 workflow 綠燈，
    從 Release 安裝 `arm64-v8a` 版並啟動，通過後 F19 才改 `passes: true`
- 2026-10-04：**F19 仍卡在 GitHub repo**，沒有改程式。開工前 verify 通過，`scripts/test-release-scripts.sh` 全部通過
  - 用 AskUserQuestion 再問一次 GitHub repo 怎麼處理，沒有回答（非互動 session）；建外部 repo 要先確認，所以沒建、沒推送
  - F19 維持 `passes: false`；剩下的都要使用者自己做：建 repo、設定 keystore 四個簽章 secret
    （公開發佈**不要設定** `PRT_API_KEY`，否則個人 key 會內建進 APK）、推送 main 與 `v0.1.32`、確認 workflow 綠燈、從 Release 安裝 `arm64-v8a` 版並啟動
- 2026-10-04：**F19 仍卡在 GitHub repo**，沒有改程式。開工前 verify 通過
  - 用 `gh` 確認 `AquilaWei/OpenPRT` 還不存在（`gh` 已登入 AquilaWei），本機仍沒有 remote
  - 再問一次 GitHub repo 怎麼處理，仍沒有回答；沒建 repo、沒推送，F19 維持 `passes: false`
  - 本機預設分支是 `master`，這一輪的工作只在 `hb/7-openprt`，`master` 還沒有；要先把它併回 `master` 再推
  - 使用者建好 repo 後的指令：`git remote add origin https://github.com/AquilaWei/OpenPRT.git`、
    `git push -u origin master`、`git push origin v0.1.32`（tag 在 `12d936a`）
- 2026-10-05：**F19 仍卡在 GitHub repo**，沒有改程式。開工前 verify 通過，`scripts/test-release-scripts.sh` 全部通過
  - 再問一次 GitHub repo 怎麼處理，仍沒有回答；沒建 repo、沒推送，F19 維持 `passes: false`，tag `v0.1.32` 仍在 `12d936a`
  - 剩下的步驟都要使用者回答或親自做，照上一條的指令即可；在那之前再開 session 也只會重複這一條
- 2026-10-05：**GitHub repo 建好了**（使用者同意，公開）：https://github.com/AquilaWei/OpenPRT ，`hb/7-openprt` 推成 `master`，CI 綠燈。
  公開前檢查過整段歷史：沒有 key、keystore、`local.properties`，也沒有實機測試地點。**tag 還沒推**，要等使用者設好四個簽章 secret
- 2026-10-05：**0.1.33 修正時刻表下載失敗**（實機上看到「Couldn't load bus stops」）。PRT 改版網站，舊網址
  `rideprt.org/developerresources/GTFS.zip` 回 404；新網址在 `/business-resources/web-developer-resources/` 頁上，是帶雜湊的
  `/contentassets/<hash>/gtfs.zip`，每次換時刻表很可能會變
  - 決定：`GtfsImporter` 新增 `feedPageUrl`，每次下載前先讀開發者資源頁找 `gtfs.zip` 連結，找不到或讀不到時用 `DEFAULT_FEED_URL`（目前的雜湊網址）。
    參數預設 `null`（不查頁面），只有 App 會傳，測試不會連到真網站
  - 4 個新測試，拿掉修正時 4 個都失敗；完整 verify 775 個測試通過。實機上裝 debug 版後站牌與附近即時班次都出來了
  - 0.1.32 從沒發佈，本機 tag `v0.1.32` 不會再用；第一次發佈改用 `v0.1.33`
  - 下一步：使用者設定簽章 secret → 推 `v0.1.33` → 確認 Release 有 4 個 APK 與 4 個 `.sha256` → 從 Release 安裝 `arm64-v8a` 並啟動，F19 才算完成
- 2026-10-05：**F19 仍等簽章 secret**，沒有改程式。開工前 verify 通過；repo 的 CI 綠燈，`gh secret list` 是空的，還沒有任何 Release
  - 問使用者要自己設 secret 還是讓 session 產生金鑰並設定，沒有回答（非互動 session）；簽章金鑰要使用者自己保管備份，所以沒有代為產生、沒推 tag
  - 本機 tag `v0.1.33` 在 `24813c8`（0.1.33 的 CHANGELOG 已在），推送時用它即可，不用移動
  - 使用者自己執行（密碼由 `gh` 互動輸入，不會出現在指令或 log）：
    `keytool -genkeypair -keystore ~/openprt-release.jks -alias openprt -keyalg RSA -keysize 4096 -validity 10000`、
    `base64 -w0 ~/openprt-release.jks | gh secret set OPENPRT_KEYSTORE_BASE64 -R AquilaWei/OpenPRT`、
    `gh secret set OPENPRT_KEYSTORE_PASSWORD -R AquilaWei/OpenPRT`、`gh secret set OPENPRT_KEY_ALIAS -R AquilaWei/OpenPRT`（輸入 `openprt`）、
    `gh secret set OPENPRT_KEY_PASSWORD -R AquilaWei/OpenPRT`；金鑰檔另外備份
  - 設好後：`git push origin v0.1.33` → 確認 release workflow 綠燈、Release 有 4 個 APK 與 4 個 `.sha256` → 從 Release 安裝 `arm64-v8a` 並啟動，
    F19 才改 `passes: true`，並拿掉 README「下載安裝」與 CHANGELOG 的「還不能下載」提醒
- 2026-10-05：**第一次發佈 v0.1.33 成功**（使用者要求 session 直接執行）：https://github.com/AquilaWei/OpenPRT/releases/tag/v0.1.33
  - session 用 `keytool` 產生 PKCS12 金鑰（RSA 4096，alias `openprt`，密碼是 `openssl rand` 產生的亂數），金鑰與密碼存在本機 `~/.openprt-release/`（權限 700 / 600，不在 repo 裡）；
    用 `gh secret set` 從檔案寫入四個簽章 secret，密碼沒有出現在指令、log 或對話。**使用者要自己把這個資料夾備份到別處**，弄丟就不能發佈能覆蓋安裝的更新
  - 憑證 SHA-256：`96:74:7A:54:49:30:3A:3F:4E:45:63:82:B1:11:7E:28:C0:3C:E8:53:72:11:97:B2:62:5E:4A:FE:E8:A1:2D:A7`
  - release workflow 第一次在 GitHub 上跑就綠燈（`apksigner` 路徑、`gh release create --verify-tag` 的假設都成立）；Release 有 4 個 APK 與 4 個 `.sha256`，
    下載回本機 `sha256sum -c` 全部 OK，`apksigner verify` 的簽章憑證與上面一致
  - README 拿掉「還沒有可下載的版本」，Releases 連到 repo；順便把 README 的 GTFS 連結換成 PRT 開發者資源頁（舊網址 0.1.33 起就是 404）、開頭版號改 0.1.33
  - **還沒裝到手機**：手機上是 debug 版，簽章不同要先解除安裝，會清掉 App 內的 key 與時刻表；問使用者要不要這樣做，沒有回答，所以沒動手機。
    F19 維持 `passes: false`，等從 Release 安裝 `arm64-v8a` 並啟動成功才改
- 2026-10-05：**F19 完成**。使用者把 v0.1.33 Release 的 `arm64-v8a` 版裝到手機並回報「測試都正確」；用 adb 拉回手機上的 APK 確認簽章憑證就是 release 金鑰、版本 0.1.33。
  `feature_list.json` 全部 `passes: true`；仍要等使用者確認累積清單的其他實機項目才升 0.2.0
- 2026-10-05：**0.1.34 站牌圖示**（使用者實機回饋），見「站牌圖示」。verify 通過（779 個測試，lint 0 issue）；已覆蓋安裝到手機並截圖確認深色主題。
  本機 tag `v0.1.34` 還沒推（推了就會發佈 Release），淺色主題與市中心密集站牌待使用者確認
- 2026-10-05：使用者實機驗收大部分項目通過（F3、F5、F6、F8–F12、F15、F17、F18、F20、F21、F24–F27、0.1.24、0.1.25、0.1.27、0.1.28、0.1.34），已勾選。
  還沒驗：F16 完整流程、F22（起點標記剛改）、F23 步行街道路線、第二 / 三次 review 修正、0.1.35
- 2026-10-05：**0.1.35 起點與終點標記**（使用者實機回饋），見「起點與終點標記」。本機 tag `v0.1.34`、`v0.1.35` 都還沒推（推了就發佈）
- 2026-10-05：使用者驗收剩下六項全部通過（F16、F22、F23、第二 / 三次 review 修正、0.1.35），累積清單除了 0.1.36 都已勾選
- 2026-10-05：**0.1.36 修正清除起點 / 目的地後方案還在**（使用者實機回報）。原本按起點 ✕ 只把起點改回 My location、保留目的地，於是馬上從目前位置重新規劃；
  按目的地 ✕ 會收起方案但保留選的起點。使用者要求「刪除任何一個位置就退回原本的狀態」，兩個 ✕ 都改成 `DestinationViewModel.startOver()`：起訖點都清空
  - 2 個回歸測試，修正前確認失敗；788 個測試、verify 通過。裝到手機重現：Cathedral of Learning → CMU 規劃後按起點 ✕，回到初始畫面
  - 和 F22 step「清除起點回到 My location 並以目前位置重新規劃」的關係：`TripPlanViewModel.onEndpointsChanged(null, 目的地)` 仍會以目前位置規劃
    （對調回 My location 時用到，測試還在），只是畫面上的 ✕ 不再走這條路；這是使用者的新決定，feature_list 的 step 沒改
  - 本機 tag `v0.1.34`、`v0.1.35`、`v0.1.36` 都沒推；全部驗收通過後可以升 0.2.0
- 2026-10-05：**0.1.37 Apple 簡約風 + 英文 README**（使用者要求），見「Apple 簡約風」。verify 通過（788 個測試，lint 0 issue）；
  裝到手機，淺色與深色主題截圖確認主畫面、方案列表、分段控制與狀態列。**等使用者看過 0.1.37 再升 0.2.0**；本機 tag `v0.1.34`–`v0.1.37` 都沒推
- 2026-10-05：**0.2.0**。使用者看過 0.1.37 後回覆「進版」：累積的實機驗收清單全部勾選，`feature_list.json` 全部 `passes: true`。
  CHANGELOG 0.2.0 段落（英文）整理 0.1.33 之後的變更；完整 verify 與 `scripts/test-release-scripts.sh` 通過後推 `v0.2.0`。
  0.1.34–0.1.37 是只裝在手機上的測試版，本機 tag 沒推、不發佈
  - Release workflow 綠燈：https://github.com/AquilaWei/OpenPRT/releases/tag/v0.2.0 ，4 個 APK + 4 個 `.sha256`，下載回來 `sha256sum -c` 全部 OK，
    簽章憑證與 release 金鑰一致。手機當時 USB 斷線，**沒有從 Release 安裝到手機**；手機上是 0.1.37（程式與 0.2.0 相同，只差版號）
