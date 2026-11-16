export const dinhDangNgay = giaTri => {
  if (!giaTri) return '—'
  const ngay = new Date(giaTri)
  return Number.isNaN(ngay.getTime()) ? giaTri : ngay.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
}
