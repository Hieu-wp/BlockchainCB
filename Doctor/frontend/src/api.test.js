import { afterEach, expect, it, vi } from 'vitest';
import { api, request, setToken, requireAuthTransport } from './api';
import { walletError } from './blockchain/anchorApi';
afterEach(()=>{setToken(null);vi.unstubAllGlobals();});
it.each([
  ['https:', 'doctor.example.test', true],
  ['http:', 'localhost', true],
  ['http:', '127.0.0.1', true],
  ['http:', '[::1]', true],
  ['http:', '192.168.1.20', false],
  ['http:', 'localhost.example.test', false],
  ['file:', '', false],
])('checks authentication transport for %s//%s', (protocol, hostname, allowed) => {
  const check = () => requireAuthTransport({protocol, hostname});
  if (allowed) expect(check).not.toThrow();
  else expect(check).toThrow(/HTTPS/);
});
it.each(['/auth/doctor/login','/auth/doctor/register'])('does not send credentials to insecure remote %s', async path => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  vi.stubGlobal('window', {location:{protocol:'http:',hostname:'doctor.example.test'}});
  await expect(request(path,{method:'POST',body:{email:'fixture@example.test',password:'Test-only!123'}})).rejects.toThrow(/HTTPS/);
  expect(fetch).not.toHaveBeenCalled();
});
it('encodes query and attaches Bearer without persisting medical data',async()=>{
  const fetch=vi.fn().mockResolvedValue({ok:true,json:async()=>({content:[]})});vi.stubGlobal('fetch',fetch);setToken('test-token');
  await api.patients('Hà & Nam',2);expect(fetch.mock.calls[0][0]).toContain('q=H%C3%A0%20%26%20Nam');
  expect(fetch.mock.calls[0][1]).toMatchObject({headers:{Authorization:'Bearer test-token'},cache:'no-store',credentials:'omit'});
  expect(localStorage.length).toBe(0);
});
it('sends exact append DTO and does not send a doctorId',async()=>{
  const fetch=vi.fn().mockResolvedValue({ok:true,json:async()=>({})});vi.stubGlobal('fetch',fetch);
  const content={patientId:1,examDate:'2026-01-01',diagnosis:'demo',prescription:'',testResults:'',notes:'',attachmentIds:[]};
  await api.update(5,{expectedVersion:2,content});expect(fetch.mock.calls[0][0]).toBe('/api/doctor/records/5/versions');
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({expectedVersion:2,content});
});
it('expires authentication on 401',async()=>{
  const event=vi.fn();window.addEventListener('doctor:expired',event);setToken('expired');vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:401}));
  await expect(api.me()).rejects.toMatchObject({status:401});expect(event).toHaveBeenCalledOnce();window.removeEventListener('doctor:expired',event);
});
it('retains permission failure as 403 and uploads multipart without JSON header',async()=>{
  const fetch=vi.fn().mockResolvedValueOnce({ok:false,status:403}).mockResolvedValueOnce({ok:true,json:async()=>({id:9})});vi.stubGlobal('fetch',fetch);
  await expect(api.record(4)).rejects.toMatchObject({status:403});await api.upload(1,new File(['x'],'test.pdf',{type:'application/pdf'}));
  expect(fetch.mock.calls[1][1].body).toBeInstanceOf(FormData);expect(fetch.mock.calls[1][1].headers['Content-Type']).toBeUndefined();
});
it.each([
  ['/doctor/wallet/challenges', {code:'ACCOUNT_DISABLED',message:'Account disabled'}, 'ACCOUNT_DISABLED'],
  ['/doctor/wallet/verify', {code:'DOCTOR_BLOCKED',message:'Blocked'}, 'DOCTOR_BLOCKED'],
  ['/doctor/blockchain/config', {message:'Bac si chua duoc phe duyet'}, 'DOCTOR_APPROVAL_REQUIRED'],
  ['/doctor/wallet', {message:'Tai khoan khong duoc phep truy cap'}, 'ACCOUNT_RESTRICTED'],
])('keeps safe error code and exact request path for %s', async (path, body, code) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:false,status:403,json:async()=>body}));
  await expect(request(path,{method:'POST'})).rejects.toMatchObject({status:403,path,method:'POST',code});
});
it('does not expose an arbitrary server exception through wallet errors', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:false,status:500,json:async()=>({message:'PRIVATE_SERVER_DETAIL'})}));
  const error = await request('/doctor/wallet').catch(e => e);
  expect(error.message).toBe('Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại sau.');
  expect(walletError(error)).not.toContain('PRIVATE_SERVER_DETAIL');
});
it('posts patientCode only and keeps the source boundary error explicit',async()=>{
  const fetch=vi.fn().mockResolvedValueOnce({ok:true,json:async()=>({status:'PENDING'})}).mockResolvedValueOnce({ok:false,status:503,json:async()=>({code:'SHARED_RECORDS_NOT_READY'})});
  vi.stubGlobal('fetch',fetch);await api.requestAccess('PC-001');
  expect(fetch.mock.calls[0][0]).toBe('/api/doctor/access-grants/request');expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({patientCode:'PC-001'});
  await expect(api.record(1)).rejects.toMatchObject({code:'SHARED_RECORDS_NOT_READY',message:expect.stringContaining('Bệnh án chung chưa được tích hợp')});
});
it.each([
  ['SHARED_SCHEMA_UNVERIFIED','Backend chưa xác minh được cấu trúc dữ liệu Patient'],
  ['SHARED_METADATA_UNAVAILABLE','Backend không đọc được metadata database'],
  ['AUDIT_UNAVAILABLE','Nhật ký hoạt động tạm thời không khả dụng'],
])('shows the real %s cause on Dashboard and patient requests', async(code,message)=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:false,status:503,json:async()=>({code,message:code})}));
  await expect(api.integration()).rejects.toMatchObject({code,message:expect.stringContaining(message)});
  await expect(api.patients()).rejects.toMatchObject({code,message:expect.stringContaining(message)});
});
