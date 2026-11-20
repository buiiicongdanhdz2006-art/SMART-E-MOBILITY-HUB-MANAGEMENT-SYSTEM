import { useState } from 'react';
import { fmtClock, fmtTime, get, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Bar, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** F-06: ports, charging request, waiting queue, live charging status, stop / cancel, report broken port. */
export default function ChargingPage({ session }) {
  const isOperator = session.role === 'OPERATOR';
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [vehicles] = useLoad(() => (isOperator ? get('/api/fleet/vehicles?status=AVAILABLE') : get('/api/profile/vehicles')));
  const [requests, reloadRequests] = useLoad(() => get('/api/charging/requests/my'));
  const [hubId, setHubId] = useState(null);
  const [ports, setPorts] = useState([]);
  const [queue, setQueue] = useState([]);
  const [f, setF] = useState({ vehicle: '', area: '', batteryLevel: 25, targetBattery: 90, expectedDuration: 60, startTime: '', nextUsageTime: '' });
  const [fault, setFault] = useState({ portId: null, note: '' });
  const [run, busy, error, ok] = useAction();

  const loadHub = async (id) => {
    setHubId(id);
    if (!id) return;
    const [p, q] = await Promise.all([get(`/api/charging/hubs/${id}/ports`), get(`/api/charging/hubs/${id}/queue`)]);
    setPorts(p); setQueue(q);
  };
  useLive(['charging', 'port', 'schedule'], () => { reloadRequests(); if (hubId) loadHub(hubId); });

  const submit = () => run(async () => {
    const body = {
      hubId, area: f.area || null, expectedDuration: Number(f.expectedDuration), targetBattery: Number(f.targetBattery),
      startTime: f.startTime || null, nextUsageTime: f.nextUsageTime || null,
      batteryLevel: f.batteryLevel === '' ? null : Number(f.batteryLevel),
    };
    if (isOperator) body.vehicleId = Number(f.vehicle); else body.personalVehicleId = Number(f.vehicle);
    const r = await post('/api/charging/requests', body);
    await reloadRequests(); await loadHub(hubId);
    return r;
  }, 'Đã gửi yêu cầu sạc');
  const act = (r, action, text) => run(async () => { await post(`/api/charging/requests/${r.id}/${action}`); await reloadRequests(); if (hubId) await loadHub(hubId); }, text);
  const reportFault = () => run(async () => { await post(`/api/charging/ports/${fault.portId}/fault`, { note: fault.note }); setFault({ portId: null, note: '' }); await loadHub(hubId); }, 'Đã báo lỗi cổng sạc');

  const areas = [...new Set(ports.map((p) => p.area))];
  const active = (requests || []).filter((r) => ['WAITING', 'ASSIGNED', 'CHARGING'].includes(r.status));
  const past = (requests || []).filter((r) => !['WAITING', 'ASSIGNED', 'CHARGING'].includes(r.status));

  return (
    <>
      <h2>Sạc xe</h2>
      <Msg error={error} ok={ok} />
      {active.map((r) => (
        <Card key={r.id} title={`Yêu cầu #${r.id} · ${r.vehicleRef}`} actions={<><Badge value={r.status} /><Badge value={r.priority} text={'Ưu tiên ' + r.priority} />{r.urgent && <Badge value="CRITICAL" text="Khẩn cấp" />}</>}>
          <div className="grid g4">
            <div className="stat"><b>{r.hubName}</b><span>Trạm{r.area ? ' · khu ' + r.area : ''}</span></div>
            <div className="stat"><b>{r.portCode || '—'}</b><span>{r.status === 'ASSIGNED' ? 'Cắm sạc trước ' + fmtClock(r.plugInDeadline) : 'Cổng sạc'}</span></div>
            <div className="stat"><b>{r.queuePosition ?? '—'}</b><span>Vị trí hàng chờ</span></div>
            <div className="stat"><b>{r.session?.expectedEnd ? fmtClock(r.session.expectedEnd) : r.expectedDuration + ' phút'}</b><span>{r.session ? 'Dự kiến xong' : 'Thời gian sạc'}</span></div>
          </div>
          {r.status === 'CHARGING' && r.session && (
            <div style={{ marginTop: 12 }}>
              <Bar value={r.session.currentBattery ?? r.batteryLevel} />
              <div className="muted" style={{ marginTop: 4 }}>Pin {r.session.initialBattery}% → {r.session.currentBattery}% (mục tiêu {r.targetBattery}%) · cập nhật {fmtClock(r.session.lastReadingAt)}
                {r.session.dataInterrupted && <> · <Badge value="DELIVERY_FAILED" text="Mất dữ liệu cảm biến - đang hiển thị số liệu gần nhất" /></>}</div>
            </div>)}
          <div className="row" style={{ marginTop: 10 }}>
            {r.status === 'CHARGING' && <button onClick={() => act(r, 'stop', 'Đã dừng sạc')} disabled={busy}>Dừng sạc</button>}
            {['WAITING', 'ASSIGNED'].includes(r.status) && <button className="danger" onClick={() => act(r, 'cancel', 'Đã huỷ yêu cầu')} disabled={busy}>Huỷ yêu cầu</button>}
            {r.status === 'ASSIGNED' && <span className="muted">Khi xe được cắm vào cổng, cảm biến IoT sẽ tự bắt đầu phiên sạc.</span>}
          </div>
        </Card>))}
      <div className="cols">
        <Card title="Đăng ký sạc (UC-F06-02)">
          <div className="grid g2">
            <Field label="Trạm"><HubSelect hubs={hubs} value={hubId} onChange={loadHub} /></Field>
            <Field label="Khu vực"><select value={f.area} onChange={(e) => setF({ ...f, area: e.target.value })}><option value="">Bất kỳ</option>{areas.map((a) => <option key={a} value={a}>Khu {a}</option>)}</select></Field>
            <Field label={isOperator ? 'Xe dùng chung' : 'Xe của tôi'}><select value={f.vehicle} onChange={(e) => setF({ ...f, vehicle: e.target.value })}>
              <option value="">— Chọn xe —</option>
              {(vehicles || []).filter((v) => !isOperator || !hubId || v.hubId === hubId).map((v) => <option key={v.id} value={v.id}>{isOperator ? `${v.code} (${v.batteryLevel}%)` : `${v.plate} · ${v.brand}`}</option>)}</select></Field>
            <Field label="Pin hiện tại (%)"><input type="number" value={f.batteryLevel} onChange={(e) => setF({ ...f, batteryLevel: e.target.value })} placeholder={isOperator ? 'Lấy từ xe' : ''} /></Field>
            <Field label="Pin mục tiêu (%)"><input type="number" value={f.targetBattery} onChange={(e) => setF({ ...f, targetBattery: e.target.value })} /></Field>
            <Field label="Thời gian sạc dự kiến (phút)"><input type="number" value={f.expectedDuration} onChange={(e) => setF({ ...f, expectedDuration: e.target.value })} /></Field>
            <Field label="Bắt đầu (để trống = ngay bây giờ)"><input type="datetime-local" value={f.startTime} onChange={(e) => setF({ ...f, startTime: e.target.value })} /></Field>
            <Field label="Lần dùng xe kế tiếp (tuỳ chọn)"><input type="datetime-local" value={f.nextUsageTime} onChange={(e) => setF({ ...f, nextUsageTime: e.target.value })} /></Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}><button onClick={submit} disabled={busy || !hubId || !f.vehicle}>Gửi yêu cầu</button>
            <span className="muted">Hệ thống gán cổng trống hoặc đưa vào hàng chờ theo độ ưu tiên.</span></div>
        </Card>
        <Card title={hubId ? 'Cổng sạc của trạm (UC-F06-01)' : 'Chọn trạm để xem cổng sạc'}>
          <div className="ports">{ports.map((p) => (
            <div className="port" key={p.id}><b>{p.code}</b><span className="muted">Khu {p.area} · {p.powerKw} kW</span>
              <div className="row"><Badge value={p.reservedRequestId && p.status === 'AVAILABLE' ? 'ASSIGNED' : p.status} />
                {p.status !== 'FIXING' && <button className="small danger" onClick={() => setFault({ portId: p.id, note: '' })}>Báo lỗi</button>}</div>
              {p.lastTemperature != null && <div className="muted">{p.lastVoltage} V · {p.lastCurrent} A · {p.lastTemperature}°C</div>}
            </div>))}</div>
          {fault.portId && <div className="row" style={{ marginTop: 10 }}>
            <input style={{ flex: 1 }} placeholder="Mô tả lỗi (vd: không nhận sạc, đèn báo đỏ)" value={fault.note} onChange={(e) => setFault({ ...fault, note: e.target.value })} />
            <button className="danger" onClick={reportFault} disabled={busy}>Gửi báo lỗi</button><button className="ghost" onClick={() => setFault({ portId: null, note: '' })}>Đóng</button></div>}
          {hubId && <><h3 style={{ margin: '14px 0 8px' }}>Hàng chờ (UC-F06-03)</h3>
            {!queue.length ? <Empty>Không có yêu cầu chờ</Empty> : queue.map((r) => (
              <div className="list-item" key={r.id}>#{r.queuePosition} · Yêu cầu {r.id} · {r.vehicleRef} <Badge value={r.priority} />{r.urgent && <Badge value="CRITICAL" text="Khẩn cấp" />}</div>))}</>}
        </Card>
      </div>
      <Card title="Lịch sử sạc">
        {!past.length ? <Empty /> : (
          <table><thead><tr><th>#</th><th>Xe</th><th>Trạm / cổng</th><th>Pin</th><th>Tạo lúc</th><th>Trạng thái</th></tr></thead>
            <tbody>{past.map((r) => (
              <tr key={r.id}><td>{r.id}</td><td>{r.vehicleRef}</td><td>{r.hubName} {r.portCode && '· ' + r.portCode}</td>
                <td>{r.session ? `${r.session.initialBattery}% → ${r.session.currentBattery}%` : r.batteryLevel + '%'}</td><td>{fmtTime(r.createdAt)}</td><td><Badge value={r.status} /></td></tr>))}</tbody></table>)}
      </Card>
    </>
  );
}
