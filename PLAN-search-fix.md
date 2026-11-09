# PLAN: Rà Soát & Khắc Phục Toàn Diện Lỗi Điều Hướng & Cuộn Tự Động Mục Tìm Kiếm (Search Screen TV)

> **Dành cho Agentic Workers**: Áp dụng quy trình thực thi từng tác vụ với kiểm thử chặt chẽ. Mỗi bước sử dụng checkbox (`- [ ]`) để theo dõi tiến độ.

**Mục tiêu**: Loại bỏ 100% hiện tượng màn hình nhảy loạn, giật cục, mất tiêu điểm và tự động cuộn lung tung khi người dùng nhấn "OK" để tìm kiếm trên Android TV.

**Kiến trúc giải pháp**:
1. Chuẩn hóa luồng gửi tìm kiếm (`SubmitSearch`): Nếu Live Search đã có kết quả cho từ khóa hiện tại, không xóa dữ liệu để tải lại từ đầu (tránh flash trắng và mất View); chỉ lưu lịch sử và chuyển giao tiêu điểm mượt mà.
2. Hợp nhất 2 cơ chế `LaunchedEffect` đang xung đột thành một State Machine duy nhất: Chỉ chuyển tiêu điểm và cuộn xuống hàng kết quả đúng **1 lần duy nhất** khi có kết quả. Các đợt trả về kết quả tiếp theo từ các Addon khác tuyệt đối không được cướp tiêu điểm hay kích hoạt lại cuộn.
3. Tách biệt việc cuộn dọc `LazyColumn` với việc focus ngang: Trong `onItemFocused`, chỉ kích hoạt cuộn dọc khi người dùng thực sự chuyển hàng (`rowIndex`), không cuộn lại khi đang duyệt ngang giữa các thẻ trong cùng một hàng.
4. Đóng bàn phím ảo (IME) ổn định trước khi chuyển giao tiêu điểm để tránh xung đột Layout Insets.

**Tech Stack**: Jetpack Compose TV, Android Leanback Native (`InteropHorizontalGridView`), Kotlin Coroutines, StateFlow, FocusRequester.

---

## 📌 Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis)

Qua rà soát chuyên sâu mã nguồn tại `SearchScreen.kt`, `SearchViewModel.kt` và `CatalogRowSection.kt`, đã phát hiện 4 nguyên nhân cốt lõi gây ra hiện tượng "nhảy loạn và cuộn lung tung":

1. **Xung Đột Đua Lệnh (Race Condition) Giữa 2 `LaunchedEffect` Tự Động Focus**:
   - `LaunchedEffect(pendingAutoMoveToResultsOnLoad, uiState.isSearching, visibleCatalogRows.size)` (dòng 466) và `LaunchedEffect(pendingFocusMoveToResultsQuery, ...)` (dòng 480) cùng chạy song song khi nhấn OK.
   - Do phụ thuộc vào `visibleCatalogRows.size`, mỗi khi một Addon mới trả về kết quả (size tăng từ 1 -> 2 -> 3...), `LaunchedEffect` lại bị kích hoạt lại, liên tục gọi `animateScrollToItem(1)` và cướp tiêu điểm trở lại hàng đầu tiên, giằng co với thao tác của người dùng.

2. **Xóa Sạch Kết Quả Cũ Khi Đã Có Sẵn Dữ Liệu Từ Live Search**:
   - Khi người dùng đang gõ, Live Search đã nạp sẵn kết quả. Khi người dùng nhấn OK để xác nhận, `SearchViewModel.submitSearch()` lại gọi `performSearch` và đặt `catalogRows = emptyList()`.
   - Toàn bộ các hàng kết quả trên màn hình bị hủy (disposed) ngay tức khắc trong khi coroutine cuộn đang chạy, khiến tiêu điểm bị văng mất và `LazyColumn` giật ngược về vị trí 0. Vài chục mili-giây sau khi Addon trả kết quả mới, màn hình lại giật xuống lần nữa.

3. **Lệnh Cuộn Kép (Double Scroll) Và Cuộn Thừa Khi Duyệt Thẻ Ngang**:
   - Trong `CatalogRowSection.onItemFocused`: Dòng 937 gọi `coroutineScope.launch { listState.animateScrollToItem(index + 1) }` cho **mọi thẻ nhận focus**. Khi người dùng chỉ duyệt sang trái/phải giữa các phim trong cùng hàng 0, lệnh cuộn dọc `animateScrollToItem(1)` vẫn liên tục bị bắn ra.
   - Khi chuyển hàng từ 0 xuống 1, `onNavigateDown` gọi `animateScrollToItem(2)`, và khi hàng 1 nhận focus, `onItemFocused` lại gọi tiếp `animateScrollToItem(2)` lần thứ hai, làm hỏng chuyển động êm ái của Compose.

4. **Biến Động Chiều Cao Màn Hình Do Đóng Bàn Phím Ảo (IME Window Insets)**:
   - Khi nhấn OK/Done, bàn phím ảo đóng lại làm viewport của `LazyColumn` giãn rộng đột ngột. Nếu lệnh cuộn `animateScrollToItem` chạy đồng thời trong cùng frame, tọa độ mục tiêu bị tính sai lệch, dẫn đến việc cuộn lố hoặc giật nảy màn hình.

---

## 🗺️ Kế Hoạch Triển Khai Chi Tiết (Implementation Tasks)

### 📋 Task 1: Tối Ưu Hóa `SearchViewModel` Tránh Hủy Rows Trùng Lặp
**Mục tiêu**: Khi người dùng nhấn OK/Submit với từ khóa mà Live Search đã tải xong kết quả, giữ nguyên các hàng kết quả hiện có, không reset về `emptyList()`, chỉ cập nhật lịch sử tìm kiếm.

**Files:**
- Modify: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchViewModel.kt:443-465, 525-610`

**Chi tiết các bước thực hiện:**
- [x] **Step 1.1**: Kiểm tra trong `submitSearch()`: Nếu `_uiState.value.query.trim() == activeSearchQuery` và `catalogRows.isNotEmpty()`, không hủy danh sách hàng, chỉ thực hiện lưu từ khóa vào `searchHistoryDataStore.saveRecentSearch(...)`.
- [x] **Step 1.2**: Trong `performSearch(rawQuery, ...)`: Đảm bảo nếu từ khóa trùng khớp với kết quả đang hiển thị và không yêu cầu cưỡng chế làm mới, tái sử dụng kết quả thay vì gán `catalogRows = emptyList()`.

---

### 📋 Task 2: Hợp Nhất & Tái Cấu Trúc Cơ Chế Tự Động Focus Trong `SearchScreen.kt`
**Mục tiêu**: Xóa bỏ xung đột giữa các `LaunchedEffect`, thiết lập một quy trình chuyển giao tiêu điểm duy nhất, dứt khoát và mượt mà.

**Files:**
- Modify: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt:407-515`

**Chi tiết các bước thực hiện:**
- [x] **Step 2.1**: Loại bỏ biến cờ thừa `pendingAutoMoveToResultsOnLoad` và các trạng thái chạy đua không cần thiết.
- [x] **Step 2.2**: Thiết lập quy trình chuyển focus khi Submit (`submitCurrentQuery`):
  - Ẩn bàn phím ảo ngay lập tức: `keyboardController?.hide()`.
  - Nếu đã có kết quả sẵn (`canMoveToResults == true`):
    - Đợi 1-2 frames (`withFrameNanos`) để Layout Insets sau khi ẩn bàn phím ổn định.
    - Cuộn êm ái tới hàng kết quả: `listState.animateScrollToItem(1)`.
    - Đưa tiêu điểm vào thẻ đầu tiên của hàng đầu tiên: `searchRowEntryFocusRequesters[firstKey]?.requestFocus()`.
  - Nếu chưa có kết quả (đang nạp):
    - Đặt cờ duy nhất `pendingFocusOnResultsReady = true`.
    - Khi đợt kết quả đầu tiên xuất hiện (`visibleCatalogRows.isNotEmpty()`): Chuyển focus **đúng 1 lần**, sau đó xóa cờ ngay lập tức để ngăn việc các Addon nạp sau kích hoạt lại.
- [x] **Step 2.3**: Xóa bỏ `LaunchedEffect` trùng lặp ở dòng 466 và dòng 480, thay bằng một `LaunchedEffect(pendingFocusOnResultsReady, visibleCatalogRows.size)` có chốt bảo vệ `if (pendingFocusOnResultsReady && visibleCatalogRows.isNotEmpty())`.

---

### 📋 Task 3: Tối Ưu Hóa Cuộn Dọc Trong `onItemFocused` & Điều Hướng Giữa Các Hàng
**Mục tiêu**: Chỉ cuộn dọc khi người dùng thực sự đổi hàng, không cuộn dọc khi duyệt ngang giữa các thẻ trong cùng một hàng.

**Files:**
- Modify: `app/src/main/java/com/nuvio/tv/ui/screens/search/SearchScreen.kt:860-955`

**Chi tiết các bước thực hiện:**
- [x] **Step 3.1**: Trong `CatalogRowSection.onItemFocused`:
  - Thêm biến theo dõi hàng đang active: `var currentActiveRowIndex by remember { mutableIntStateOf(-1) }`.
  - Chỉ gọi `listState.animateScrollToItem(index + 1)` khi `currentActiveRowIndex != index`.
  - Khi người dùng chỉ duyệt trái/phải (`itemIndex` thay đổi nhưng `index` không đổi), tuyệt đối không gọi `animateScrollToItem`.
- [x] **Step 3.2**: Trong `onNavigateDown` và `onNavigateUp`:
  - Đồng bộ việc cập nhật `currentActiveRowIndex` trước khi kích hoạt cuộn để tránh xung đột với callback `onItemFocused`.

---

### 📋 Task 4: Kiểm Thử Tự Động & Xác Minh Nghiệm Thu Toàn Diện
**Mục tiêu**: Đảm bảo mã nguồn biên dịch hoàn hảo và trải nghiệm thực tế đạt chuẩn 60fps trên Android TV.

**Files:**
- Test / Build verification: `gradlew compileFullDebugKotlin`

**Chi tiết các bước thực hiện:**
- [x] **Step 4.1**: Chạy lệnh kiểm tra biên dịch toàn bộ Kotlin code:
  ```powershell
  ./gradlew compileFullDebugKotlin
  ```
  *Yêu cầu*: 0 lỗi cú pháp, build SUCCESSFUL.
- [x] **Step 4.2**: Kiểm tra kịch bản trải nghiệm người dùng (Manual Verification Checklist):
  1. **Kịch bản 1 (Live search đã có kết quả -> Bấm OK)**:
     - Nhập "batman" -> Chờ kết quả hiển thị -> Bấm OK trên remote.
     - *Kỳ vọng*: Bàn phím ẩn đi, màn hình cuộn mượt mà đưa hàng kết quả đầu tiên vào tầm nhìn, tiêu điểm nằm ngay ngắn ở poster đầu tiên, không có hiện tượng giật ngược lên ô tìm kiếm hay cuộn lung tung.
  2. **Kịch bản 2 (Bấm OK khi kết quả chưa nạp xong)**:
     - Nhập từ khóa mới -> Bấm OK ngay lập tức.
     - *Kỳ vọng*: Bàn phím ẩn đi, khi kết quả đầu tiên xuất hiện thì tiêu điểm tự động đáp xuống poster đầu tiên đúng 1 lần, các hàng tiếp theo load xong không làm giật màn hình.
  3. **Kịch bản 3 (Duyệt thẻ ngang trong cùng một hàng)**:
     - Khi đang ở hàng kết quả, nhấn D-pad Phải/Trái qua lại giữa các thẻ.
     - *Kỳ vọng*: Màn hình đứng yên, chỉ có hàng ngang cuộn các thẻ poster, không kích hoạt cuộn dọc LazyColumn.
  4. **Kịch bản 4 (Duyệt dọc giữa các hàng)**:
     - Nhấn D-pad Xuống/Lên giữa các hàng kết quả.
     - *Kỳ vọng*: Màn hình cuộn chuyển hàng êm ái, hàng tiếp theo luôn nằm trong khung nhìn.

