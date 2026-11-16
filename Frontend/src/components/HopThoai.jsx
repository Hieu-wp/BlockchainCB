import { useEffect, useRef, useId } from 'react'
import { X } from 'lucide-react'
import { DangTai, ThongBaoLoi } from './Shared'
import { useDuLieu } from '../hooks/useDuLieu'

export function HopThoai({ tieuDe, dong, dangGui = false, children }) {
  const thamChieu = useRef(null)
  const maTieuDe = useId()
  useEffect(() => {
    const hop = thamChieu.current
    hop.showModal()
    return () => hop.close()
  }, [])
  return <dialog ref={thamChieu} className="modal" aria-labelledby={maTieuDe}
    onCancel={suKien => { suKien.preventDefault(); if (!dangGui) dong() }}>
    <div className="modal-header"><h2 id={maTieuDe}>{tieuDe}</h2>
      <button type="button" className="icon-button" aria-label="Đóng hộp thoại" disabled={dangGui} onClick={dong}><X size={20}/></button>
    </div>{children}
  </dialog>
}

export function ChiTiet({ tieuDe, taiDuLieu, ma, truong, dong, children }) {
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(taiDuLieu, { ma })
  return <HopThoai tieuDe={tieuDe} dong={dong}>
    {dangTai ? <DangTai/> : loi ? <ThongBaoLoi loi={loi} onRetry={taiLai}/> : duLieu ?
      <dl className="detail-grid">{truong.map(([nhan, ten, dinhDang]) => <div key={ten}><dt>{nhan}</dt><dd>{dinhDang ? dinhDang(duLieu[ten]) : (duLieu[ten] ?? '—')}</dd></div>)}</dl> : <p>Không có thông tin chi tiết.</p>}
    {children}
  </HopThoai>
}
