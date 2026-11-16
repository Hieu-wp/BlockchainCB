import { afterEach, expect, it, vi } from 'vitest';
import { api, setToken } from './api';
afterEach(()=>{setToken(null);vi.unstubAllGlobals();});
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
