import { useEffect, useRef } from 'react';

// One shared WebSocket connection to /ws; listeners receive every live update {type, ...}.
const listeners = new Set();
let socket = null;
let retry = 1000;

function connect() {
  if (socket && (socket.readyState === 0 || socket.readyState === 1)) return;
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${proto}://${location.host}/ws`);
  socket.onopen = () => { retry = 1000; };
  socket.onmessage = (ev) => {
    let msg;
    try { msg = JSON.parse(ev.data); } catch { return; }
    listeners.forEach((fn) => { try { fn(msg); } catch { /* ignore */ } });
  };
  socket.onclose = () => { setTimeout(connect, retry); retry = Math.min(retry * 2, 15000); };
}

/**
 * Calls handler(msg) for live updates whose type is in `types` (all types if empty) and that pass `filter`.
 * Bursts are coalesced: the handler runs at most once per `throttleMs` (with the last message), because the
 * IoT simulator sends many messages per second and each handler usually reloads data from the API.
 */
export function useLive(types, handler, { filter, throttleMs = 1500 } = {}) {
  const ref = useRef({ handler, filter });
  ref.current = { handler, filter };
  useEffect(() => {
    connect();
    let timer = null;
    let last = null;
    const fire = () => { timer = null; const m = last; last = null; ref.current.handler(m); };
    const fn = (msg) => {
      if (types?.length && !types.includes(msg.type)) return;
      if (ref.current.filter && !ref.current.filter(msg)) return;
      last = msg;
      if (!timer) timer = setTimeout(fire, throttleMs);
    };
    listeners.add(fn);
    return () => { listeners.delete(fn); if (timer) clearTimeout(timer); };
  }, [JSON.stringify(types), throttleMs]);
}
