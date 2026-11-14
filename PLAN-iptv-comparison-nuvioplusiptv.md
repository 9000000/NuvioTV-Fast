# Kế hoạch đối chiếu IPTV: NuvioTV-Fast và NuvioPlusIPTV

## Mục tiêu

Lập ma trận có bằng chứng về mức độ hoàn thiện, trải nghiệm, kiến trúc và rủi ro của chế độ IPTV trong hai dự án. Kết quả dùng để quyết định phần nào nên giữ, cải thiện hoặc cân nhắc đưa vào NuvioTV-Fast; không mặc định rằng toàn bộ chức năng của dự án tham chiếu đều cần port.

## Mốc mã nguồn

| Dự án | Nhánh / commit dùng làm baseline | Ghi chú |
|---|---|---|
| Repo đang mở: `NuvioTV-Fast` | `tet2` / `3979358bb` | Đối chiếu module IPTV hiện có trong `app/src/main/java/com/nuvio/tv/features/livetv`. |
| Repo tham chiếu: `NuvioPlusIPTV` | `dev` / `8df56e1e3d3c171f6312a1b29ea0e7b5321add00` | Nhánh `dev` là nhánh mặc định tại thời điểm khảo sát; repo có cả nhánh `main`, nên mọi so sánh sau này cần pin commit. |

## Phạm vi

**Phần IPTV cốt lõi:** cấu hình nguồn, đọc playlist, quản lý kênh/nhóm, EPG, phát live, catch-up/timeshift, ghi hình và nhắc lịch.

**Phần mở rộng cần tách riêng:** VOD từ Xtream, Sports, Calendar, đồng bộ cấu hình, VPN và ứng dụng điện thoại. Chúng có thể phụ thuộc vào tài khoản, dịch vụ ngoài hoặc những phần khác của Nuvio; không gộp chúng thành yêu cầu bắt buộc cho Live TV.

## Snapshot sơ bộ

| Nhóm | NuvioTV-Fast hiện tại | NuvioPlusIPTV tham chiếu | Hướng xác minh khi làm ma trận |
|---|---|---|---|
| Nguồn IPTV | Có playlist M3U từ URL/tệp, nhiều playlist, Xtream Codes và Stalker Portal. | Có M3U/XMLTV, Xtream và Stalker; Xtream có tùy chọn import TV và VOD. | So sánh luồng thêm/sửa/xóa/kiểm tra nguồn, nhiều nguồn cùng lúc, lỗi đăng nhập, lưu thông tin nhạy cảm. |
| Parser và tương thích stream | Có parser M3U riêng, hỗ trợ metadata, header và một số thuộc tính DRM; có cache và tải nhiều nguồn. | Parser và pipeline dữ liệu lớn hơn, có metadata catch-up, XMLTV và xử lý theo provider. | Dùng cùng bộ playlist mẫu để so độ bao phủ cú pháp, tốc độ tải, lỗi parse và khả năng giữ metadata tới player. |
| Duyệt kênh | Có lưới kênh, nhóm, playlist, favorites, recent và tìm kiếm/lọc. | Có Live TV guide dạng lịch, màn hình phát/guide overlay, tìm kiếm và tùy chỉnh nhóm/kênh. | So thao tác remote, số lần bấm để mở kênh, quay lại kênh trước, lưu trạng thái và điều hướng focus. |
| EPG | Chưa thấy parser XMLTV, database chương trình hoặc màn hình guide trong module IPTV hiện tại. | Có XMLTV parser/database, hợp nhất nhiều guide, ghép kênh và chỉnh lệch giờ. | Đo độ chính xác ghép kênh, timezone/DST, guide trống/sai giờ, cập nhật nền và dung lượng cache. |
| Catch-up và timeshift | Chưa thấy trong module IPTV hiện tại. | Có phát lại chương trình cũ, xem từ đầu, pause/rewind live và xử lý lệch giờ catch-up. | Kiểm tra URL catch-up theo provider, seek, hết hạn archive, TS/HLS và đồng bộ giờ guide với giờ server. |
| Ghi hình và nhắc lịch | Chưa thấy scheduler/recordings/reminders trong module IPTV hiện tại. | Có ghi hình thủ công/theo EPG, ghi series, danh sách recording và nhắc chương trình. | Kiểm tra chạy nền/standby, quyền lưu trữ, phục hồi sau mất mạng/reboot, trùng lịch và thông báo. |
| VOD | Chưa thấy VOD IPTV trong module Live TV; cần xác minh có tích hợp ở luồng nội dung chung hay không. | Có On Demand cho phim/series của Xtream, tìm kiếm, nhóm và chọn phiên bản stream. | Xác định ranh giới giữa catalog IPTV và catalog phim chung; kiểm tra trùng nội dung, metadata và autoplay. |
| Tính năng liên kết | Cần xác minh Live TV có tham gia account sync/tìm kiếm chung hay không. | Sports tìm kênh đang phát trận; Calendar gắn lịch chương trình với EPG; Setup Sync đồng bộ cấu hình. | Phân biệt tính năng chạy cục bộ với tính năng cần API/tài khoản/dữ liệu thể thao bên ngoài. |
| VPN/điện thoại | Không thấy trong module Live TV đang khảo sát. | Có VPN tích hợp; phần điện thoại là dự án riêng dùng chung cấu hình theo README. | Chỉ phân tích như hạng mục mở rộng; xem dependency, quyền Android, bảo mật và chi phí bảo trì. |

Snapshot trên là định hướng từ cấu trúc mã và tài liệu, chưa phải kết luận chất lượng hay xác nhận hành vi runtime. Trạng thái “chưa thấy” cần được kiểm tra thêm trên toàn luồng trước khi chốt là “không có”.

## Quy trình đối chiếu

### 1. Chốt phạm vi và lập danh mục tính năng

- Chốt thiết bị đích: Android TV/Google TV/Fire TV; tách khác biệt nếu có giao diện điện thoại.
- Chia feature thành: IPTV core, tích hợp với Nuvio, và tiện ích ngoài phạm vi Live TV.
- Gắn trạng thái cho từng mục: **Có**, **Một phần**, **Chưa thấy**, **Chưa xác minh**.
- Mỗi kết luận phải có đường dẫn file/luồng UI hoặc test tương ứng; README chỉ dùng để tìm điểm cần kiểm tra.

### 2. So sánh nguồn và dữ liệu

- Kiểm tra M3U URL, import file, nhiều playlist, Xtream, Stalker và XMLTV.
- So sánh thông tin tài khoản, stream format, headers/DRM, nhóm kênh, logo, channel ID và metadata provider.
- Theo dõi dữ liệu từ màn hình Settings → model → repository/parser → cache/database → player.
- Ghi rõ cách refresh, tải song song, xử lý playlist lớn, lỗi từng nguồn và giữ dữ liệu cũ khi refresh lỗi.

### 3. So sánh guide và trải nghiệm xem

- Đối chiếu channel grid với EPG grid/list, overlay khi đang phát, lịch theo ngày và xem thông tin chương trình.
- Kiểm tra tìm kiếm, nhóm, favorites, recent, hidden/renamed channels, numbering, zap và khôi phục kênh gần nhất.
- Ghi lại thao tác remote/focus, trạng thái loading/empty/error và khả năng dùng khi provider không có EPG.

### 4. So sánh playback nâng cao

- Lập bảng tương thích HLS, MPEG-TS, headers, DRM và hành vi retry/error recovery.
- Kiểm tra live pause/rewind, catch-up, xem từ đầu, seek và lựa chọn TS/HLS khi provider hỗ trợ nhiều định dạng.
- Đánh giá timezone server, timezone người dùng, DST và các offset riêng cho guide/catch-up.
- Đo startup time, thời gian chuyển kênh, bộ nhớ khi phát playlist lớn và hành vi khi mạng chập chờn.

### 5. So sánh recording, reminders và tích hợp

- Lần theo vòng đời đặt lịch → scheduler/background service → ghi stream/catch-up → lưu trữ → phát lại/xóa.
- Kiểm tra lịch nhắc, thông báo, ghi series, va chạm lịch, dung lượng còn trống và resume sau standby/reboot.
- Với VOD, Sports, Calendar và sync: xác định nguồn dữ liệu, API bên ngoài, dữ liệu đồng bộ, quyền riêng tư và điểm phụ thuộc vào tài khoản.
- Xác định rõ feature nào có giá trị trực tiếp cho người xem IPTV và feature nào chỉ hợp lý khi làm sản phẩm Nuvio mở rộng.

### 6. Đánh giá kiến trúc, bảo mật và bằng chứng chất lượng

- So sánh model/repository/service/UI, persistence, cache invalidation, concurrency và dependency vào player.
- Kiểm tra cách bảo vệ username/password, log/diagnostics, TLS, cấu hình backup/sync và VPN routing.
- Lập danh mục test hiện có theo parser, timezone, playback, scheduler, UI remote và migration dữ liệu.
- Không dùng số lượng file hoặc dòng code làm chỉ số chất lượng; ưu tiên độ bao phủ use case, tính cô lập module và khả năng kiểm thử.

## Thang điểm và ưu tiên

Chấm mỗi hạng mục trên hai trục riêng:

- **Mức đáp ứng (0–5):** 0 chưa có; 1 mới có nền tảng; 2 một phần; 3 luồng chính dùng được; 4 bao phủ lỗi/cấu hình tốt; 5 đã kiểm chứng đầy đủ trên thiết bị và dữ liệu thực tế.
- **Ưu tiên xem xét (P0–P3):** P0 độ tin cậy nguồn/phát; P1 guide và trải nghiệm Live TV; P2 tiện ích như catch-up/recording/VOD; P3 mở rộng như Sports, Calendar, VPN và mobile.

Mỗi đề xuất port/cải thiện ghi thêm giá trị người dùng, effort, rủi ro, phụ thuộc, file liên quan và quyết định **giữ / cải thiện / cân nhắc port / không làm**.

## Thứ tự thực hiện và đầu ra

1. **Kiểm kê mã nguồn:** bảng tính năng có trạng thái và đường dẫn file ở cả hai repo.
2. **Đối chiếu use case:** flow cấu hình nguồn, tải playlist, tìm kênh, mở stream, xem guide, xem lại và ghi hình.
3. **Đối chiếu kỹ thuật:** sơ đồ data flow, persistence, player integration, background work, API và security.
4. **Chấm điểm và xếp hạng gap:** P0–P3, effort/risk/dependency; tách các tính năng core khỏi phần mở rộng Nuvio.
5. **Chốt đề xuất:** danh sách việc nên làm cho NuvioTV-Fast theo từng đợt, tiêu chí hoàn tất và các feature chủ động loại khỏi phạm vi.

### Tiêu chí hoàn tất

- Mỗi feature trong README tham chiếu được gắn trạng thái dựa trên mã hoặc luồng UI, không chỉ dựa trên mô tả.
- Các tính năng “chưa thấy” đã được tìm trên toàn luồng liên quan trước khi kết luận thiếu.
- Ma trận chỉ rõ phần nào hai bên đã có tương đương, phần nào khác về cách làm và phần nào là gap thực sự.
- Đề xuất cuối có thứ tự, chi phí/rủi ro tương đối, phụ thuộc và phạm vi kiểm chứng; không tự động chọn port toàn bộ sản phẩm tham chiếu.

## Điểm vào mã nguồn

**NuvioTV-Fast:** [models](app/src/main/java/com/nuvio/tv/features/livetv/LiveTvModels.kt), [repository](app/src/main/java/com/nuvio/tv/features/livetv/LiveTvRepository.kt), [M3U parser](app/src/main/java/com/nuvio/tv/features/livetv/M3uStreamParser.kt), [Xtream](app/src/main/java/com/nuvio/tv/features/livetv/XtreamPortalService.kt), [Stalker](app/src/main/java/com/nuvio/tv/features/livetv/StalkerPortalService.kt), [Live TV screen](app/src/main/java/com/nuvio/tv/features/livetv/LiveTvScreen.kt), [settings](app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsScreen.kt).

**NuvioPlusIPTV (`dev`):** [README](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/README.md), [Live TV repository](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/data/LiveTvRepository.kt), [XMLTV parser](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/data/XmltvParser.kt), [guide screen](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/ui/LiveTvGuideScreen.kt), [catch-up URL builder](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/data/CatchupUrlBuilder.kt), [timeshift](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/player/LiveTvTimeshift.kt), [recording](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/record/LiveTvRecorder.kt), [On Demand](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/ondemand/OnDemandRepository.kt), [Sports](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/sports/SportsRepository.kt), [cloud sync](https://github.com/homelessbrian/NuvioPlusIPTV/blob/dev/app/src/main/java/com/nuvio/tv/livetv/sync/LiveTvCloudSync.kt).
