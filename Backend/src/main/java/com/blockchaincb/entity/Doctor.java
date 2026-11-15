package com.blockchaincb.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ánh xạ 13 cột của public.doctors; chưa tự thay đổi schema của nhóm. */
@Entity
@Table(name = "doctors")
public class Doctor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long maBacSi;

    @Column(name = "user_id", nullable = false)
    private Long maNguoiDung;

    @Column(name = "specialty_id", nullable = false)
    private Long maChuyenKhoa;

    @Column(name = "license_no")
    private String soGiayPhepPhu;

    @Column(name = "approval_status", nullable = false)
    private String trangThaiPheDuyet;

    @Column(name = "approved_by")
    private Long maNguoiPheDuyet;

    @Column(name = "approved_at")
    private LocalDateTime ngayPheDuyet;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String hoTen;

    @Column(name = "hospital", nullable = false)
    private String benhVien;

    @Column(name = "license_number", nullable = false)
    private String soGiayPhep;

    // Chỉ ánh xạ cột hiện có, KHÔNG sử dụng để xác thực.
    @Column(name = "password", nullable = false)
    private String matKhauCu;

    @Column(name = "status", nullable = false)
    private String trangThaiTaiKhoan;

    public Long getMaBacSi() { return maBacSi; }
    public Long getMaNguoiDung() { return maNguoiDung; }
    public Long getMaChuyenKhoa() { return maChuyenKhoa; }
    public String getSoGiayPhepPhu() { return soGiayPhepPhu; }
    public String getTrangThaiPheDuyet() { return trangThaiPheDuyet; }
    public Long getMaNguoiPheDuyet() { return maNguoiPheDuyet; }
    public LocalDateTime getNgayPheDuyet() { return ngayPheDuyet; }
    public String getEmail() { return email; }
    public String getHoTen() { return hoTen; }
    public String getBenhVien() { return benhVien; }
    public String getSoGiayPhep() { return soGiayPhep; }
    public String getTrangThaiTaiKhoan() { return trangThaiTaiKhoan; }
}
