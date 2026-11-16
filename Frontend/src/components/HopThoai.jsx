import { useEffect, useRef, useId } from 'react'
import { X } from 'lucide-react'
import { DangTai, ThongBaoLoi } from './Shared'
import { useDuLieu } from '../hooks/useDuLieu'

export function HopThoai({ tieuDe, dong, dangGui = false, children, maMoTa, lopBoSung = '', tieuDiemBanDau }) {
  const thamChieu = useRef(null)
  const maTieuDe = useId()
  useEffect(() => {
    const hop = thamChieu.current
    const tieuDiemCu = document.activeElement
    hop.showModal()
    tieuDiemBanDau?.current?.focus()
    return () => {
      hop.close()
      if (tieuDiemCu instanceof HTMLElement && tieuDiemCu.isConnected) tieuDiemCu.focus()
    }
  }, [tieuDiemBanDau])
  return <dialog ref={thamChieu} className={`modal ${lopBoSung}`} aria-labelledby={maTieuDe} aria-describedby={maMoTa} aria-busy={dangGui}
    onCancel={suKien => { suKien.preventDefault(); suKien.stopPropagation(); if (!dangGui) dong() }}>
    <div className="modal-header"><h2 id={maTieuDe}>{tieuDe}</h2>
      <button type="button" className="icon-button" aria-label="Đóng hộp thoại" disabled={dangGui} onClick={dong}><X size={20}/></button>
    </div>{children}
  </dialog>
}

export function ChiTiet({ tieuDe, taiDuLieu, ma, truong, dong, children, noiDungDangTai }) {
  const { duLieu, loi, dangTai, taiLai } = useDuLieu(taiDuLieu, { ma })
  return <HopThoai tieuDe={tieuDe} dong={dong}>
    {dangTai ? <DangTai noiDung={noiDungDangTai}/> : loi ? <ThongBaoLoi loi={loi} onRetry={taiLai}/> : duLieu ?
      <dl className="detail-grid">{truong.map(([nhan, ten, dinhDang]) => <div key={ten}><dt>{nhan}</dt><dd>{dinhDang ? dinhDang(duLieu[ten]) : (duLieu[ten] ?? '—')}</dd></div>)}</dl> : <p>Không có thông tin chi tiết.</p>}
    {typeof children === 'function' ? children({ duLieu, loi, dangTai, taiLai }) : children}
  </HopThoai>
}
