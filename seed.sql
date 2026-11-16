-- =====================================================================
-- DU LIEU MAU cho giao dien "Danh sach ho so kham suc khoe"
-- Chay trong Supabase SQL Editor SAU khi chay schema_supabase.sql.
--
-- BUOC 1: dang ky tai khoan benh nhan bang API (de mat khau duoc ma hoa BCrypt dung chuan):
--   POST http://localhost:8080/api/auth/register
--   {
--     "email": "an@demo.com", "password": "123456",
--     "fullName": "Nguyễn Văn An", "dateOfBirth": "1985-03-15",
--     "gender": "MALE", "bloodType": "O+", "phone": "0901234567", "address": "Cần Thơ"
--   }
-- BUOC 2: chay file nay. No gan du lieu cho benh nhan co email an@demo.com.
--
-- Tai khoan bac si tao o day chi de lam du lieu hien thi (password_hash la gia tri
-- gia, KHONG dang nhap duoc). Luong dang nhap bac si se lam o buoc sau.
-- =====================================================================

-- ---------- 6 bac si (da duoc duyet) ----------
INSERT INTO users (email, password_hash, full_name, role) VALUES
  ('bs.khoa@demo.local',  '!demo-khong-dang-nhap', 'Trần Minh Khoa',   'DOCTOR'),
  ('bs.nam@demo.local',   '!demo-khong-dang-nhap', 'Lê Hoàng Nam',     'DOCTOR'),
  ('bs.huong@demo.local', '!demo-khong-dang-nhap', 'Phạm Thị Hương',   'DOCTOR'),
  ('bs.duc@demo.local',   '!demo-khong-dang-nhap', 'Nguyễn Văn Đức',   'DOCTOR'),
  ('bs.lan@demo.local',   '!demo-khong-dang-nhap', 'Hoàng Thị Lan',    'DOCTOR'),
  ('bs.tung@demo.local',  '!demo-khong-dang-nhap', 'Võ Thanh Tùng',    'DOCTOR')
ON CONFLICT (email) DO NOTHING;

INSERT INTO doctors (user_id, specialty_id, license_no, approval_status, approved_at)
SELECT u.id, s.id, v.license, 'APPROVED', now()
FROM (VALUES
  ('bs.khoa@demo.local',  'noitiet',  'CCHN-0001'),
  ('bs.nam@demo.local',   'timmach',  'CCHN-0002'),
  ('bs.huong@demo.local', 'tongquat', 'CCHN-0003'),
  ('bs.duc@demo.local',   'tieuhoa',  'CCHN-0004'),
  ('bs.lan@demo.local',   'noikhoa',  'CCHN-0005'),
  ('bs.tung@demo.local',  'thankinh', 'CCHN-0006')
) AS v(email, spec_code, license)
JOIN users u       ON u.email = v.email
JOIN specialties s ON s.code  = v.spec_code
ON CONFLICT (user_id) DO NOTHING;

-- ---------- 7 ho so giong het giao dien ----------
INSERT INTO medical_records (record_code, patient_id, doctor_id, specialty_id, visit_at, reason, status)
SELECT v.code, p.id, d.id, d.specialty_id, v.visit_at::timestamp, v.reason, v.status
FROM (VALUES
  ('HS-2026-1042', 'bs.khoa@demo.local',  '2026-10-28 08:30', 'Tái khám đái tháo đường, điều chỉnh liều thuốc',  'COMPLETED'),
  ('HS-2026-0987', 'bs.nam@demo.local',   '2026-09-12 14:15', 'Kiểm tra huyết áp định kỳ, theo dõi Amlodipine',   'COMPLETED'),
  ('HS-2026-0851', 'bs.huong@demo.local', '2026-07-05 09:00', 'Khám sức khỏe định kỳ 6 tháng',                   'COMPLETED'),
  ('HS-2026-0723', 'bs.khoa@demo.local',  '2026-05-18 10:45', 'Kiểm tra HbA1c, tư vấn chế độ ăn',                 'COMPLETED'),
  ('HS-2026-0610', 'bs.duc@demo.local',   '2026-03-22 15:30', 'Đau thượng vị, nghi viêm dạ dày',                  'COMPLETED'),
  ('HS-2026-1105', 'bs.lan@demo.local',   '2026-11-01 07:45', 'Khám theo dõi sau xét nghiệm máu',                 'PENDING_RESULT'),
  ('HS-2026-0542', 'bs.tung@demo.local',  '2026-02-10 11:00', 'Đau đầu kéo dài – Bệnh nhân hủy lịch',             'CANCELLED')
) AS v(code, doctor_email, visit_at, reason, status)
JOIN users du   ON du.email = v.doctor_email
JOIN doctors d  ON d.user_id = du.id
JOIN users pu   ON pu.email = 'an@demo.com'
JOIN patients p ON p.user_id = pu.id
ON CONFLICT (record_code) DO NOTHING;

-- ---------- them 17 ho so de du 24 (thu phan trang) ----------
INSERT INTO medical_records (record_code, patient_id, doctor_id, specialty_id, visit_at, reason, status)
SELECT 'HS-2025-' || lpad(n::text, 4, '0'),
       p.id,
       d.id,
       d.specialty_id,
       timestamp '2025-12-20 08:00' - (n * interval '17 days'),
       'Khám định kỳ lần ' || n,
       'COMPLETED'
FROM generate_series(1, 17) AS n
JOIN users pu   ON pu.email = 'an@demo.com'
JOIN patients p ON p.user_id = pu.id
JOIN (
    SELECT id, specialty_id, row_number() OVER (ORDER BY id) - 1 AS rn FROM doctors
) d ON d.rn = (n - 1) % 6
ON CONFLICT (record_code) DO NOTHING;

-- ---------- phien ban 1 (noi dung chi tiet) cho moi ho so chua co ----------
INSERT INTO record_versions (record_id, version_no, diagnosis, prescription, note, created_by)
SELECT r.id, 1,
       CASE r.record_code
         WHEN 'HS-2026-1042' THEN 'Đái tháo đường type 2, kiểm soát đường huyết tạm ổn'
         WHEN 'HS-2026-0987' THEN 'Tăng huyết áp độ 1, đáp ứng tốt với điều trị'
         WHEN 'HS-2026-0851' THEN 'Sức khỏe tổng quát bình thường'
         WHEN 'HS-2026-0723' THEN 'HbA1c cao hơn mục tiêu, cần điều chỉnh chế độ ăn'
         WHEN 'HS-2026-0610' THEN 'Viêm dạ dày nhẹ'
         WHEN 'HS-2026-1105' THEN 'Đang chờ kết quả xét nghiệm'
         WHEN 'HS-2026-0542' THEN NULL
         ELSE 'Khám định kỳ, chưa ghi nhận bất thường'
       END,
       CASE WHEN r.status = 'COMPLETED' THEN 'Theo toa của bác sĩ (dữ liệu mẫu)' END,
       CASE WHEN r.status = 'CANCELLED' THEN 'Bệnh nhân hủy lịch khám' ELSE 'Dữ liệu mẫu để thử giao diện' END,
       du.id
FROM medical_records r
JOIN doctors d ON d.id = r.doctor_id
JOIN users du  ON du.id = d.user_id
WHERE NOT EXISTS (
    SELECT 1 FROM record_versions v WHERE v.record_id = r.id AND v.version_no = 1
);
