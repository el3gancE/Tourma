# Lỗi chọn thể thức giải con — 2026-11-11

## Nguyên nhân và bản sửa

Form POST của màn đội có input ẩn name="action" (bulkAdd, saveTeamsOrder, addPartnerTeams...). Trên HTMLFormElement, input này che thuộc tính form.action. series-setup.js dùng form.action để nhận diện endpoint nên bỏ qua nhánh sao chép cấu hình vào form POST. Danh sách đội vẫn được lưu, nhưng format/type/stage/config mới không đi theo request. Giải mới vì thế thường giữ mặc định SE.

Đã dùng form.getAttribute('action') để đọc URL thật. Tăng version series-setup.js trong JSP và cập nhật bản chạy build/web. Không sửa DAO, thuật toán hay dữ liệu giải có sẵn. Các giải đã tạo sai trước bản sửa không tự được sửa ngược.

## Kiểm tra

- Trước sửa: Series QA chọn GS → SE, 8 đội, đi tiếp lại nhận SINGLE_ELIMINATION tại điểm thưởng.
- Sau sửa: cùng fixture chuyển đúng manage-group.jsp?format=GROUP_STAGE; mở lại màn đội giữ hai giai đoạn, GS, số đi tiếp 4.
- Sau sửa: chuyển cùng fixture sang DE một giai đoạn, màn điểm thưởng nhận DOUBLE_ELIMINATION; chuyển RR nhận ROUND_ROBIN. Không phát hiện console error.
- Giải đơn mới DE 4 đội hiển thị đủ nhánh thắng/thua; RR 4 đội hiển thị 3 vòng/6 trận.
- Test series-setup.cjs mô phỏng chính xác action bị che bởi input, kiểm tra 6 format SE/DE/RR/GS/GSL/Swiss, dữ liệu stage/config/copy/exclusion, validation và thao tác thêm đội cho phép cấu hình chưa hoàn chỉnh.
- Test standalone-setup.cjs, standalone-format-layout.cjs, format-restore.cjs, setup-startup-performance.cjs pass. PointConfigRoutingCheck pass cho các route thể thức.

Giới hạn: GSL/Swiss được kiểm tra gửi cấu hình và ánh xạ route bằng test, chưa chạy UI xuyên suốt tới hoàn thành giải. Chưa xác minh giai đoạn 2 trên dữ liệu thi đấu thực trong đợt này.

Fixture QA còn giữ: T_3c3288db (standalone DE), T_fb014660 (standalone RR), TOURNEY_1794335008279 trong Series QA S_1501601e (cuối đợt ở RR, 8 đội). Không dùng Series đang vận hành để thử POST.
