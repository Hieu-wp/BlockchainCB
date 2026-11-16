import { useEffect, useState } from 'react'

// Mỗi bộ lọc có một kết quả riêng; bỏ qua phản hồi cũ khi chuyển trang.
export function useDuLieu(taiDuLieu, thamSo = {}) {
  const [lan, setLan] = useState(0)
  const [ketQua, setKetQua] = useState(null)
  const chuoiThamSo = JSON.stringify(thamSo)
  const khoa = `${chuoiThamSo}:${lan}`
  useEffect(() => {
    let conHieuLuc = true
    taiDuLieu(JSON.parse(chuoiThamSo))
      .then(duLieu => { if (conHieuLuc) setKetQua({ khoa, taiDuLieu, duLieu }) })
      .catch(loi => { if (conHieuLuc) setKetQua({ khoa, taiDuLieu, loi }) })
    return () => { conHieuLuc = false }
  }, [taiDuLieu, chuoiThamSo, khoa])
  const hienTai = ketQua?.khoa === khoa && ketQua?.taiDuLieu === taiDuLieu ? ketQua : null
  return {
    duLieu: hienTai?.duLieu,
    loi: hienTai?.loi,
    dangTai: hienTai === null,
    taiLai: () => setLan(giaTri => giaTri + 1),
  }
}
