package com.blockchaincb.blockchain;

import org.springframework.stereotype.Component;

/** Không có ABI/contract/network được xác nhận: tuyệt đối không gửi hoặc giả nhận receipt. */
@Component
public class BlockchainBacSiChuaTichHop implements DieuKienTichHopBacSi {
    @Override
    public String lyDoChuaHoTro(ThaoTac thaoTac) {
        return switch (thaoTac) {
            case PHE_DUYET -> "Chưa tích hợp xác nhận cấp quyền bác sĩ trên Blockchain";
            case DINH_CHI -> "Chưa thống nhất doctors.status và xác nhận thu hồi quyền Blockchain";
            case KICH_HOAT_LAI -> "Chưa thống nhất doctors.status và xác nhận khôi phục quyền Blockchain";
        };
    }
}
