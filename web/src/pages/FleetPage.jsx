import { useState } from 'react';
import { fmtTime, get, post, put } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Bar, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

const STATUSES = ['AVAILABLE', 'RESERVED', 'IN_USE', 'IN_TRANSIT', 'CHARGING', 'MAINTENANCE', 'OUT_OF_SERVICE'];

/** F-08 Vehicle monitoring & dispatching (UC-F08-01 .. 05). */
export default function FleetPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [filter, setFilter] = useState({ hubId: null, type: '', status: '' });
  const qs = () => new URLSearchParams(Object.entries(filter).filter(([, v]) => v)).toString();
  const [stats, reloadStats] = useLoad(() => get('/api/fleet/statistics?' + qs()), [JSON.stringify(filter)]);
  const [vehicles, reloadVehicles] = useLoad(() => get('/api/fleet/vehicles?' + qs()), [JSON.stringify(filter)]);
  const [imbalances, reloadImb] = useLoad(() => get('/api/fleet/imbalances'));
  const [suggestions, reloadSug] = useLoad(() => get('/api/fleet/recommendations'));
  const [orders, reloadOrders] = useLoad(() => get('/api/fleet/dispatch-orders/active'));
  const [d, setD] = useState({ fromHubId: null, toHubId: null, quantity: 1, note: '' });
  const [inc, setInc] = useState({});
  const [run, busy, error, ok] = useAction();
  const reloadAll = () => { reloadStats(); reloadVehicles(); reloadImb(); reloadSug(); reloadOrders(); };
  useLive(['dispatch', 'vehicle'], reloadAll, { throttleMs: 4000 });

  const dispatch = (req) => run(async () => { await post('/api/fleet/dispatch-orders', req); reloadAll(); }, 'Đã tạo lệnh điều phối');
  const arrive = (o, v) => run(async () => { await post(`/api/fleet/dispatch-orders/${o.id}/arrivals/${v.id}`); reloadAll(); }, `Xe ${v.code} đã đến`);
  const refresh = (o) => run(async () => { await post(`/api/fleet/dispatch-orders/${o.id}/refresh`); reloadAll(); });
  const report = (o, v) => run(async () => { await post(`/api/fleet/dispatch-orders/${o.id}/incidents`, { vehicleId: String(v.id), type: 'VEHICLE_FAULT', description: inc[v.id] || 'Sự cố trên đường điều phối' }); reloadAll(); }, 'Đã ghi nhận sự cố');
  const correct = (v, status) => run(async () => { await put(`/api/fleet/vehicles/${v.id}/status`, { status }); reloadAll(); }, `Đã sửa trạng thái ${v.code}`);

  return (
    <>
      <h2>Giám sát & điều phối đội xe</h2>
      <Msg error={error} ok={ok} />
      <Card title="Bảng trạng thái đội xe (UC-F08-01)" actions={<>
        <HubSelect hubs={hubs} value={filter.hubId} allowEmpty emptyText="Mọi trạm" onChange={(v) => setFilter({ ...filter, hubId: v })} />
        <select value={filter.type} onChange={(e) => setFilter({ ...filter, type: e.target.value })}><option value="">Mọi loại</option><option>E_BIKE</option><option>E_MOTORBIKE</option><option>E_CAR</option></select>
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })}><option value="">Mọi trạng thái</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select></>}>
        {stats && <div className="row" style={{ marginBottom: 10 }}>
          <Badge value="ACTIVE" text={`${stats.total} xe`} />{Object.entries(stats.byStatus).filter(([, n]) => n).map(([k, n]) => <Badge key={k} value={k} text={`${n} ${k}`} />)}
          <span className="muted">Pin TB {stats.averageBattery}% · {stats.lowBattery} xe pin thấp</span></div>}
        <table><thead><tr><th>Xe</th><th>Trạm</th><th>Pin</th><th>Trạng thái</th><th>Sửa trạng thái</th></tr></thead>
          <tbody>{(vehicles || []).map((v) => (
            <tr key={v.id}><td><b>{v.code}</b> <span className="muted">{v.type}</span></td><td>{v.hubName || '—'}</td><td><Bar value={v.batteryLevel} /></td><td><Badge value={v.status} /></td>
              <td><select value="" onChange={(e) => e.target.value && correct(v, e.target.value)}><option value="">…</option>{STATUSES.filter((s) => s !== v.status).map((s) => <option key={s}>{s}</option>)}</select></td></tr>))}</tbody></table>
      </Card>
      <div className="cols">
        <Card title="Mất cân bằng (UC-F08-02)">
          {!imbalances?.length ? <Empty>Các trạm đang cân bằng</Empty> : imbalances.map((i) => (
            <div className="list-item" key={i.hubId}><div className="row"><b>{i.hubName}</b><Badge value={i.type} /><Badge value={i.severity} /></div>
              <div className="muted">{i.available} xe sẵn sàng / nhu cầu {i.demand} (chênh {i.gap > 0 ? '+' : ''}{i.gap})</div></div>))}
          <h3 style={{ margin: '14px 0 8px' }}>Gợi ý điều phối (UC-F08-03)</h3>
          {!suggestions?.length ? <Empty>Không có gợi ý</Empty> : suggestions.map((s, k) => (
            <div className="list-item" key={k}><div className="row"><b>{s.quantity} xe: {s.fromHubName} → {s.toHubName}</b><Badge value={s.severity} />
              <button className="small" disabled={busy} onClick={() => dispatch({ fromHubId: s.fromHubId, toHubId: s.toHubId, quantity: s.quantity, note: 'Áp dụng gợi ý: ' + s.reason })}>Áp dụng</button></div>
              <div className="muted">{s.reason}</div></div>))}
        </Card>
        <Card title="Tạo lệnh điều phối (UC-F08-04)">
          <div className="grid g2">
            <Field label="Từ trạm"><HubSelect hubs={hubs} value={d.fromHubId} onChange={(v) => setD({ ...d, fromHubId: v })} /></Field>
            <Field label="Đến trạm"><HubSelect hubs={hubs} value={d.toHubId} onChange={(v) => setD({ ...d, toHubId: v })} /></Field>
            <Field label="Số lượng xe"><input type="number" min="1" value={d.quantity} onChange={(e) => setD({ ...d, quantity: e.target.value })} /></Field>
            <Field label="Ghi chú"><input value={d.note} onChange={(e) => setD({ ...d, note: e.target.value })} /></Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}><button disabled={busy} onClick={() => dispatch({ ...d, quantity: Number(d.quantity) })}>Điều phối</button></div>
        </Card>
      </div>
      <Card title="Lệnh điều phối đang thực hiện (UC-F08-05)">
        {!orders?.length ? <Empty /> : orders.map((o) => (
          <div className="list-item" key={o.id}>
            <div className="row"><b>#{o.id} {o.fromHubName} → {o.toHubName}</b><Badge value={o.status} /><span className="muted">{fmtTime(o.startedAt)}</span>
              <div style={{ width: 160 }}><Bar value={o.progress * 100} /></div><button className="ghost small" onClick={() => refresh(o)}>Cập nhật vị trí</button></div>
            <table><tbody>{o.vehicles.map((v) => {
              const arrived = o.arrivedVehicleIds.includes(v.id); const flagged = o.flaggedVehicles[v.id] != null;
              return (<tr key={v.id}><td>{v.code}</td><td>{arrived ? <Badge value="COMPLETED" text="Đã đến" /> : flagged ? <Badge value="CRITICAL" text={'Sự cố #' + o.flaggedVehicles[v.id]} /> : <Badge value="IN_TRANSIT" />}</td>
                <td className="muted">{v.latitude?.toFixed(5)}, {v.longitude?.toFixed(5)}</td>
                <td className="row">{!arrived && !flagged && <><button className="small" onClick={() => arrive(o, v)} disabled={busy}>Xác nhận đến</button>
                  <input placeholder="Mô tả sự cố" value={inc[v.id] || ''} onChange={(e) => setInc({ ...inc, [v.id]: e.target.value })} />
                  <button className="small danger" onClick={() => report(o, v)} disabled={busy}>Báo sự cố</button></>}</td></tr>);
            })}</tbody></table>
          </div>))}
      </Card>
    </>
  );
}
