import { expect, it, vi } from 'vitest';
import { apiSlot } from './apiQueue';
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};
it('allows two concurrent requests so a slow first request does not block the second',async()=>{
  const first=deferred(),second=deferred();const operation=vi.fn(()=>second.promise),third=vi.fn().mockResolvedValue('third');
  const a=apiSlot(undefined,()=>first.promise),b=apiSlot(undefined,operation),c=apiSlot(undefined,third);
  await Promise.resolve();await Promise.resolve();expect(operation).toHaveBeenCalledOnce();expect(third).not.toHaveBeenCalled();
  first.resolve('first');expect(await a).toBe('first');expect(await c).toBe('third');second.resolve('second');expect(await b).toBe('second');
});
it('does not send an aborted queued write and releases its reservation',async()=>{
  const first=deferred();const abort=new AbortController();const write=vi.fn();
  const a=apiSlot(undefined,()=>first.promise);const other=apiSlot(undefined,()=>first.promise);const b=apiSlot(abort.signal,write);
  const denied=expect(b).rejects.toMatchObject({name:'AbortError'});abort.abort();await denied;
  first.resolve();await a;await other;expect(write).not.toHaveBeenCalled();
  expect(await apiSlot(undefined,()=>Promise.resolve('after'))).toBe('after');
});
it('releases the queue after errors without retrying a write',async()=>{
  const write=vi.fn().mockRejectedValue(new Error('offline'));
  await expect(apiSlot(undefined,write)).rejects.toThrow('offline');
  expect(await apiSlot(undefined,()=>Promise.resolve('after'))).toBe('after');expect(write).toHaveBeenCalledOnce();
});
it('cancels an active request even when a mock transport ignores abort',async()=>{
  const abort=new AbortController();const never=deferred();
  const pending=apiSlot(abort.signal,()=>never.promise);
  const denied=expect(pending).rejects.toMatchObject({name:'AbortError'});abort.abort();await denied;
  expect(await apiSlot(undefined,()=>Promise.resolve('after'))).toBe('after');
});
