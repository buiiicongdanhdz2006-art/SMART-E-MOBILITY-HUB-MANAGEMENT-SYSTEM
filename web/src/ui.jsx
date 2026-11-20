import { useCallback, useEffect, useState } from 'react';
import { errorText, label, tone } from './api.js';

export function Badge({ value, text }) {
  return <span className={`badge ${tone(value)}`}>{text ?? label(value)}</span>;
}

export function Card({ title, actions, children }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-head"><h3>{title}</h3><div className="row">{actions}</div></div>
      )}
      {children}
    </section>
  );
}

export function Msg({ error, ok }) {
  if (error) return <div className="msg bad">{errorText(error)}</div>;
  if (ok) return <div className="msg ok">{ok}</div>;
  return null;
}

export function Field({ label: text, children }) {
  return <label className="field"><span>{text}</span>{children}</label>;
}

export function Empty({ children = 'Chưa có dữ liệu' }) {
  return <div className="empty">{children}</div>;
}

export function Bar({ value, max = 100 }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  const cls = pct < 20 ? 'bad' : pct < 50 ? 'warn' : 'ok';
  return <div className="bar"><i className={cls} style={{ width: pct + '%' }} /><span>{Math.round(value)}%</span></div>;
}

/** Loads data with `loader()`; returns [data, reload, error, loading]. */
export function useLoad(loader, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    try { setData(await loader()); setError(null); } catch (e) { setError(e); } finally { setLoading(false); }
  }, deps);
  useEffect(() => { reload(); }, [reload]);
  return [data, reload, error, loading];
}

/** Wraps an async action: returns [run, busy, error, ok, setOk]. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [ok, setOk] = useState(null);
  const run = async (fn, okText) => {
    setBusy(true); setError(null); setOk(null);
    try { const r = await fn(); if (okText) setOk(okText); return r; } catch (e) { setError(e); return undefined; } finally { setBusy(false); }
  };
  return [run, busy, error, ok, setOk];
}

export function HubSelect({ hubs, value, onChange, allowEmpty = false, emptyText = '— Chọn trạm —' }) {
  return (
    <select value={value ?? ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
      {(allowEmpty || value == null) && <option value="">{emptyText}</option>}
      {(hubs || []).map((h) => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
    </select>
  );
}
