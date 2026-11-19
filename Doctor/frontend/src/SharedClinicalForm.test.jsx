import {beforeEach,it,expect,vi} from 'vitest';
import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
vi.mock('./api',()=>({api:{clinicalAccess:vi.fn(),clinicalCreate:vi.fn(),clinicalUpdate:vi.fn(),sharedRecord:vi.fn(),sharedIntegrity:vi.fn()}}));
import {api} from './api';
import SharedClinicalForm from './SharedClinicalForm';
beforeEach(()=>{vi.clearAllMocks();api.clinicalAccess.mockResolvedValue({canCreate:true});api.clinicalCreate.mockResolvedValue({id:90});});
function mount(path='/records/new?patientId=3'){render(<MemoryRouter initialEntries={[path]}><Routes><Route path="records/new" element={<SharedClinicalForm/>}/><Route path="shared-records/:id/edit" element={<SharedClinicalForm/>}/><Route path="shared-records/90" element={<h1>Đã lưu hồ sơ 90</h1>}/></Routes></MemoryRouter>);}
it('updates the expected version and keeps attachment references without resending response-only fields',async()=>{
  api.sharedRecord.mockResolvedValue({record:{id:90,patientId:3,patientCode:'PC-3',currentVersion:1,reason:'Fixture reason',visitAt:'2026-01-01T10:30:45'},version:{diagnosis:'Fixture diagnosis',medicines:[{sortOrder:1,medicineName:'Fixture A',quantityText:'2 tablets'}],attachments:[{id:8,fileName:'old.pdf'}]}});
  api.sharedIntegrity.mockResolvedValue({verified:true,testResults:'Fixture tests'});api.clinicalUpdate.mockResolvedValue({id:90});
  mount('/shared-records/90/edit');await screen.findByRole('button',{name:'Lưu phiên bản mới'});
  await userEvent.click(screen.getByRole('button',{name:'Lưu phiên bản mới'}));await screen.findByText('Đã lưu hồ sơ 90');
  expect(api.clinicalUpdate).toHaveBeenCalledWith('90',expect.objectContaining({expectedVersion:1,content:expect.objectContaining({keepAttachmentIds:[8],testResults:'Fixture tests'})}));
  expect(api.clinicalUpdate.mock.calls[0][1].content.medicines[0]).not.toHaveProperty('sortOrder');
});
it('sends structured medicines and navigates to public detail after save',async()=>{
  mount();await screen.findByRole('heading',{name:'D. Kê đơn thuốc'});
  await userEvent.type(screen.getByLabelText('Lý do khám'),'Fixture reason');await userEvent.type(screen.getByLabelText('Chẩn đoán chính'),'Fixture diagnosis');
  await userEvent.click(screen.getByRole('button',{name:'Thêm thuốc'}));await userEvent.click(screen.getByRole('button',{name:'Thêm thuốc'}));
  const names=screen.getAllByLabelText('Tên thuốc'),quantities=screen.getAllByLabelText('Số lượng và đơn vị');
  await userEvent.type(names[0],'Fixture A');await userEvent.type(quantities[0],'2 tablets');await userEvent.type(names[1],'Fixture B');await userEvent.type(quantities[1],'1 bottle');
  await userEvent.click(screen.getByRole('button',{name:'Lưu hồ sơ khám'}));await screen.findByText('Đã lưu hồ sơ 90');
  expect(api.clinicalCreate).toHaveBeenCalledWith(expect.objectContaining({patientId:3,medicines:[expect.objectContaining({medicineName:'Fixture A',quantityText:'2 tablets'}),expect.objectContaining({medicineName:'Fixture B',quantityText:'1 bottle'})]}));
  expect(api.clinicalCreate.mock.calls[0][0]).not.toHaveProperty('doctorId');
});
it('blocks the form when a changed patient URL has no current permission',async()=>{
  api.clinicalAccess.mockRejectedValue(new Error('Không có quyền'));mount();expect(await screen.findByRole('alert')).toHaveTextContent('Không có quyền');
  expect(screen.queryByRole('button',{name:'Lưu hồ sơ khám'})).not.toBeInTheDocument();expect(api.clinicalCreate).not.toHaveBeenCalled();
});
it('can retry a failed permission lookup without sending a medical write',async()=>{
  api.clinicalAccess.mockRejectedValueOnce(Object.assign(new Error('Tạm thời lỗi'),{status:500})).mockResolvedValueOnce({canCreate:false,reason:'SHARED_WRITE_MIGRATION_REQUIRED'});
  mount();expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500');
  await userEvent.click(screen.getByRole('button',{name:'Kiểm tra lại quyền tạo hồ sơ'}));
  await screen.findByRole('button',{name:'Lưu hồ sơ khám'});
  expect(screen.getByRole('button',{name:'Lưu hồ sơ khám'})).toBeDisabled();expect(api.clinicalCreate).not.toHaveBeenCalled();
});
it('shows migration dependency honestly and disables saving',async()=>{
  api.clinicalAccess.mockResolvedValue({canCreate:false,reason:'SHARED_WRITE_MIGRATION_REQUIRED'});mount();
  await screen.findByRole('alert');expect(screen.getByRole('button',{name:'Lưu hồ sơ khám'})).toBeDisabled();
});
it('removes only the chosen prescription row',async()=>{
  mount();await screen.findByRole('button',{name:'Thêm thuốc'});await userEvent.click(screen.getByRole('button',{name:'Thêm thuốc'}));
  await userEvent.click(screen.getByRole('button',{name:'Xóa thuốc 1'}));expect(screen.queryByLabelText('Tên thuốc')).not.toBeInTheDocument();
});
