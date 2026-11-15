package com.blockchaincb.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.blockchaincb.dto.AdminUserPageResponse;
import com.blockchaincb.dto.AdminUserResponse;
import com.blockchaincb.entity.User;
import com.blockchaincb.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Truy vấn và phân trang tài khoản thật; không trả mật khẩu. */
@Service
public class AdminUserService {
    private final UserRepository khoNguoiDung;

    public AdminUserService(UserRepository khoNguoiDung) { this.khoNguoiDung = khoNguoiDung; }

    @Transactional(readOnly = true)
    public AdminUserPageResponse layDanhSachNguoiDung(
            String tuKhoa, String vaiTro, String trangThai, int trang, int kichThuoc) {
        if (trang < 0 || kichThuoc < 1 || kichThuoc > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Trang phải >= 0 và kích thước trang từ 1 đến 100");
        }
        String boLocVaiTro = chuanHoaVaiTro(vaiTro);
        Boolean boLocHoatDong = chuanHoaTrangThai(trangThai);
        String boLocTuKhoa = tuKhoa == null ? "" : tuKhoa.trim().toLowerCase(Locale.ROOT);

        Specification<User> dieuKien = (goc, truyVan, taoDieuKien) -> {
            List<Predicate> danhSachDieuKien = new ArrayList<>();
            if (!boLocTuKhoa.isBlank()) {
                String mau = "%" + boLocTuKhoa + "%";
                danhSachDieuKien.add(taoDieuKien.or(
                    taoDieuKien.like(taoDieuKien.lower(goc.get("email")), mau),
                    taoDieuKien.like(taoDieuKien.lower(goc.get("hoTen")), mau)
                ));
            }
            if (boLocVaiTro != null) {
                danhSachDieuKien.add(taoDieuKien.equal(
                    taoDieuKien.upper(goc.get("vaiTro")), boLocVaiTro));
            }
            if (boLocHoatDong != null) {
                danhSachDieuKien.add(taoDieuKien.equal(goc.get("dangHoatDong"), boLocHoatDong));
            }
            return taoDieuKien.and(danhSachDieuKien.toArray(new Predicate[0]));
        };
        Page<User> ketQua = khoNguoiDung.findAll(dieuKien,
            PageRequest.of(trang, kichThuoc, Sort.by(Sort.Direction.DESC, "maNguoiDung")));
        return new AdminUserPageResponse(
            ketQua.getContent().stream().map(this::chuyenThanhPhanHoi).toList(),
            ketQua.getNumber(), ketQua.getSize(), ketQua.getTotalElements(), ketQua.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse layChiTietNguoiDung(Long maNguoiDung) {
        if (maNguoiDung == null || maNguoiDung <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã người dùng không hợp lệ");
        }
        User nguoiDung = khoNguoiDung.findById(maNguoiDung)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        return chuyenThanhPhanHoi(nguoiDung);
    }

    private AdminUserResponse chuyenThanhPhanHoi(User nguoiDung) {
        return new AdminUserResponse(nguoiDung.getMaNguoiDung(), nguoiDung.getHoTen(),
            nguoiDung.getEmail(), nguoiDung.getVaiTro(),
            nguoiDung.isDangHoatDong() ? "ACTIVE" : "DISABLED",
            nguoiDung.getDiaChiVi(), nguoiDung.getNgayTao());
    }

    private String chuanHoaVaiTro(String vaiTro) {
        if (vaiTro == null || vaiTro.isBlank()) return null;
        String ketQua = vaiTro.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "DOCTOR", "PATIENT").contains(ketQua)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò lọc không hợp lệ");
        }
        return ketQua;
    }

    private Boolean chuanHoaTrangThai(String trangThai) {
        if (trangThai == null || trangThai.isBlank()) return null;
        return switch (trangThai.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "ENABLED", "TRUE" -> true;
            case "DISABLED", "INACTIVE", "FALSE" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lọc không hợp lệ");
        };
    }
}
