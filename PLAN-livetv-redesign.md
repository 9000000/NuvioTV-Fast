# PLAN-livetv-redesign: Thiết kế lại giao diện Live TV (Bố cục 3 cột hiện đại)

> [!IMPORTANT]
> **Mục tiêu cốt lõi:** Thay thế giao diện EPG nhiều dòng hiện tại trong [LiveTvGuideScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt) bằng bố cục **3 cột cố định** khớp với bản phác thảo và các lựa chọn đã thống nhất:
> - **Cột Trái (Khung Vàng — Cố định):** Thông tin chương trình đang chọn ở trên + bộ chọn **List kênh** (mặc định: *Tất cả list kênh*) ở vị trí trên nhất của danh sách **DANH MỤC** bên dưới.
> - **Cột Giữa (Khung Xanh dương + Lưới ô Hồng):** Lưới thẻ kênh (full height) của danh mục và list kênh đang chọn.
> - **Cột Phải (Khung Tím + Khung Cam):** Cửa sổ Video Preview 16:9 ở trên + Lịch phát sóng dọc (Vertical EPG) cho kênh đang focus/preview ở dưới.

---

## 1. Kiến trúc Bố cục Tổng thể

```text
+-----------------------+-----------------------------------+--------------------------+
| CỘT TRÁI (240.dp)     | CỘT GIỮA (weight 1f ~ 440.dp)     | CỘT PHẢI (280.dp)        |
| [Khung Vàng - Cố định]| [Khung Xanh dương]                | [Khung Tím - Preview]    |
|                       |                                   |  +--------------------+  |
|  [Logo] VTV1 [LIVE]   |  +----+ +----+ +----+ +----+ +----+ |  |  Video Preview     |  |
|  Tên chương trình     |  | K1 | | K2 | | K3 | | K4 | | K5 | |  |  16:9 (OK phát /   |  |
|  08:15 - 08:45        |  +----+ +----+ +----+ +----+ +----+ |  |  OK lần 2 Full)    |  |
|  [======---------]    |  +----+ +----+ +----+ +----+ +----+ |  +--------------------+  |
|  Mô tả gọn (ellipsize)|  | K6 | | K7 | | K8 | | K9 | |K10 | |  [Khung Cam - EPG Dọc]   |
+-----------------------+  +----+ +----+ +----+ +----+ +----+ |  LỊCH PHÁT SÓNG · VTV1   |
|  [List kênh: Tất cả▾] |  ...                              |  • 07:45-08:15 (Catchup) |
|  DANH MỤC             |  (Cuộn dọc mượt, hỗ trợ D-pad     |  > 08:15-08:45 [LIVE]    |
|  > Yêu thích ⭐    8  |   lên/xuống/trái/phải + long OK   |    Sống xanh             |
|  > Xem gần đây     8  |   để Yêu thích)                   |  • 08:45-09:15 Thời sự   |
|  > LIVE EVENTS 🔴 76  |                                   |  • 09:15-10:00 ...       |
|  > Thể Thao        8  |                                   |  (Catchup nếu hỗ trợ)    |
+-----------------------+-----------------------------------+--------------------------+
```

> [!WARNING]
> **Ràng buộc kích thước & hiển thị trên TV (`960dp x 540dp` chuẩn Android TV Compose):**
> - Phần mô tả chương trình ở **Cột Trái** phải được giới hạn chiều rộng bên trong cột (`240.dp`) với `clip = true` và `maxLines = 2..3` (hoặc marquee chạy chữ bên trong khung cột) để **không tràn chữ sang Cột Giữa** như lỗi ở giao diện cũ.
> - Cột Giữa cần đủ rộng cho lưới 4 hoặc 5 cột thẻ kênh rõ ràng, logo sắc nét và viền focus không bị cắt mép (`contentPadding` đủ thoáng).

---

## 2. Chuỗi Phụ thuộc (Dependency Chains)

| Thành phần | Vai trò trong kiến trúc mới | Tệp liên quan |
| :--- | :--- | :--- |
| **Entry & Navigation** | Mount màn hình Live TV khi chọn từ Sidebar / StartupTab | [NuvioNavHost.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt) |
| **ViewModel & State** | Giữ nguyên `uiState`, `programs`, `playbackState`, `userState`, `now`, `play()`, `toggleFavorite()`, `stopPlayback()` | [LiveTvViewModel.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvViewModel.kt), [LiveTvModels.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvModels.kt) |
| **Playback & Fullscreen** | Giữ nguyên `LiveTvPlaybackController`, `LivePlayerSurface`, Fullscreen OSD & Quick Channels | [LiveTvPlaybackController.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvPlaybackController.kt), [LiveTvComponents.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvComponents.kt) |
| **EPG & Catch-up** | Lấy danh sách chương trình (`programs[ch.key]` + fallback `SyntheticEpgGenerator`) và build URL xem lại (`CatchupUrlBuilder`) | [SyntheticEpgGenerator.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/SyntheticEpgGenerator.kt), [CatchupUrlBuilder.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/CatchupUrlBuilder.kt) |
| **Channel Card UI** | Tái sử dụng / tinh chỉnh thẻ kênh gọn nhẹ (Logo, tên kênh, icon ⭐, indicator đang phát `LIVE`) | [LiveTvChannelCard.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvChannelCard.kt) |
| **Màn hình chính cần thiết kế lại** | Viết lại bố cục non-fullscreen và tách thành các component nhỏ, rõ ràng | [LiveTvGuideScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt) |

---

## 3. Phân rã Kế hoạch Thực thi theo Giai đoạn (Phases)

### Phase 1: Thiết kế lại Bố cục 3 Cột & Tách Component Gọn Sạch
Chia màn hình non-fullscreen trong [LiveTvGuideScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt) (có thể tách sang các file con trong `features/livetv/` nếu vượt ngưỡng gọn) thành `Row(fillMaxSize)` gồm 3 khối chính:

1. **`LiveTvLeftPanel` (Khung Vàng — rộng cố định ~`240.dp` – `252.dp`, luôn hiển thị):**
   - **Phần trên (Thông tin chương trình — chiều cao ~`195.dp` – `210.dp`, có đường ngăn cách tinh tế với Danh mục):**
     - Hàng 1: Logo kênh (`32.dp`) + Tên kênh (`maxLines = 1`, ellipsis) + badge `TRỰC TIẾP` (nếu chương trình đang phát) / `CATCH-UP` (nếu kênh hỗ trợ).
     - Hàng 2: Tên chương trình (`18.sp–20.sp`, `FontWeight.Bold`, `maxLines = 2`, ellipsis).
     - Hàng 3: Khung giờ `HH:mm - HH:mm` + thanh tiến độ `progress` (tương ứng phần trăm thời gian đã phát).
     - Hàng 4: Mô tả chương trình (`maxLines = 2..3`, giới hạn chặt trong cột trái bằng `fillMaxWidth()`, không tràn sang cột giữa).
     - Trạng thái rỗng: hiển thị gợi ý `"Chọn một kênh để xem thông tin"`.
   - **Phần dưới (`List kênh` + `DANH MỤC` — `weight(1f)`):**
     - **Mục trên nhất — Bộ chọn `List kênh` (`selectedPlaylistId`, mặc định `null` = Tất cả list kênh):**
       - Hiển thị ngay phía trên danh sách danh mục dưới dạng một nút/mục nổi bật (`List kênh: Tất cả` hoặc `List kênh: <Tên playlist>` kèm biểu tượng danh sách và mũi tên `▾`, tổng số kênh).
       - Tổng hợp tất cả nguồn phát đang bật có kênh: các playlist M3U (`ui.playlists`), `Xtream` (`XTREAM_PLAYLIST_ID`), và `Stalker Portal` (`STALKER_PLAYLIST_ID`), tái sử dụng logic từ [LiveTvScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvScreen.kt#L172-L202).
       - Khi **bấm OK** vào mục `List kênh`: mở popup/overlay danh sách các list kênh (gồm mục đầu tiên `"Tất cả list kênh"` mặc định + danh sách từng playlist kèm số lượng kênh) để chọn nhanh bằng D-pad.
       - Khi đổi playlist: lọc `scopedChannels` (nếu `selectedPlaylistId == null` thì giữ nguyên toàn bộ `ui.channels`, nếu chọn 1 list cụ thể thì lọc `ui.channels.filter { it.playlistId == selectedPlaylistId }`), tự động cập nhật lại các nhóm `DANH MỤC` và số lượng kênh tương ứng. Nếu `selectedGroupId` cũ không còn tồn tại trong list mới thì fallback về `ChannelGroup.ALL` (hoặc `FAVORITES` nếu còn).
     - **Ngay bên dưới là tiêu đề `DANH MỤC` và `LazyColumn` các nhóm:**
       - Gồm `Yêu thích ⭐`, `Xem gần đây`, các nhóm kênh thuộc `scopedChannels`, và `Tất cả các kênh` kèm số lượng kênh ở bên phải.
       - Luôn ghim cố định (không bị ẩn khi chuyển focus sang cột giữa hoặc cột phải).
       - Phân biệt rõ 2 trạng thái: **Đang chọn (`selectedGroupId`)** vs **Đang focus (`focusArea == CATEGORY`)** để khi focus sang lưới kênh người dùng vẫn thấy rõ mình đang đứng ở danh mục nào (và đang chọn list kênh nào).

2. **`LiveTvMiddleGridPanel` (Khung Xanh dương + Lưới ô Hồng — `weight(1f)`, `fillMaxHeight()`):**
   - Header nhỏ gọn trên đỉnh (1 dòng mảnh): Tên danh mục đang chọn + tổng số kênh + chỉ báo nhỏ nếu có filter/trạng thái (giúp cân đối đỉnh trên với 2 cột hai bên).
   - **Lưới thẻ kênh (`LazyVerticalGrid` — mặc định 5 cột giống ảnh, hoặc 4 cột nếu bề ngang khả dụng hẹp hơn):**
     - Mỗi ô thẻ kênh (khung Hồng):
       - Logo kênh căn giữa phía trên (nền card tối sạch `#1A1A20`, có fallback icon/chữ khi không có logo).
       - Tên kênh ở đáy card (`1-2` dòng tùy setting `twoLineChannelNames`, ẩn nếu tắt `showChannelNames`).
       - Góc trên phải: icon ⭐ vàng nếu thuộc Yêu thích; góc trên trái (hoặc badge viền sáng): chỉ báo chấm đỏ/tím `ĐANG PHÁT` nếu `ch.key == playback.channelKey`.
       - Hiệu ứng focus chuẩn TV: viền focus sáng (`NuvioTheme.focusRing` hoặc `Primary`), nền nổi nhẹ, không scale quá lớn để tránh đè lưới lân cận.
   - **Empty States:** Hiển thị trạng thái trống thân thiện (giống sẵn có cho `Yêu thích ⭐`, `Xem gần đây`, hoặc danh mục không có kênh).
   - **Pre-resolve 0ms:** Giữ nguyên cơ chế `LaunchedEffect(channelIndex, ...)` gọi `LiveTvRepository.prepareForPlayback(ch)` sau `220ms` khi dừng focus ở 1 kênh để khi bấm OK phát tức thì.

3. **`LiveTvRightSidebarPanel` (Khung Tím + Khung Cam — rộng cố định ~`275.dp` – `295.dp`, `fillMaxHeight()`):**
   - **Phần trên (Khung Tím — Video Preview):**
     - Khung bo góc `12.dp`, tỉ lệ `16:9` (`fillMaxWidth().aspectRatio(16f / 9f)` — không để tràn mép).
     - Chứa `LivePlayerSurface(player = viewModel.playback.player)`.
     - Nếu `playback.channelKey == null`: hiển thị chữ `"Bấm OK trên kênh để phát"` (giống ảnh) kèm gợi ý nhỏ.
     - Nếu `playback.isBuffering`: hiển thị `CircularProgressIndicator`.
     - Khi focus trực tiếp vào Khung Preview: hiển thị viền focus + pill nhỏ `"OK: Toàn màn hình"`.
   - **Phần dưới (Khung Cam — Lịch phát sóng dọc / Vertical EPG, `weight(1f)`):**
     - Header: `"LỊCH PHÁT SÓNG · <Tên kênh>"` (kèm tag ngày nếu khác hôm nay).
     - `LazyColumn` danh sách chương trình (`focusedChannelPrograms` của kênh đang focus ở lưới — hoặc nếu lưới trống thì fallback kênh đang phát Preview):
       - Tự động cuộn tới chương trình đang phát (`isLive(now)`) khi đổi kênh ở lưới.
       - Mỗi item chương trình là một card dọc gọn:
         - Giờ `HH:mm - HH:mm` + badge `TRỰC TIẾP` (đỏ) hoặc `XEM LẠI` (nếu `stopMs <= now && ch.catchup != null && CatchupUrlBuilder.isAvailable(...)`) hoặc `SẮP CHIẾU`.
         - Tên chương trình (`1-2` dòng).
         - Thanh progress mini nếu đang phát trực tiếp.
       - Khi focus duyệt trên các chương trình trong Khung Cam: cập nhật thông tin chi tiết & mô tả ở **Cột Trái (trên)** theo đúng chương trình đang trỏ tới!

> [!NOTE]
> **Xử lý `LiveTvScreen.kt` (code cũ không còn gắn trong Navigation):**
> Kiểm tra xem [LiveTvScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvScreen.kt) có còn được dùng ở đâu khác ngoài preview/legacy không; không xóa bừa nếu chưa kiểm chứng, chỉ tái sử dụng ý tưởng card/state sạch trong `LiveTvGuideScreen`.

---

### Phase 2: State Machine Điều hướng D-pad & Tương tác TV
Định nghĩa lại `GuideFocusArea` thành **4 vùng** rõ ràng, không dùng focus tự do gây nhảy loạn của Compose trên bảng nhiều panel:
`enum class GuideFocusArea { CATEGORY, CHANNEL_GRID, PREVIEW_PLAYER, VERTICAL_EPG }`

1. **Điều hướng trong `CATEGORY` (Khung Vàng — `List kênh` & Danh mục cột trái):**
   - Index `0` (trên nhất) trong cột này là mục **`List kênh`**, các index từ `1..groups.size` là các mục trong `DANH MỤC`.
   - `UP` / `DOWN`: Di chuyển từ `List kênh` xuống `Yêu thích ⭐` -> các danh mục bên dưới (hoặc ngược lại). Khi di chuyển giữa các danh mục (`index >= 1`), cập nhật danh sách kênh ở Lưới Giữa ngay lập tức.
   - `OK` khi đang focus ở `List kênh` (`index == 0`): Mở popup/dropdown chọn List kênh (hỗ trợ `UP`/`DOWN` chọn, `OK` xác nhận, `BACK` đóng popup và giữ nguyên focus ở mục `List kênh`).
   - `RIGHT` (hoặc `OK` trên một danh mục): Chuyển focus sang `CHANNEL_GRID` (focus vào thẻ kênh đang chọn).
   - `LEFT`: Mở Sidebar chính của app (`LocalOpenSidebar.current?.invoke()`).
   - `BACK`: Nếu popup chọn `List kênh` đang mở -> đóng popup; ngược lại trả `false` cho hệ thống / NavHost xử lý thoát màn hình Live TV.

2. **Điều hướng trong `CHANNEL_GRID` (Lưới thẻ kênh ở giữa):**
   - `UP` / `DOWN` / `LEFT` / `RIGHT`: Tính toán theo `cols = 5` (chú ý xử lý chuẩn hàng cuối cùng không đủ 5 ô — không bị kẹt khi đi xuống từ hàng trên xuống hàng cuối!).
   - `LEFT` tại cột `0` (`channelIndex % cols == 0`): Chuyển focus về `CATEGORY` (giữ nguyên `drawerIndex` khớp `selectedGroupId`).
   - `RIGHT` tại cột cuối hàng (`channelIndex % cols == cols - 1` hoặc `channelIndex == channels.lastIndex`):
     - Nếu đang ở hàng đầu hoặc `showPreview == true` và muốn ưu tiên lên preview? Hợp lý nhất: nếu hàng đầu (`row == 0`) -> chuyển focus sang `PREVIEW_PLAYER`, từ hàng `>= 1` -> chuyển sang `VERTICAL_EPG` (hoặc luôn sang `VERTICAL_EPG` nếu có lịch, bấm `UP` ở mục đầu tiên của `VERTICAL_EPG` sẽ lên `PREVIEW_PLAYER`!).
   - `OK` (nhấn ngắn):
     - Nếu kênh chưa phát (`playback.channelKey != ch.key`): gọi `viewModel.play(ch, selectedGroupId)` để phát ngay trong **Khung Preview (Tím)**.
     - Nếu kênh đã đang phát trong Preview (`playback.channelKey == ch.key`): chuyển sang **Toàn màn hình** (`isFullscreen = true; showOsd = true`).
   - `OK` nhấn giữ (`600ms`) hoặc phím `MENU`/`STAR`: Thêm/bỏ **Yêu thích ⭐** + hiện Toast thông báo.
   - `BACK`: Đưa focus về `CATEGORY`.

3. **Điều hướng trong `PREVIEW_PLAYER` (Khung Tím) & `VERTICAL_EPG` (Khung Cam):**
   - Trong `PREVIEW_PLAYER`:
     - `OK`: Nếu đã có `playback.channelKey` -> mở Fullscreen (`isFullscreen = true; showOsd = true`); nếu chưa có -> gọi `viewModel.play(focusedChannel, selectedGroupId)`.
     - `DOWN`: Xuống `VERTICAL_EPG` (nếu có chương trình).
     - `LEFT`: Về `CHANNEL_GRID` (tại thẻ kênh đang chọn).
     - `BACK`: Về `CHANNEL_GRID`.
   - Trong `VERTICAL_EPG`:
     - `UP` / `DOWN`: Di chuyển qua các khung giờ của kênh. Tại `epgIndex == 0` bấm `UP` -> lên `PREVIEW_PLAYER`.
     - `LEFT` hoặc `BACK`: Quay về `CHANNEL_GRID` (trả lại focus cho đúng thẻ kênh đang chọn).
     - `OK`:
       - Nếu chương trình đang phát live (`now in p.startMs until p.stopMs`): phát live kênh này (nếu chưa phát) + mở Fullscreen (`isFullscreen = true`).
       - Nếu chương trình đã phát & hỗ trợ Catch-up (`CatchupUrlBuilder.isAvailable(...)`): build `catchupUrl` và phát xem lại (giữ nguyên logic hiện có trong [LiveTvGuideScreen.kt](file:///f:/MyGithub/NuvioTV-Fast/app/src/main/java/com/nuvio/tv/features/livetv/LiveTvGuideScreen.kt#L434-L452)).
       - Nếu chương trình tương lai: có thể phát live kênh hiện tại (hoặc thông báo chưa tới giờ phát).

4. **Chế độ Toàn màn hình (`isFullscreen = true`):**
   - **Giữ nguyên 100%** toàn bộ trải nghiệm Fullscreen đang hoạt động tốt (`UP`/`DOWN` chuyển kênh nhanh, `LEFT` mở `DANH SÁCH KÊNH` trượt ngang, `OK` bật/tắt OSD, `BACK` thu về lại giao diện 3 cột và giữ nguyên `channelIndex` + danh mục!).

---

### Phase 3: Tối ưu Hiệu năng & Đồng bộ Chuỗi Ngôn ngữ
1. **Hiệu năng cuộn & Render:**
   - Lưới thẻ kênh dùng key ổn định (`ch.key`), ảnh logo qua Coil size giới hạn rõ ràng, tránh tính toán nặng trong item composable (`focusedChannelPrograms` tính bằng `remember(focusedChannel?.key, programs[...], now / 60_000L)`).
   - Xử lý trường hợp `settings.showPreview == false`: Nếu người dùng tắt Preview trong Settings, ẩn Khung Tím (hoặc thu gọn thành header ngắn `"Chế độ Preview đang tắt"`), nhường toàn bộ chiều cao cột phải cho Khung Cam (`VERTICAL_EPG`), và bấm `OK` 1 lần vào kênh sẽ mở thẳng Fullscreen.
2. **Đa ngôn ngữ (`strings.xml` & `values-vi/strings.xml`):**
   - Đưa các chuỗi hiển thị cố định (`DANH MỤC`, `LỊCH PHÁT SÓNG`, `Bấm OK trên kênh để phát`, `TRỰC TIẾP`, `XEM LẠI`, v.v.) vào `res/values/strings.xml` và `res/values-vi/strings.xml` (nếu thích hợp) để đồng bộ chuẩn dự án.

---

## 4. Kế hoạch Kiểm chứng (Verification Plan)

### A. Kiểm tra Tự động (Automated Verification)
1. **Biên dịch đa flavor:**
   ```bash
   ./gradlew compileFullDebugKotlin compilePlaystoreDebugKotlin compileFullDebugAndroidTestKotlin compilePlaystoreDebugAndroidTestKotlin --console=plain
   ```
   -> Yêu cầu: `BUILD SUCCESSFUL`, không phát sinh warning mới.
2. **Unit Test D-pad & Logic chia cột / EPG / Catch-up:**
   - Tách pure helper tính toán điều hướng lưới (tính `nextIndex` khi bấm `UP`/`DOWN`/`LEFT`/`RIGHT` kể cả hàng cuối bị khuyết ô, kiểm tra ranh giới sang `CATEGORY` / `VERTICAL_EPG` / `PREVIEW_PLAYER`) và viết Unit Test kiểm chứng 100% các biên.
   - Chạy `./gradlew testFullDebugUnitTest --console=plain` đảm bảo không có regression.

### B. Kiểm tra Thủ công trên TV (Manual Checklist)
- [ ] Vào mục Live TV -> Mặc định `List kênh` là `"Tất cả"`, hiển thị đủ kênh từ tất cả playlist/nguồn đang bật; Cột Trái cố định rõ ràng, text mô tả không tràn sang cột giữa.
- [ ] Bấm `UP` lên mục `List kênh` trên nhất -> bấm `OK` hiện danh sách các list kênh (kèm số lượng kênh); chọn 1 list cụ thể -> `DANH MỤC` và Lưới kênh cập nhật theo đúng list đó; chọn lại `"Tất cả"` -> hiện lại toàn bộ.
- [ ] Bấm `UP`/`DOWN` ở Danh mục -> Lưới thẻ kênh ở giữa cập nhật đúng danh mục, số lượng kênh khớp.
- [ ] Bấm `RIGHT`/`OK` vào Lưới thẻ kênh -> Di chuyển 4 hướng mượt mà; thử hàng cuối cùng (khi số kênh không chia hết cho 5) vẫn đi `UP`/`DOWN` đúng, bấm `LEFT` ở cột đầu quay về Danh mục.
- [ ] Bấm `OK` lần 1 trên thẻ kênh -> phát video trong Khung Tím góc trên phải; bấm `OK` lần 2 -> mở Toàn màn hình; bấm `BACK` từ Toàn màn hình -> quay lại đúng vị trí kênh trong giao diện 3 cột.
- [ ] Nhấn giữ `OK` trên thẻ kênh -> thêm/xóa Yêu thích ⭐, số lượng trong mục `Yêu thích ⭐` ở cột trái cập nhật ngay.
- [ ] Bấm `RIGHT` từ cột cuối của Lưới kênh -> focus sang Khung Cam (Lịch phát sóng dọc) hoặc Khung Tím (Preview); duyệt lên xuống trong Khung Cam thấy Thông tin chi tiết ở Cột Trái trên cập nhật tương ứng; thử `OK` trên chương trình Live và chương trình Catch-up (nếu có).
- [ ] Từ Danh mục bấm `LEFT` -> mở được Sidebar chính của ứng dụng mà không bị kẹt focus.
