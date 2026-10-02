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

## 給下一個 session 的注意事項

- 先載入 `coding-standards` skill：commit 訊息英文一行 `<type>: <description>`、功能與測試同一個 commit、版號只寫在 `gradle.properties`
- verify 指令：`./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug assembleDebug`，F1 完成前會失敗屬正常
- 需要網路或 API key 的測試：本機沒有就自動略過，CI 一定要跑
- 版號：功能寫完未經實機驗收用 PATCH；使用者驗收後才升 MINOR
- CLAUDE.md、`.claude/`、`notes/` 不進 git

## 需要實機驗收的項目（累積清單）

自動化測不到，完成對應功能後由使用者在手機上確認：

- [x] F2 填入 `PRT_API_KEY` 後實際呼叫各端點成功（2026-10-01 用 curl 實測，發現並修正 rtpidatafeed 問題；fixture 只錄了 predictions）
- [ ] F3 在手機上執行一次 GTFS 匯入（目前還沒有接到畫面或背景工作，F4/F16 接上後再驗）
- [ ] F5 首次啟動跳出定位權限對話框，允許後主畫面顯示真實座標；拒絕時顯示 Downtown 提示；關閉手機定位時顯示「location is turned off」
- [ ] F6 首次啟動後自動下載站牌資料，地圖顯示匹茲堡與你的位置（藍點），附近站牌（深藍圓點）位置與實際站牌吻合；
  走動時地圖跟著移動；按「重新定位」回到目前位置；圖磚右下角有 OSM 標示
- [ ] F8 填入 `PRT_API_KEY` 後下方面板出現附近班次，路線 / 分鐘數與站牌電子看板一致（同時驗證 stop_code 是否就是 TrueTime 的 stpid）；
  面板可往上拉開並捲動；收合時地圖中心（你的位置）沒有被面板遮住；切到背景再回來會立刻更新
- [ ] F9 點一班車：地圖縮放到整條路線，折線沿實際道路，上車站是橘色大圓點；面板列出沿線站牌並捲到「Board here」；
  按返回箭頭或手機返回鍵回到列表，地圖回到你的位置
- [ ] F10 詳情中的綠色公車圓點與實際車輛位置一致、每 15 秒移動；「Arrives at your stop in x min」與站牌看板一致；
  公車開過上車站後顯示「This bus has left your stop.」；切到背景再回來立刻更新
- [ ] F11 在搜尋框輸入「carnegie mellon」等地點，停止打字後出現匹茲堡的結果；點一筆後地圖出現紅點並縮放到你和目的地；
  長按地圖任一處也會設成目的地（「To: Pinned spot …」）；按 ✕ 清除；關掉網路搜尋時出現錯誤與 Retry，開網路後按 Retry 有結果
- [ ] F12 完整 PRT GTFS 匯入耗時與資料庫大小：JVM 上匯入 3.9 秒、資料庫 74.7 MB（見 F12 段落）；
  手機上要量第一次啟動到附近站牌出現的時間（含下載），以及「設定 → 應用程式 → OpenPRT → 儲存空間」的資料大小。
  已裝過舊版的手機更新後會自動重新下載一次
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
