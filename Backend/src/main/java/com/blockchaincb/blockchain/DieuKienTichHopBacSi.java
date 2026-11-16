package com.blockchaincb.blockchain;

/**
 * Cổng mô tả phần tích hợp chưa sẵn sàng. Chưa phải adapter gửi giao dịch.
 * Khi có ABI/network/receipt và quy trình nhất quán đã thống nhất, bổ sung bộ điều phối riêng.
 */
public interface DieuKienTichHopBacSi {
    enum ThaoTac { PHE_DUYET, DINH_CHI, KICH_HOAT_LAI }

    String lyDoChuaHoTro(ThaoTac thaoTac);
}
