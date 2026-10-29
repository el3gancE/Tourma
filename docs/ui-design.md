# Tourma — Quy chuẩn giao diện

Tài liệu này là điểm tham chiếu khi thiết kế hoặc sửa bất kỳ màn nào. Các màn
có thể có bố cục khác nhau theo công việc, nhưng phải dùng chung ngôn ngữ thiết kế.

## Trạng thái thiết kế

- Trang chủ hiện tại là bản thử; người dùng dự định làm lại. Không sao chép bố cục
  hoặc các chi tiết minh họa ở đó như một thiết kế đã được duyệt.
- Hướng màu hiện tại: light nền trắng + wine; dark đen + sky blue. Emerald là hướng cũ.
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
| `web/common/template.jsp` | Trang trưng bày component; không phải nơi duy nhất lưu thiết kế |
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
5. Trang Template UI phải trưng bày chính component/style dùng trong màn thật,
   không duy trì một bộ CSS mẫu độc lập lệch với ứng dụng.

## Vị trí chuyển theme

Nút chuyển sáng/tối nằm trong header dùng chung, không lặp trong nội dung trang.
Các màn đã nạp token và theme.js truyền `themeToggle=true` vào header.jsp để bật nút.

## Light mode: nền dịu dùng chung

Nền trang, header/sidebar và canvas dùng trắng ngà `#F7F6F3`; card dùng `#FDFCF9`, vùng phụ dùng `#EEECE6`. Dùng token chung, không đặt nền trắng tinh riêng cho từng màn. Chữ trên nút màu nhấn vẫn giữ trắng để đủ tương phản. Dark mode giữ nguyên.

Font Readex Pro dùng weight 500 cho nội dung, 600 cho tên đội/menu và 700 cho tiêu đề/đội thắng.

## Popup dùng chung

`web/css/popup.css` cung cấp lớp nền, khung, header/footer và control cho popup tỉ số, xác nhận, thêm đội, sửa Series và stage. CSS từng popup nhập stylesheet này; không đặt style popup riêng trong CSS của màn SE.
