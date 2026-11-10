# Kiểm thử luồng tạo giải — 2026-11-10

Kiểm thử trực tiếp trên localhost:8080/Tourma bằng trình duyệt, đối chiếu log Tomcat và source. Đây là kết quả một phiên kiểm thử, không phải chứng nhận toàn bộ luồng đã đạt.

## Phát hiện

| Mức | Hiện tượng | Bằng chứng và tác động |
| --- | --- | --- |
| P1 | Trang xuất hiện từng phần, thiếu trạng thái chờ | Giải con mới: thấy header/sidebar trước, rồi bộ nút thể thức thô, vùng đội xuất hiện sau. GET /rolling/tournament-teams 42,578ms lúc 23:23:33; lần sau thêm đội rồi redirect mất 13,543ms. Không có skeleton/loading/error trong vùng còn thiếu. Người dùng có thể tưởng trang đã hỏng. |
| P1 | Lưu điểm và mở sơ đồ chậm cộng dồn | POST /rolling/point-config 9,526ms, 302 lúc 23:24:22; GET /common/single-elimination.jsp 15,744ms lúc 23:24:38. Quan sát trung gian chỉ có logo và nền trống. Không dùng timeout của công cụ trình duyệt làm số đo HTTP. |
| P2 | Thể thức chưa lưu bị mất khi reload, thiếu chỉ dẫn trạng thái lưu | Giải đơn mới: thêm 4 đội, chọn Double Elimination, reload; danh sách 4 đội còn, thể thức trở lại Single Elimination. Không thấy cảnh báo rời trang hay nhãn chưa lưu. Đây là mất lựa chọn chưa submit, không phải bằng chứng DB tự đổi thể thức đã lưu. |
| P2 | Popup sao chép có hai thanh cuộn lồng nhau | Desktop và viewport 390×844 đều thấy cuộn nội dung popup và cuộn danh sách bên trong. Chiếm phần lớn chiều cao, đặc biệt trên điện thoại. Không tràn ngang ở 390px. |
| P2 | Popup thiếu hành vi bàn phím/ngữ nghĩa | Search được focus khi mở, nhưng Escape không đóng. Không có role=dialog hoặc phần tử dialog trong DOM khi đang mở. Chưa kiểm chứng đầy đủ focus trap và screen reader. |
| P2 | Validation thiếu thống nhất với giao diện | Bấm Tiếp tục khi 0 đội không thấy lỗi trong trang; source dùng window.alert cho thiếu đội/trùng tên/cấu hình không hợp lệ. Công cụ không thu được hộp thoại native nên không kết luận trình duyệt thông thường hoàn toàn không báo lỗi. Cần lỗi cạnh vùng nhập, focus tới lỗi và thông báo truy cập được. |

## Điểm đã kiểm tra đạt

- Tên giải trống bị chặn bằng native validation, focus về ô tên.
- Giải đơn không sao chép: tạo tên → thêm 4 đội → SE → sơ đồ đúng 4 đội; Back giữ danh sách.
- Giải con không sao chép: tạo Series riêng → Tier D → thêm 4 đội → điểm thưởng hiển thị SINGLE_ELIMINATION; POST chuyển tới single-elimination.jsp, không chuyển GS. Chưa xác nhận sơ đồ Series hiển thị hoàn chỉnh vì lần quan sát cuối còn đang tải.
- Popup sao chép mở giữa màn, có nền mờ, search được focus, nút Đóng hoạt động; điện thoại không tràn ngang.
- Không sửa dữ liệu giải hiện có trong quá trình kiểm thử.

## Nguồn cần xử lý

Log ghi các query mất 7–15 giây, trong khi pool không có request chờ (waiting=0): chưa có bằng chứng cạn pool trong các lần này. Không thể kết luận nguyên nhân SQL chỉ từ log thời gian.

JSP cấu hình trực tiếp gọi DAO để lấy giải hiện tại và danh sách nguồn sao chép; JSP sơ đồ trực tiếp gọi TournamentDAO/ParticipantDAO/SingleEliminationDAO. setup-presentation.js và standalone-format-layout.js dựng lại bố cục sau DOMContentLoaded. Đây là các điểm cần tách việc nạp dữ liệu khỏi render và giảm đổi bố cục sau khi trang đã xuất hiện; chưa có trace để quy toàn bộ độ chậm cho một query cụ thể.

Ưu tiên: đo và xử lý query chậm; hoàn thiện khung loading/error/retry; render bố cục cuối ngay từ đầu; thống nhất trạng thái lưu/chưa lưu; chuẩn hóa popup và validation. Chưa kiểm thử đồng thời, double-submit, lỗi mạng giữa chừng, tất cả thể thức, hay hoàn thành giải.

## Dữ liệu QA còn giữ để tái hiện

- Standalone: QA NFR 20261110 standalone — T_038cb168, 4 đội QA.
- Series: QA NFR 20261110 Series — S_1501601e.
- Giải con: QA NFR 20261110 Series - Stop #1 — TOURNEY_1794327770809, Tier D, 4 đội QA.

Chưa xóa các bản ghi QA; không trộn chúng vào Series đang sử dụng. Không thay đổi code ứng dụng trong đợt audit này.
