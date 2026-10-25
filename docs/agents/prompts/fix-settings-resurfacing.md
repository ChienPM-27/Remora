# Kế hoạch sửa: Settings & Resurfacing

## Bối cảnh & Mục tiêu
Lưu bộ này thành file fix-settings-resurfacing.md, tạo từng sub agent, để fix từng task có trong file.

TASK 2 (sau Task 1): Capture/notification phải tôn trọng paused, pause_until, notifications_enabled; nối nút "Tắt 1 giờ".

Đọc: capture/ScreenshotObserver.kt, NotificationHelper.kt, CaptureActionReceiver.kt, ui/capture/SavePromptSheet.kt, ui/RemoraMainScreen.kt (callbacks của sheet), work/CardAnalyzeWorker.kt, MainActivity.kt (ScreenCaptureCallback).

Làm:
1. ScreenshotObserver dùng CapturePolicy.isCaptureSuppressed(setting, now) thay vì chỉ paused == true. Áp dụng cùng kiểm tra cho đường ScreenCaptureCallback (API 34+) trong MainActivity để không bật sheet khi đang tạm dừng/tắt.
2. Notification chụp màn hình có đúng 3 nút: Lưu (giữ RemoteInput ghi chú), Bỏ qua, Tắt 1 giờ (ACTION_MUTE_1H đang khai báo nhưng chưa dùng). Chuyển "Tạm dừng" sang SavePromptSheet và Settings. Ghi quyết định này vào docs/decisions.md (lý do: Android chỉ hiện tối đa 3 nút).
3. Tách logic xử lý action thành hàm thuần/suspend dễ test, vd. applyCaptureAction(action, dao, nowMs): MUTE_1H -> setPauseUntil(now + 3_600_000); PAUSE -> setPaused(true). CaptureActionReceiver gọi hàm này. Giữ goAsync, timeout, và dismiss notification như hiện tại.
4. SavePromptSheet: nút "Tắt thông báo" đổi thành "Tắt 1 giờ" = setPauseUntil(now + 1h); "Tạm dừng" = setPaused(true). Không đụng notifications_enabled hay consent. Hiện Toast xác nhận ngắn.
5. CardAnalyzeWorker chỉ gọi NotificationHelper.showResurfacingAlert khi shouldShowResurfacingAlert(setting) là true. Pipeline resurfacing và lưu kết quả vẫn chạy như cũ.

Test: applyCaptureAction với fake DAO và clock cố định; shouldShowResurfacingAlert.

Kiểm tra máy thật (liệt kê cho tôi làm): chụp ảnh -> thấy 3 nút; bấm Tắt 1 giờ -> chụp tiếp không có prompt, sau 1 giờ (hoặc chỉnh giờ máy) lại có; Tạm dừng trong sheet -> không có prompt cho đến khi tiếp tục (Task 3); tắt switch gợi nhớ -> vẫn lưu và có kết quả nhưng không có notification gợi nhớ; sau mọi thao tác trên, mở lại app KHÔNG hiện màn đồng ý.

TASK 3 (sau Task 2): SettingsScreen hiện dùng remember state, không lưu và không nối vào đâu.

Đọc: ui/settings/SettingsScreen.kt, ui/RemoraMainScreen.kt, MainActivity.kt (cách tạo ViewModel hiện có), AppSettingDao.getSettingFlow().

Làm:
1. Tạo SettingsViewModel (ViewModel thường, nhận AppSettingDao, theo đúng kiểu các ViewModel hiện có; không thêm DI/factory framework) phơi StateFlow từ getSettingFlow().
2. Switch "tự động nhận diện ảnh chụp": BẬT = không bị chặn theo CapturePolicy. Tắt -> setPaused(true). Bật -> setPaused(false) và setPauseUntil(0). Khi đang tắt có thời hạn, switch hiển thị TẮT và dòng phụ "Đang tắt đến HH:mm". Khi đang tạm dừng vô thời hạn, dòng phụ "Đang tạm dừng".
3. Switch "thông báo gợi nhớ kiến thức" ghi notifications_enabled qua setNotificationsEnabled.
4. Xoá các biến remember cục bộ autoDetectEnabled/notificationEnabled. Trạng thái phải sống sót khi app bị kill và mở lại.
5. Hàm định dạng nhãn trạng thái là hàm thuần (nhận setting, now, locale/zone inject được).
6. Thẻ "cam kết quyền riêng tư": sửa câu chữ cho đúng thực tế, không dùng "tuyệt đối" và không khẳng định không có OCR/văn bản nào rời máy. Nêu đúng: ảnh đã nén + ghi chú gửi lên khi bấm Lưu; tóm tắt/tag/ghi chú của tối đa 15 thẻ liên quan gửi khi phân tích gợi nhớ; câu tìm kiếm gửi để tạo embedding. Chỉ đổi câu chữ, giữ bố cục.

Test: SettingsViewModel với fake DAO (chuỗi thao tác bật/tắt; tắt có thời hạn rồi bật lại xoá pause_until); hàm nhãn trạng thái.
Kiểm tra máy thật: Tạm dừng từ notification -> Settings hiện "Đang tạm dừng" -> bật lại -> chụp ảnh có prompt; kill app giữa chừng, trạng thái giữ nguyên.

TASK 4: Resurfacing sai ở tầng dữ liệu: kết quả của mọi thẻ bị trộn, trùng candidate, cắt theo thứ tự LLM thay vì confidence.

Đọc: core/model/.../resurface/ResurfaceModels.kt (ResurfaceValidator), app/.../resurfacing/{ResurfacingManager,Stage2BatchEvaluator}.kt, data/local/dao/ResurfacingResultDao.kt, ui/gallery/GalleryViewModel.kt, các test ResurfaceModelsTest / ResurfacingValidatorTest / ResurfacingFlowTest.

Lỗi cần xác minh:
- GalleryViewModel.resurfacedItems = 20 kết quả mới nhất của TẤT CẢ thẻ nguồn, trộn lẫn.
- filterValidItems không dedupe candidateId nên một thẻ có thể xuất hiện ở hai nhóm (docs yêu cầu gộp khi Bổ trợ và Có giá trị sử dụng trùng); và cắt "tối đa 2 mỗi nhóm" theo thứ tự LLM trả về.
- deleteResultsForCard tồn tại nhưng không dùng, nên chạy lại có thể nhân đôi dòng.

Làm:
1. filterValidItems: sau khi lọc (confidence > 0.80, id hợp lệ, relation khớp group): (a) sắp theo confidence giảm dần; (b) mỗi candidateId giữ một mục, confidence cao nhất, hoà thì ưu tiên ACTIONABLE > COMPLEMENTARY > SIMILAR; (c) rồi mới áp giới hạn 2 mục mỗi nhóm. Ghi comment rằng đây là cách hiện thực "gộp khi trùng". Không đổi enum, không đổi bảng Room.
2. ResurfacingManager: xoá kết quả cũ của newCardId trước khi insert.
3. DAO: thêm truy vấn lấy new_card_id có kết quả mới nhất. GalleryViewModel thay resurfacedItems bằng state gồm {sourceCard: MemoryCard?, items} của đúng bộ kết quả mới nhất (dùng getResultsForCardFlow + flatMapLatest). Thẻ nguồn bị xoá không được làm crash.
4. KHÔNG sửa prompt hay schema ở ai-service trong task này (cần gọi API thật để kiểm chứng theo AGENTS.md).

Test: dedupe khác nhóm, tie-break, thứ tự confidence, cap 2 SAU dedupe, id không hợp lệ; manager xoá-rồi-chèn với fake DAO; ViewModel chọn đúng thẻ nguồn mới nhất và đổi khi có kết quả mới.

TASK 5 (sau Task 4): Tab Gợi nhớ hiện là danh sách phẳng, dưới tiêu đề của thẻ mới nhất bất kể kết quả thuộc thẻ nào.

Đọc: ui/resurfacing/{ResurfacingScreen,ResurfacingBanner,ResurfacingDetailDialog}.kt, ui/RemoraMainScreen.kt, ui/gallery/CardDetailScreen.kt (CardDetailDialog).

Làm:
1. Header: tiêu đề = tiêu đề thẻ nguồn lấy từ state mới của Task 4; số liên kết = số mục của đúng bộ đó.
2. Chia 3 mục "tương đồng", "bổ trợ", "có giá trị sử dụng"; chỉ hiện mục có phần tử; trong mục sắp theo confidence. Logic nhóm là hàm thuần (groupForDisplay) có unit test.
3. Mỗi dòng: thumbnail, tiêu đề, reason, và phần bung ra hiển thị new_info và benefit (hiện chưa hiển thị ở đâu). Chạm vào mở CardDetailDialog như hiện tại.
4. Không gọi .lowercase() trên title/reason (làm hỏng công thức, tên riêng, mã); chỉ giữ chữ thường cho nhãn UI cố định.
5. Trạng thái rỗng phân biệt rõ: chưa có thẻ nào; thẻ mới nhất đang PENDING ("AI đang phân tích..."); thẻ nguồn DONE nhưng không có liên kết ("chưa tìm thấy liên kết đủ chắc").
6. ResurfacingBanner và ResurfacingDetailDialog hiện không được dùng. Đọc rồi quyết định: dùng nếu hợp cho phần new_info/benefit, nếu không thì xoá. Không để code chết trong phần này.
Không thêm UI test mới (AGENTS.md chỉ cho 1 Compose smoke test).

Kiểm tra máy thật: với dữ liệu demo, lưu một thẻ liên quan -> tab hiện đúng thẻ nguồn, đúng nhóm, bung được new_info/benefit; lưu thêm thẻ khác -> tab chuyển sang bộ mới, không trộn.

TASK 6 (sau Task 5): Notification gợi nhớ chỉ nói "có N liên kết hữu ích", không có nhóm hay lý do.

Đọc: capture/NotificationHelper.kt (showResurfacingAlert), work/CardAnalyzeWorker.kt, MainActivity.kt, ui/RemoraMainScreen.kt (currentTab).

Làm:
1. showResurfacingAlert nhận danh sách mục (group, tiêu đề ứng viên, reason) và tiêu đề thẻ nguồn thay vì (title, count). Dùng BigTextStyle: dòng đầu là tiêu đề thẻ nguồn; thân tối đa 3 dòng "nhóm · tiêu đề — lý do (cắt ~80 ký tự)", chọn mục confidence cao nhất mỗi nhóm trước. Hàm dựng nội dung là hàm thuần; cắt chuỗi không được cắt giữa ký tự tiếng Việt hay surrogate pair.
2. PendingIntent mở app vào tab Gợi nhớ: extra EXTRA_OPEN_RESURFACING (+ newCardId), xử lý ở MainActivity (onCreate/onNewIntent) rồi truyền thành initialTab cho RemoraMainScreen. FLAG_IMMUTABLE, requestCode riêng, không đè notification chụp màn hình.
3. Vẫn tôn trọng notifications_enabled (kiểm tra không bị gọi hai lần).
4. Không log nội dung notification.

Test: hàm dựng nội dung (ít hơn 3 mục, cắt chuỗi, thứ tự ưu tiên, tiếng Việt có dấu).
Kiểm tra máy thật: lưu một thẻ liên quan -> notification đúng nội dung -> chạm vào đúng tab với đúng bộ kết quả; tắt switch gợi nhớ -> không có notification nhưng kết quả vẫn hiện trong tab.

TASK 7 (sau Task 6): Rà soát cuối cho nhóm sửa này.

1. Chạy ./gradlew lint testDebugUnitTest assembleDebug và dán output thực tế.
2. Đồng bộ tài liệu: AGENTS.md mục 4 (cột app_setting mới + ngữ nghĩa 4 cờ), docs/decisions.md (quyết định ngữ nghĩa cờ, 3 nút notification), docs/architecture liên quan nếu có nhắc app_setting, tasks.md (đánh dấu T032 theo hiện trạng thực tế).
3. grep xác nhận: không còn insertOrUpdate(AppSettingEntity( ở nơi đổi một cờ; không còn ".lowercase()" trên title/reason ở màn Gợi nhớ; không còn tham chiếu tới các file/hàm đã xoá.
4. Liệt kê checklist kiểm tra thủ công trên máy demo (tối đa 12 bước) gộp từ Task 1-6, kèm kết quả mong đợi, để tôi chạy.
5. Báo rõ việc KHÔNG làm trong đợt này: ghi chú mặc định "ôn thi giữa kỳ", copy ảnh vào bộ nhớ riêng, ngưỡng similarity Stage 1, auth theo từng cài đặt, độ bền foreground service, xoá code chết ngoài phần resurfacing.

Nhóm 1: Cài đặt và capture

#	Việc cần sửa	Prompt
1.1	Tách consent_granted khỏi notifications_enabled, Room 1→2 có migration	1
1.2	Bỏ 4 chỗ ghi đè cả dòng app_setting, chuyển sang cập nhật từng cột	1
1.3	CapturePolicy thuần (paused + pause_until + thông báo gợi nhớ)	1
1.4	Observer và ScreenCaptureCallback tôn trọng tạm dừng/tắt có thời hạn	2
1.5	Nối “Tắt 1 giờ” vào notification, sheet và receiver; chuyển “Tạm dừng” sang sheet/Settings	2
1.6	Notification gợi nhớ chỉ hiện khi notifications_enabled = true	2
1.7	SettingsScreen lưu thật qua SettingsViewModel, có đường Tiếp tục sau Tạm dừng	3
1.8	Sửa câu chữ thẻ “cam kết quyền riêng tư” cho đúng thực tế (đi kèm)	3

Nhóm 2: Resurfacing

#	Việc cần sửa	Prompt
2.1	Lọc theo confidence → dedupe candidate_id → cap 2/nhóm (đúng thứ tự)	4
2.2	Xoá kết quả cũ của thẻ nguồn trước khi chèn lại	4
2.3	Chỉ hiện bộ kết quả của đúng thẻ nguồn mới nhất, không trộn 20 kết quả	4
2.4	Chia 3 mục theo nhóm, hiện reason + new_info + benefit	5
2.5	Bỏ .lowercase() trên nội dung thẻ; phân biệt 3 trạng thái rỗng	5
2.6	Xử lý ResurfacingBanner/ResurfacingDetailDialog (dùng hoặc xoá)	5
2.7	Notification gợi nhớ có nhóm + lý do, chạm mở đúng tab	6
2.8	Đồng bộ docs, tasks.md, checklist máy thật	7
