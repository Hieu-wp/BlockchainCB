import { apiAdmin } from './api.js'

export function coTheTuChoi(bacSi) {
  return Number.isSafeInteger(bacSi?.maBacSi) && bacSi.maBacSi > 0 &&
    typeof bacSi.hoTen === 'string' && !!bacSi.hoTen.trim() && bacSi.trangThaiPheDuyet === 'PENDING'
}

export function loiLyDo(lyDo) {
  if (!lyDo.trim()) return 'Lý do không được để trống.'
  if (lyDo.length > 350) return 'Lý do không được vượt quá 350 ký tự.'
  return ''
}

export async function taiTrangBacSi(params) {
  let duLieu = await apiAdmin.bacSi(params)
  const trangHienTai = Math.min(params.trang, Math.max(0, duLieu.totalPages - 1))
  if (trangHienTai !== params.trang) duLieu = await apiAdmin.bacSi({ ...params, trang: trangHienTai })
  return { ...duLieu, trangHienTai }
}

export async function guiTuChoi(bacSi, lyDo) {
  if (!coTheTuChoi(bacSi)) throw Object.assign(new Error('Hồ sơ không còn đủ điều kiện từ chối.'), { status: 409 })
  const loi = loiLyDo(lyDo)
  if (loi) throw Object.assign(new Error(loi), { status: 400 })
  const ketQua = await apiAdmin.tuChoiBacSi(bacSi.maBacSi, lyDo.trim())
  if (ketQua?.maBacSi !== bacSi.maBacSi || ketQua?.trangThaiPheDuyet !== 'REJECTED') throw new Error('Phản hồi chưa xác nhận hồ sơ đã bị từ chối.')
  return ketQua
}
