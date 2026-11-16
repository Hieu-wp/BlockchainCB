import { useEffect, useState, useSyncExternalStore } from 'react'
import { taoQuanLyVi } from '../services/metamask'

export function useMetaMask() {
  const [quanLy] = useState(() => taoQuanLyVi(window, import.meta.env.VITE_BLOCKCHAIN_CHAIN_ID || ''))
  const state = useSyncExternalStore(quanLy.subscribe, quanLy.getSnapshot)
  useEffect(() => { quanLy.start(); return () => quanLy.stop() }, [quanLy])
  return { ...state, ketNoi: quanLy.ketNoi, ngatLienKet: quanLy.ngatLienKet }
}
