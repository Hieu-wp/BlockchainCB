import { useEffect, useRef, useState } from 'react';

// Never retain content after permission/identity errors. Temporary transport failures
// may retain a recently verified view, labelled stale, for at most 60 seconds.
export function useResource(loader, deps = [], live = true) {
  const [state, setState] = useState({ data: null, error: '', warning: '', loading: true });
  const reloadRef = useRef(() => {});
  useEffect(() => {
    const controller = new AbortController();
    let pending = false, stopped = false, lastSuccess = 0, lastFocus = 0, expiry;
    setState({ data: null, error: '', warning: '', loading: true });
    async function load() {
      if (pending || stopped) return;
      pending = true;
      try {
        const data = await loader(controller.signal);
        if (!stopped) {
          lastSuccess = Date.now(); clearTimeout(expiry);
          setState({ data, error: '', warning: '', loading: false });
        }
      } catch (e) {
        if (!stopped && e.name !== 'AbortError') {
          const temporary = e.status === 0 || e.status === 429 || e.status >= 500;
          const remaining = 60000 - (Date.now() - lastSuccess);
          if (temporary && lastSuccess && remaining > 0) {
            setState(old => ({ ...old, loading: false, error: '', warning: 'Chưa cập nhật được dữ liệu. Đang hiển thị kết quả kiểm tra gần nhất. ' + e.message }));
            clearTimeout(expiry);
            expiry = setTimeout(() => { if (!stopped) setState({ data: null, loading: false, warning: '', error: 'Chưa xác minh lại được quyền và dữ liệu. Vui lòng thử lại.' }); }, remaining);
          } else {
            clearTimeout(expiry); lastSuccess = 0;
            setState({ data: null, loading: false, warning: '', error: e.message });
          }
        }
      } finally { pending = false; }
    }
    reloadRef.current = load;
    const focus = () => {
      if (!document.hidden && Date.now() - lastFocus > 1000) { lastFocus = Date.now(); load(); }
    };
    const timer = live ? setInterval(() => { if (!document.hidden) load(); }, 30000) : null;
    if (live) window.addEventListener('focus', focus);
    load();
    return () => { stopped = true; controller.abort(); clearInterval(timer); clearTimeout(expiry); window.removeEventListener('focus', focus); };
  }, [...deps, live]);
  return { ...state, reload: () => reloadRef.current() };
}
