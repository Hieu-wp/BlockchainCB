import { useEffect,useState } from 'react';
import { api } from '../api';
import { sharedAnchorApi } from './anchorApi';
import AnchorVersionButton from './AnchorVersionButton';
import { useSearchParams } from 'react-router-dom';
export default function SharedVersionAnchor({recordId,version,proof}){
  const [search,setSearch]=useSearchParams();
  const autoStart=Number(search.get('anchor'))===version.versionNumber;
  const [me,setMe]=useState(null);
  useEffect(()=>{let live=true;Promise.resolve().then(()=>api.me()).then(user=>{if(live)setMe(user);}).catch(()=>{});return()=>{live=false;};},[]);
  return <section className="panel" id="blockchain-confirmation"><h2>Xác nhận ghi Blockchain</h2>
    <p>Hồ sơ đã được lưu trong Supabase. Nhấn nút bên dưới để xác nhận bằng MetaMask. Từ chối ký không xóa hồ sơ.</p>
    <p>SHA-256: <code>{version.storedDataHash||'Chưa có hash'}</code></p>
    <AnchorVersionButton key={`${recordId}:${version.versionNumber}`} recordId={recordId} versionNumber={version.versionNumber}
      linkedWallet={me?.walletAddress} authorAllowed={me?.userId===version.createdByUserId} integrity={proof?.verified===true} api={sharedAnchorApi} autoStart={autoStart}
      onAutoStart={()=>{const next=new URLSearchParams(search);next.delete('anchor');setSearch(next,{replace:true});}}/>
  </section>;
}
