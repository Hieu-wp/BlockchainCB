import React from 'react';
import { act, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { useResource } from './useResource';
afterEach(() => vi.useRealTimers());
function View({loader,id=1}) {
  const state=useResource(loader,[id]);
  return <><p>{state.data||'empty'}</p>{state.warning&&<p role="status">{state.warning}</p>}{state.error&&<p role="alert">{state.error}</p>}</>;
}
it('retains verified content during a background read, then replaces it on success',async()=>{
  vi.useFakeTimers();let finish;
  const loader=vi.fn().mockResolvedValueOnce('first').mockImplementationOnce(()=>new Promise(r=>{finish=r;}));
  render(<View loader={loader}/>);await act(async()=>{});
  await act(async()=>{vi.advanceTimersByTime(30000);});
  expect(screen.getByText('first')).toBeInTheDocument();
  await act(async()=>{finish('updated');});expect(screen.getByText('updated')).toBeInTheDocument();
});
it('labels temporary failures and removes stale content after the bounded grace period',async()=>{
  vi.useFakeTimers();const loader=vi.fn().mockResolvedValueOnce('private content').mockRejectedValue(Object.assign(new Error('Busy'),{status:503}));
  render(<View loader={loader}/>);await act(async()=>{});
  await act(async()=>{vi.advanceTimersByTime(30000);});
  expect(screen.getByText('private content')).toBeInTheDocument();expect(screen.getByRole('status')).toHaveTextContent('Busy');
  await act(async()=>{vi.advanceTimersByTime(30001);});
  expect(screen.queryByText('private content')).not.toBeInTheDocument();expect(screen.getByRole('alert')).toBeInTheDocument();
});
it.each([401,403,404])('erases content immediately after HTTP %s',async status=>{
  vi.useFakeTimers();const loader=vi.fn().mockResolvedValueOnce('private content').mockRejectedValue(Object.assign(new Error('Denied'),{status}));
  render(<View loader={loader}/>);await act(async()=>{});
  await act(async()=>{window.dispatchEvent(new Event('focus'));});
  expect(screen.queryByText('private content')).not.toBeInTheDocument();expect(screen.getByRole('alert')).toHaveTextContent('Denied');
});
it('clears the previous patient when route identity changes',async()=>{
  const loader=vi.fn().mockResolvedValueOnce('patient one').mockImplementation(()=>new Promise(()=>{}));
  const view=render(<View loader={loader}/>);await screen.findByText('patient one');
  view.rerender(<View loader={loader} id={2}/>);expect(screen.queryByText('patient one')).not.toBeInTheDocument();
});
