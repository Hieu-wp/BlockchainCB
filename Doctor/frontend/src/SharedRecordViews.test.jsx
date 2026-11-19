import React from 'react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
vi.mock('./api',()=>({api:{sharedRecords:vi.fn(),sharedPatientRecords:vi.fn(),sharedRecord:vi.fn(),sharedVersions:vi.fn(),sharedVersion:vi.fn()}}));
import { api } from './api';
import { SharedRecordHistory, SharedRecordDetail } from './SharedRecordViews';
const summary={id:10,recordCode:'MR-PUBLIC',patientId:30,patientCode:'BN-2026001',patientName:'Granted patient',specialty:'Test specialty',visitAt:'2026-01-01T10:30',reason:'Visit',status:'COMPLETED',currentVersion:2};
const version={id:102,versionNumber:2,createdByUserId:70,createdAt:'2026-01-01T10:30',diagnosis:'PRIVATE_DIAGNOSIS_V2',medicines:[{medicineName:'Fixture medicine',quantityText:'1'}],vitals:null,attachments:[{id:1,fileName:'result.pdf',fileType:'PDF',fileUrl:'https://private.test/DO_NOT_EXPOSE'}],storedChainStatus:'CONFIRMED',storedDataHash:'0x'+'a'.repeat(64),hashVerification:'UNVERIFIED_PUBLIC_CANONICAL',downloadAvailable:false};
beforeEach(()=>{
  vi.clearAllMocks();api.sharedRecords.mockResolvedValue({content:[],totalElements:0,totalPages:0});
  api.sharedRecord.mockResolvedValue({record:summary,version});
  api.sharedVersions.mockResolvedValue({content:[{id:102,versionNumber:2},{id:101,versionNumber:1}],totalElements:2,totalPages:1});
  api.sharedVersion.mockResolvedValue({...version,id:101,versionNumber:1,diagnosis:'PRIVATE_DIAGNOSIS_V1',medicines:[],attachments:[]});
});
function detail(){return render(<MemoryRouter initialEntries={['/shared-records/10']}><Routes><Route path="shared-records/:id" element={<SharedRecordDetail/>}/></Routes></MemoryRouter>);}
it('shows a valid empty history instead of the source guard error',async()=>{
  render(<MemoryRouter><SharedRecordHistory/></MemoryRouter>);
  await screen.findByText('Chưa có hồ sơ chung phù hợp và còn quyền đọc.');expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
it('filters history by the actual patient code and keeps a separate record namespace',async()=>{
  api.sharedRecords.mockResolvedValue({content:[summary],totalElements:1,totalPages:1});
  render(<MemoryRouter><SharedRecordHistory/></MemoryRouter>);await screen.findByText('MR-PUBLIC');
  expect(screen.getByRole('link',{name:'MR-PUBLIC'})).toHaveAttribute('href','/shared-records/10');
  await userEvent.type(screen.getByLabelText('Lọc mã bệnh nhân chung'),'BN-2026001');await userEvent.click(screen.getByRole('button',{name:'Lọc'}));
  await screen.findByText('MR-PUBLIC');expect(api.sharedRecords).toHaveBeenLastCalledWith('BN-2026001',0,expect.any(AbortSignal));
});
it('shows stored hash as unverified, file names without URLs, and no write or anchor controls',async()=>{
  detail();await screen.findByText('PRIVATE_DIAGNOSIS_V2');await screen.findByText('Fixture medicine');
  expect(screen.getByText(/Hash và trạng thái này chưa được Java xác minh/)).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:/Ghi SHA/})).not.toBeInTheDocument();
  expect(document.body.textContent).not.toContain('DO_NOT_EXPOSE');expect(screen.getByText(/result.pdf/)).toBeInTheDocument();
});
it('loads the chosen immutable version rather than overwriting the current view',async()=>{
  detail();await screen.findByText('PRIVATE_DIAGNOSIS_V2');await userEvent.click(screen.getByRole('button',{name:/^Phiên bản 1/}));
  await screen.findByText('PRIVATE_DIAGNOSIS_V1');expect(screen.queryByText('PRIVATE_DIAGNOSIS_V2')).not.toBeInTheDocument();
  expect(api.sharedVersion).toHaveBeenCalledWith('10',1,expect.any(AbortSignal));
});
it('erases medical content when the next backend read reports revoked permission',async()=>{
  detail();await screen.findByText('PRIVATE_DIAGNOSIS_V2');api.sharedRecord.mockRejectedValue(new Error('Quyền đã thu hồi'));
  await act(async()=>{window.dispatchEvent(new Event('focus'));});
  expect(await screen.findByRole('alert')).toHaveTextContent('Quyền đã thu hồi');
  expect(screen.queryByText('PRIVATE_DIAGNOSIS_V2')).not.toBeInTheDocument();expect(screen.queryByText('Granted patient')).not.toBeInTheDocument();
});
