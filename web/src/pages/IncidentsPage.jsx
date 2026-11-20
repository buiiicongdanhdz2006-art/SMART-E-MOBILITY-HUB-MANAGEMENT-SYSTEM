import { useState } from 'react';
import { fmtTime, get, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

const TYPES = ['VEHICLE_FAULT', 'PORT_FAULT', 'ACCIDENT', 'LOW_BATTERY', 'SENSOR_FAILURE', 'OVERHEAT', 'OTHER'];

/** F-09 Incident & maintenance management (UC-F09-01 .. 05). */
export default function IncidentsPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [vehicles] = useLoad(() => get('/api/vehicles'));
  const [filter, setFilter] = useState({ status: '', hubId: null, keyword: '', from: '', to: '' });
  const qs = () => new URLSearchParams(Object.entries(filter).filter(([, v]) => v)).toString();
  const [items, reload] = useLoad(() => get('/api/incidents?' + qs()), [JSON.stringify(filter)]);
  const [selected, setSelected] = useState(null);
  const [f, setF] = useState({ type: 'VEHICLE_FAULT', severity: 'MEDIUM', hubId: null, vehicleId: '', title: '', description: '' });
  const [text, setText] = useState('');
  const [run, busy, error, ok] = useAction();
  useLive(['notification'], reload, { filter: (m) => m.alertType === 'INCIDENT' });

  const open = async (i) => setSelected(await get(`/api/incidents/${i.id}`));
  const act = (action, body) => run(async () => { const r = await post(`/api/incidents/${selected.id}/${action}`, body); setSelected(r); setText(''); await reload(); }, 'Đã cập nhật sự cố');
  const report = () => run(async () => {
    const r = await post('/api/incidents', { ...f, vehicleId: f.vehicleId ? Number(f.vehicleId) : null });
    setF({ ...f, title: '', description: '' }); await reload(); setSelected(r);
  }, 'Đã báo cáo sự cố');

  return (
    <>
      <h2>Sự cố & bảo trì</h2>
      <Msg error={error} ok={ok} />
      <Card title="Lịch sử sự cố (UC-F09-05)" actions={<>
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })}><option value="">Mọi trạng thái</option>
          {['REPORTED', 'INVESTIGATING', 'MAINTENANCE', 'RESOLVED', 'INVALID_CLOSED'].map((s) => <option key={s}>{s}</option>)}</select>
        <HubSelect hubs={hubs} value={filter.hubId} allowEmpty emptyText="Mọi trạm" onChange={(v) => setFilter({ ...filter, hubId: v })} />
        <input type="date" value={filter.from} onChange={(e) => setFilter({ ...filter, from: e.target.value })} />
        <input type="date" value={filter.to} onChange={(e) => setFilter({ ...filter, to: e.target.value })} />
        <input placeholder="Từ khoá" value={filter.keyword} onChange={(e) => setFilter({ ...filter, keyword: e.target.value })} /></>}>
        {!items?.length ? <Empty /> : (
          <table><thead><tr><th>#</th><th>Sự cố</th><th>Đối tượng</th><th>Mức độ</th><th>Trạng thái</th><th>Thời gian</th></tr></thead>
            <tbody>{items.map((i) => (
              <tr key={i.id} onClick={() => open(i)} style={{ cursor: 'pointer', background: selected?.id === i.id ? '#f0f7f3' : undefined }}>
                <td>{i.id}</td><td><b>{i.title}</b><div className="muted">{i.type}{i.autoDetected ? ' · tự động' : ''}</div></td>
                <td>{[i.vehicleCode, i.portCode, i.hubName].filter(Boolean).join(' · ')}</td><td><Badge value={i.severity} /></td><td><Badge value={i.status} /></td><td>{fmtTime(i.createdAt)}</td></tr>))}</tbody></table>)}
      </Card>
      <div className="cols">
        <Card title={selected ? `Sự cố #${selected.id}: ${selected.title}` : 'Chi tiết sự cố (UC-F09-02)'}>
          {!selected ? <Empty>Chọn một sự cố</Empty> : (
            <>
              <div className="row"><Badge value={selected.status} /><Badge value={selected.severity} /><span className="muted">{selected.type}</span></div>
              <p>{selected.description}</p>
              <div className="row">
                {selected.status === 'REPORTED' && <button onClick={() => act('investigate')} disabled={busy}>Kiểm tra</button>}
                {['REPORTED', 'INVESTIGATING'].includes(selected.status) && <button onClick={() => act('maintenance')} disabled={busy}>Chuyển bảo trì</button>}
                {['MAINTENANCE', 'INVESTIGATING'].includes(selected.status) && <button onClick={() => act('resolve', { note: text })} disabled={busy}>Đã xử lý</button>}
                {['REPORTED', 'INVESTIGATING'].includes(selected.status) && <button className="danger" onClick={() => act('invalid', { reason: text })} disabled={busy}>Báo động giả</button>}
              </div>
              <input style={{ width: '100%', marginTop: 8 }} placeholder="Ghi chú / lý do" value={text} onChange={(e) => setText(e.target.value)} />
              <h3 style={{ margin: '14px 0 8px' }}>Lịch sử xử lý</h3>
              {selected.statusHistory.map((l) => <div className="list-item" key={l.id}><Badge value={l.toStatus} /> <span className="muted">{fmtTime(l.at)}</span> {l.note}</div>)}
            </>)}
        </Card>
        <Card title="Báo cáo sự cố (UC-F09-01)">
          <div className="grid g2">
            <Field label="Loại"><select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}>{TYPES.map((t) => <option key={t}>{t}</option>)}</select></Field>
            <Field label="Mức độ"><select value={f.severity} onChange={(e) => setF({ ...f, severity: e.target.value })}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((t) => <option key={t}>{t}</option>)}</select></Field>
            <Field label="Trạm"><HubSelect hubs={hubs} value={f.hubId} allowEmpty emptyText="—" onChange={(v) => setF({ ...f, hubId: v })} /></Field>
            <Field label="Xe"><select value={f.vehicleId} onChange={(e) => setF({ ...f, vehicleId: e.target.value })}><option value="">—</option>{(vehicles || []).map((v) => <option key={v.id} value={v.id}>{v.code}</option>)}</select></Field>
          </div>
          <div className="grid" style={{ marginTop: 8 }}>
            <Field label="Tiêu đề"><input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /></Field>
            <Field label="Mô tả"><textarea value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
            <button onClick={report} disabled={busy}>Báo cáo</button>
          </div>
        </Card>
      </div>
    </>
  );
}
