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

/** Calls handler(msg) for every live update whose type is in `types` (all types if empty). */
export function useLive(types, handler) {
  const ref = useRef(handler);
  ref.current = handler;
  useEffect(() => {
    connect();
    const fn = (msg) => { if (!types?.length || types.includes(msg.type)) ref.current(msg); };
    listeners.add(fn);
    return () => listeners.delete(fn);
  }, [JSON.stringify(types)]);
}
