# Tourma — Quy chuẩn giao diện

## Luồng thiết lập giải

Giải đơn dùng luồng Lập giải → Thể thức & đội tham gia → Chia bảng nếu cần → Thi đấu.
Chọn một/hai giai đoạn, thể thức và cấu hình nằm cùng màn thêm đội; nút Tiếp tục
gửi cả cấu hình lẫn danh sách đội. URL cấu hình thể thức cũ chuyển về màn gộp.
Giải con trong Series cũng gộp thể thức vào màn thêm đội, dùng chung phần cấu hình gọn.
Giữ cách chọn đội theo Series, phạm vi sao chép cùng Series và bước set điểm riêng.

Tài liệu này là điểm tham chiếu khi thiết kế hoặc sửa bất kỳ màn nào. Các màn
có thể có bố cục khác nhau theo công việc, nhưng phải dùng chung ngôn ngữ thiết kế.

## Trạng thái thiết kế

- Trang chủ hiện tại là bản thử; người dùng dự định làm lại. Không sao chép bố cục
  hoặc các chi tiết minh họa ở đó như một thiết kế đã được duyệt.
- Có hai lựa chọn màu nhấn dùng chung token: Bộ Đỏ (Crimson Rose) và Bộ Xanh (Royal Blue), mỗi bộ dùng đúng 1 mã màu duy nhất trên cả Light và Dark mode. Chọn bằng hai switch Đỏ/Xanh và Sáng/Tối trên header.
- Sắc độ nền/card vẫn đang thử nghiệm, chưa được người dùng chốt về mặt thẩm mỹ.
  Dùng bộ token hiện tại để giữ đồng bộ; không tự pha một bảng màu mới cho mỗi màn.
- Nội dung header (nhãn, thứ tự và URL) được giữ nguyên. Có thể đổi hình thức khi
  thiết kế giao diện, trừ khi người dùng có chỉ dẫn khác.

## Nguồn thiết kế dùng chung

| Nguồn | Vai trò |
| --- | --- |
| `web/css/design-tokens.css` | Nguồn duy nhất cho màu của hai theme; nơi bổ sung các token dùng chung |
| `web/css/ui-base.css` | Reset và style nền tảng dùng chung; áp dụng qua class `ui-page` |
| `web/css/header.css` | Header và nút chuyển theme dùng chung |
| `web/css/tournament-screen.css` | Khung màn thi đấu chung cho SE, DE, Swiss, GSL, GS và RR: toolbar, viewport, vòng và danh sách |
| `web/css/tournament-workspace.css` | Khung điều hướng giải và sidebar dùng chung cho màn cấu hình/thêm đội |
| `web/css/workspace.css` | Khung màn danh sách: tiêu đề, bộ lọc, tìm kiếm, nút và trạng thái trống dùng chung |
| `web/js/theme.js` | Áp dụng theme, ghi nhớ lựa chọn và cập nhật nút chuyển |
| `web/common/component/` | JSP thành phần dùng lại: header, card trận, popup và các component khác |
| Component JSP và stylesheet dùng chung | Nguồn thiết kế trực tiếp; trang Template UI đã ngừng sử dụng |
| CSS của từng màn | Bố cục riêng; tham chiếu token và thành phần chung |

Hiện nhiều file CSS đã được chủ động làm trắng để xây lại frontend. Việc một
component JSP tồn tại không có nghĩa hình thức của component đó đã được chốt.
`index.css` là stylesheet riêng của bản thử trang chủ, không phải bộ component chung.

## Quy tắc màu và theme

- Mỗi màn phải hỗ trợ cả light và dark qua cùng một cấu trúc HTML/JSP.
- Dùng `var(--ui-bg)`, `var(--ui-surface)`, `var(--ui-text)`, `var(--ui-muted)`,
  `var(--ui-line)` và `var(--ui-accent)`; xem đầy đủ trong file token.
- Không hardcode các màu nền/chữ/viền riêng cho từng màn. Nếu cần vai trò màu mới,
  thêm token có nghĩa ở cả hai theme rồi dùng lại.
- Theo hướng thử hiện tại, nền trang và nền khung lớn dùng chung `--ui-bg`.
  Card bên trong dùng `--ui-surface`; vùng nhấn nhẹ dùng `--ui-surface-alt`.
- Nền trung tính; không tự thêm sắc xanh lá, đỏ hoặc lớp phủ màu vào nền dark.
- Màu nhấn dành cho hành động chính, focus, mục đang chọn và chi tiết quan trọng.
  Tránh phủ màu nhấn lên toàn bộ khung hoặc mọi tên đội.
- Màu thắng/thua/lỗi là màu ngữ nghĩa riêng, không đổi ý nghĩa theo theme và không
  dùng màu làm dấu hiệu duy nhất. Chốt token trạng thái khi xây component liên quan.
- Dùng key `tourma-ui-theme`, thuộc tính `data-theme` trên thẻ `html` và các hook
  `data-theme-toggle` / `data-theme-label` hiện có; không tạo cơ chế lưu theme thứ hai.
- Nạp script theme trong head trước stylesheet để hạn chế nháy sai màu khi mở trang.

## Thành phần và bố cục

- Font nền tảng hiện tại là Readex Pro qua `--ui-font`.
- Tiết chế mô tả: không thêm slogan, câu dẫn hoặc lời giải thích lặp lại nhãn. Chỉ giải thích lựa chọn khó hiểu, ràng buộc và lỗi; không thêm nội dung phụ để lấp khoảng trống.
- Cùng một loại button, input, card, bảng và popup phải có cùng hình thức giữa các màn.
  Khi bắt đầu tái sử dụng, tách style đó vào stylesheet component dùng chung thay vì
  sao chép từ CSS trang chủ.
- Khi chốt spacing, font size, radius hoặc kích thước control cho bộ component,
  bổ sung token dùng chung và cập nhật tài liệu này. Chưa coi các số đo trong
  `index.css` là quy chuẩn đã được duyệt.
- Bố cục từng màn phục vụ đúng công việc; không ép mọi màn theo layout trang chủ.
- Phải có focus bàn phím rõ, nhãn control và cách dùng được ở màn hình hẹp.
- CSS riêng được scope theo trang/component để không vô tình đổi các màn khác.
- CSS trang trí đặt trong stylesheet. Inline chỉ dùng cho giá trị thực sự động
  và hành vi đang được JavaScript điều khiển như ẩn/hiện, zoom, vị trí hoặc tiến độ.

## Giữ nguyên chức năng

- Phạm vi mặc định là frontend. Không sửa servlet, DAO, database hoặc thuật toán
  ghép cặp để phục vụ thay đổi hình thức.
- Khi viết lại HTML trong JSP, giữ dữ liệu server, URL, tên input và hợp đồng gửi form.
  Nếu đổi DOM, cập nhật các hook JavaScript tương ứng và kiểm tra hành vi liên quan.
- Giữ thứ tự thiết lập phù hợp với thể thức: thêm đội, chia bảng nếu có, set điểm,
  rồi thi đấu. Không thay logic chuyển stage bằng trạng thái đoán ở trình duyệt.

## Cách duy trì

1. Đọc tài liệu và token trước khi sửa màn.
2. Dùng component chung; chỉ viết CSS riêng cho phần bố cục thực sự riêng.
3. Kiểm tra cả hai theme, màn hình hẹp và các hành động chính của màn vừa sửa.
4. Khi người dùng chốt một quy ước mới, cập nhật nguồn dùng chung và tài liệu này.
5. Duy trì component/style ngay trong nguồn dùng chung; không cần trang Template UI riêng.

## Vị trí chuyển theme

Nút chuyển sáng/tối nằm trong header dùng chung, không lặp trong nội dung trang.
Các màn đã nạp token và theme.js truyền `themeToggle=true` vào header.jsp để bật nút.

## Light mode: nền dịu dùng chung

Nền trang, header/sidebar và canvas dùng trắng `#FFFFFF`; card dùng `#F8FAFC`, vùng phụ dùng `#E5E9EE`. Dùng token chung cho các màn. Chữ trên nút màu nhấn vẫn giữ trắng để đủ tương phản. Dark mode giữ nguyên.

Font Readex Pro dùng weight 500 cho nội dung, 600 cho tên đội/menu và 700 cho tiêu đề/đội thắng.

## Popup dùng chung

`web/css/popup.css` cung cấp lớp nền, khung, header/footer và control cho popup tỉ số, xác nhận, thêm đội, sửa Series và stage. CSS từng popup nhập stylesheet này; không đặt style popup riêng trong CSS của màn SE.

## Bộ màu mở rộng

Theme lưu bằng tourma-ui-theme (light/dark), màu nhấn lưu bằng tourma-ui-accent (wine/blue). theme.js áp dụng data-theme và data-accent trước khi render. Không tạo stylesheet theme riêng theo màn.
Mỗi bộ nhấn dùng đúng 1 mã màu duy nhất cho cả Light và Dark mode:
- Bộ Xanh (Blue): Royal Blue `#2563EB` cho cả Light và Dark (hover `#1D4ED8` sáng / `#3B82F6` tối, chữ trên nút màu trắng `#FFFFFF`).
- Bộ Đỏ (Wine): Crimson Velvet `#BE123C` cho cả Light và Dark (hover `#9F1239` sáng / `#E11D48` tối, chữ trên nút màu trắng `#FFFFFF`).

Hệ màu phụ và ngữ nghĩa dùng thống nhất 1 bộ duy nhất cho cả Light và Dark mode:
- Tier S: Bù trừ quang học theo nền — Vàng tươi `#FACC15` cho Dark mode (sáng rực rỡ trên nền tối), Vàng kim tươi `#D49500` cho Light mode (đậm đà, tương phản chuẩn WCAG 4.7:1 trên nền trắng).
- Tier A: Tím hoàng gia `#7C3AED` cho cả Light và Dark.
- Tier B: Lục bảo `#059669` cho cả Light và Dark.
- Tier C: Xanh biển `#0284C7` cho cả Light và Dark.
- Tier D: Xám chì `#64748B` cho cả Light và Dark.
- Trận chung kết / Vô địch: `#FACC15` cho Dark mode, `#D49500` cho Light mode.
- Điểm cộng (+), tăng hạng (▲), thành tích tốt (`--ui-success`): Xanh lá `#16A34A` mặc định (không đổi theo theme accent).
- Giảm điểm (-), giảm hạng (▼), nguy hiểm (`--ui-danger`): Đỏ `#BE123C`.
- Nhánh thua (LB): Cam đất `#EA580C` (ở bộ Xanh) và Royal Blue `#2563EB` (ở bộ Đỏ) cho cả Light và Dark.
- 32 bảng GSL: A theo UB (`var(--ui-accent)`), B theo LB (`var(--ui-bracket-lower-accent)`); C–AF dùng dải quang phổ 30 màu cố định, dùng chung 1 bộ duy nhất cho cả Light và Dark.

Nền trang, header/sidebar, workspace và canvas dùng chung --ui-bg: sáng #FFFFFF, tối #121212. Card dùng token surface riêng để giữ phân lớp.
Cả hai bộ nhấn dùng cùng màu trung tính cho surface, hàng trận, viền, chữ và shadow. Không pha xanh vào nền card/viền ở bộ xanh; khác biệt nằm ở accent và các màu ngữ nghĩa của nhánh/nhóm.

Header desktop cao 64px, chạy hết chiều rộng trang. Mục active phủ cả chiều cao ô điều hướng và có gạch dưới 3px theo accent; không dùng pill nổi. Mobile tách hàng menu cuộn ngang.

Cài đặt hiển thị trên header có công tắc Màu phân biệt nhánh/nhóm. Lưu tourma-ui-special-colors (on/off), áp dụng data-special-colors ngay trong theme.js. Tắt sẽ cho LB/final/Swiss/GSL dùng accent chung; không đổi tier và badge.

## CSS màn cấu hình giải

Màn Thể thức & đội tham gia của giải đơn và giải con Series dùng một stylesheet riêng: `web/css/tournament-setup.css`. Token, nền tảng, navigation và popup tiếp tục dùng stylesheet chung. Không nạp thêm CSS riêng cho phần format hoặc thêm đội ở hai màn này.

## Màn Bảng xếp hạng Series (Rolling Standings)

Ở màn Bảng Xếp Hạng Series: Hạng 1 nhận màu Vàng Tier S (`--ui-tier-s`). Tên của toàn bộ các đội trong Top 10 (Rank 1–10) và số thứ tự Hạng 2–10 mang màu accent (`--ui-accent`) để làm nổi bật nhóm tinh anh dẫn đầu; các đội từ Rank 11 trở đi dùng màu chữ chuẩn (`--ui-text`). Điểm cộng và tăng hạng luôn giữ màu Xanh lá (`--ui-success`).

## Bảng vàng dạng dashboard

Bảng vàng đặt số liệu tổng quan, biểu đồ số mốc giữ #1 và các kỷ lục ở đầu trang; bảng danh hiệu, bộ lọc Tier và lịch sử chi tiết nằm phía dưới. Dùng `web/css/rolling/rolling-hof.css` và token/theme chung. Một mốc #1 là BXH tích lũy sau một giải đã hoàn tất.

Ba bảng nhà vô địch, các giải và lịch sử #1 nằm trên một hàng ở desktop. Lịch sử #1 tách từng đợt liên tiếp, ghi từ giải đến giải; STT đánh từ đợt đầu nhưng hiển thị đợt mới nhất trước. Danh sách giải cũng hiển thị giải mới nhất trước. Mobile chuyển về một cột.

## Component New #1

`web/common/component/new-number-one.jsp` nhận hai request attribute `newNumberOneEligible` và `newNumberOneName`. Màn BXH chỉ include component; giao diện và hành vi nằm ở `web/css/components/new-number-one.css` và `web/js/components/new-number-one.js`. Màu nền công bố dùng token `--ui-celebration-*` gồm 4 màu gradient động có độ tương phản cao, chuyển động rõ nét (chu kỳ 8-11s, biên độ lớn), điểm xuyết các đường vệt sáng trắng mảnh (white light streaks) lướt chéo qua banner, không dùng màu vàng, bỏ chữ phụ "BẢNG XẾP HẠNG SERIES". Phối 4 màu theo theme: Bộ Đỏ 100% thuần sắc đỏ, triệt tiêu toàn bộ xanh/tím (`#BE123C` Crimson Velvet, `#DC2626` Scarlet Red, `#991B1B` Deep Blood Red, `#4C0519` Deep Wine Maroon); Bộ Xanh (`#2563EB` Royal Blue, `#06B6D4` Aqua Cyan, `#A855F7` Electric Violet, `#0F172A` Deep Navy). Không có icon vương miện.

Popup hiện mỗi lần mở BXH sau khi đổi người giữ #1, đến khi giải tiếp theo hoàn tất. Giữ nguyên #1 ở giải tiếp theo thì hết popup; đổi chủ thì hiện tên mới. Người dẫn đầu đầu tiên khi chưa từng đổi ngôi không tạo popup. Không lưu trạng thái đã xem trong browser storage; chuyển mốc lịch sử không kích hoạt popup. Quay lại BXH qua back/forward cache tải lại điều kiện từ server. Component hiển thị như banner stage trong luồng nội dung, ngay trên bảng BXH; không có dialog, backdrop, khóa cuộn hay tự chiếm focus. Khi chọn mốc lịch sử, banner ẩn; quay về mốc mới nhất sẽ hiện lại nếu còn đủ điều kiện. Hỗ trợ giảm chuyển động.
