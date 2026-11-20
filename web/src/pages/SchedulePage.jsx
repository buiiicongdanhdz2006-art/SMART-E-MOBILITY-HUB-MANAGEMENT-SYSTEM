import { useState } from 'react';
import { fmtClock, fmtTime, get, localInput, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Bar, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** F-07 Smart charging scheduling for the Operator (UC-F07-01 .. 03). */
export default function SchedulePage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [hubId, setHubId] = useState(null);
  const [ports, setPorts] = useState([]);
  const [queue, setQueue] = useState([]);
  const [active, setActive] = useState([]);
  const [schedule, setSchedule] = useState(null);
  const [params, setParams] = useState({ windowStart: localInput(0), windowEnd: localInput(8 * 60), lowBatteryThreshold: 20, nearUsageMinutes: 60, highDemandMinutes: 90 });
  const [reason, setReason] = useState({});
  const [run, busy, error, ok] = useAction();

  const load = async (id = hubId) => {
    if (!id) return;
    const [p, q, a] = await Promise.all([get(`/api/charging/hubs/${id}/ports`), get(`/api/charging/hubs/${id}/queue`), get(`/api/charging/hubs/${id}/active`)]);
    setPorts(p); setQueue(q); setActive(a);
    try { setSchedule(await get(`/api/schedule/hubs/${id}/latest`)); } catch { setSchedule(null); }
  };
  const choose = (id) => { setHubId(id); setSchedule(null); load(id); };
  useLive(['charging', 'port', 'schedule'], (m) => { if (!m.hubId || m.hubId === hubId) load(); });

  const generate = () => run(async () => {
    setSchedule(await post('/api/schedule/generate', { hubId, ...params, lowBatteryThreshold: Number(params.lowBatteryThreshold),
      nearUsageMinutes: Number(params.nearUsageMinutes), highDemandMinutes: Number(params.highDemandMinutes) }));
    await load();
  }, 'Đã tạo lịch sạc');
  const reschedule = () => run(async () => { setSchedule(await post(`/api/schedule/hubs/${hubId}/reschedule`)); await load(); }, 'Đã tính lại lịch');
  const urgent = (r) => run(async () => { setSchedule(await post(`/api/schedule/requests/${r.id}/urgent`, { reason: reason[r.id] || '' })); await load(); }, `Yêu cầu #${r.id} được ưu tiên`);
  const portStatus = (p, status) => run(async () => { await post(`/api/schedule/ports/${p.id}/status`, { status }); await load(); }, `Cổng ${p.code} → ${status}`);

  return (
    <>
      <h2>Lịch sạc thông minh</h2>
      <Msg error={error} ok={ok} />
      <Card title="Thiết lập lịch (UC-F07-01)" actions={<HubSelect hubs={hubs} value={hubId} onChange={choose} />}>
        <div className="grid g4">
          <Field label="Từ"><input type="datetime-local" value={params.windowStart} onChange={(e) => setParams({ ...params, windowStart: e.target.value })} /></Field>
          <Field label="Đến"><input type="datetime-local" value={params.windowEnd} onChange={(e) => setParams({ ...params, windowEnd: e.target.value })} /></Field>
          <Field label="Ngưỡng pin thấp (%)"><input type="number" value={params.lowBatteryThreshold} onChange={(e) => setParams({ ...params, lowBatteryThreshold: e.target.value })} /></Field>
          <Field label="Gần giờ dùng xe (phút)"><input type="number" value={params.nearUsageMinutes} onChange={(e) => setParams({ ...params, nearUsageMinutes: e.target.value })} /></Field>
          <Field label="Nhu cầu sạc cao (phút)"><input type="number" value={params.highDemandMinutes} onChange={(e) => setParams({ ...params, highDemandMinutes: e.target.value })} /></Field>
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <button onClick={generate} disabled={busy || !hubId}>Tạo lịch sạc</button>
          <button className="ghost" onClick={reschedule} disabled={busy || !hubId}>Tính lại lịch (UC-F07-03)</button>
          <span className="muted">Ưu tiên: khẩn cấp → gần giờ dùng xe → pin thấp → nhu cầu sạc cao. Không ngắt xe đang sạc.</span>
        </div>
      </Card>
      {hubId && <div className="cols">
        <Card title="Cổng sạc">
          <div className="ports">{ports.map((p) => (
            <div className="port" key={p.id}><b>{p.code}</b><span className="muted">Khu {p.area} · {p.powerKw} kW</span>
              <div><Badge value={p.reservedRequestId && p.status === 'AVAILABLE' ? 'ASSIGNED' : p.status} /></div>
              <div className="row" style={{ marginTop: 6 }}>
                {p.status === 'FIXING' ? <button className="small" onClick={() => portStatus(p, 'AVAILABLE')} disabled={busy}>Đã sửa xong</button>
                  : <button className="small danger" onClick={() => portStatus(p, 'FIXING')} disabled={busy}>Báo hỏng</button>}</div>
            </div>))}</div>
          <h3 style={{ margin: '14px 0 8px' }}>Đang sạc / đã gán</h3>
          {!active.length ? <Empty /> : active.map((r) => (
            <div className="list-item" key={r.id}><div className="row"><b>#{r.id} {r.vehicleRef}</b> · {r.portCode} <Badge value={r.status} />
              {r.session?.dataInterrupted && <Badge value="DELIVERY_FAILED" text="Mất dữ liệu" />}</div>
              {r.session && <Bar value={r.session.currentBattery} />}</div>))}
        </Card>
        <Card title="Hàng chờ ưu tiên (UC-F07-02)">
          {!queue.length ? <Empty>Không có yêu cầu chờ</Empty> : (
            <table><thead><tr><th>#</th><th>Yêu cầu</th><th>Pin</th><th>Dùng xe lúc</th><th>Ưu tiên</th><th>Priority override</th></tr></thead>
              <tbody>{queue.map((r) => (
                <tr key={r.id}><td>{r.queuePosition}</td><td>#{r.id} {r.vehicleRef}<div className="muted">{r.expectedDuration} phút{r.area ? ' · khu ' + r.area : ''}</div></td>
                  <td>{r.batteryLevel}%</td><td>{fmtClock(r.nextUsageTime)}</td><td><Badge value={r.priority} />{r.urgent && <Badge value="CRITICAL" text="Khẩn cấp" />}</td>
                  <td>{!r.urgent && <div className="row"><input placeholder="Lý do (bắt buộc)" value={reason[r.id] || ''} onChange={(e) => setReason({ ...reason, [r.id]: e.target.value })} />
                    <button className="small" onClick={() => urgent(r)} disabled={busy}>Khẩn cấp</button></div>}{r.urgent && <span className="muted">{r.urgentReason}</span>}</td></tr>))}</tbody></table>)}
        </Card>
      </div>}
      {schedule && <Card title={`Lịch sạc #${schedule.id} · ${schedule.hubName}`} actions={<>{schedule.overloaded && <Badge value="OVERLOADED" text="Trạm quá tải" />}<span className="muted">tạo lúc {fmtTime(schedule.createdAt)} · {schedule.note}</span></>}>
        {!schedule.assignments.length ? <Empty>Không có phân bổ</Empty> : (
          <table><thead><tr><th>Cổng</th><th>Yêu cầu</th><th>Bắt đầu</th><th>Kết thúc</th><th>Ưu tiên</th><th>Loại</th></tr></thead>
            <tbody>{schedule.assignments.map((a) => (
              <tr key={a.id}><td><b>{a.portCode}</b></td><td>#{a.requestId} {a.vehicleRef}</td><td>{fmtClock(a.plannedStart)}</td><td>{fmtClock(a.plannedEnd)}</td>
                <td><Badge value={a.priority} />{a.urgent && <Badge value="CRITICAL" text="Khẩn cấp" />}</td><td>{a.immediate ? <Badge value="ASSIGNED" text="Gán ngay" /> : <Badge value="WAITING" text="Dự kiến" />}</td></tr>))}</tbody></table>)}
      </Card>}
    </>
  );
}
