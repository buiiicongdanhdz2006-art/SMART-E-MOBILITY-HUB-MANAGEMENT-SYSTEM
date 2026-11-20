import { useState } from 'react';
import { fmtTime, get, localInput, post } from '../api.js';
import { Badge, Bar, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** F-04: search, book, pick up, return and cancel shared vehicles. */
export default function BookingPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [bookings, reloadBookings] = useLoad(() => get('/api/bookings/my'));
  const [q, setQ] = useState({ type: '', originHubId: null, destinationHubId: null, minBattery: 20 });
  const [times, setTimes] = useState({ pickupTime: localInput(10), returnTime: localInput(130) });
  const [results, setResults] = useState(null);
  const [returnHub, setReturnHub] = useState({});
  const [run, busy, error, ok] = useAction();

  const search = () => run(async () => {
    const p = new URLSearchParams();
    if (q.type) p.set('type', q.type);
    if (q.originHubId) p.set('originHubId', q.originHubId);
    if (q.destinationHubId) p.set('destinationHubId', q.destinationHubId);
    p.set('minBattery', q.minBattery);
    setResults(await get('/api/bookings/search?' + p));
  });
  const book = (v) => run(async () => {
    await post('/api/bookings', { vehicleId: String(v.id), ...times, destinationHubId: q.destinationHubId ? String(q.destinationHubId) : '' });
    await reloadBookings(); await search();
  }, `Đã đặt xe ${v.code}`);
  const pickup = (b) => run(async () => { await post(`/api/bookings/${b.id}/pickup`); await reloadBookings(); }, 'Đã nhận xe, chúc bạn đi đường an toàn');
  const cancel = (b) => run(async () => { await post(`/api/bookings/${b.id}/cancel`); await reloadBookings(); }, 'Đã huỷ lượt đặt');
  const giveBack = (b) => run(async () => {
    const rental = await get(`/api/bookings/${b.id}/rental`);
    await post(`/api/rentals/${rental.id}/return`, { hubId: returnHub[b.id] });
    await reloadBookings();
  }, 'Đã trả xe');

  return (
    <>
      <h2>Thuê xe điện dùng chung</h2>
      <Msg error={error} ok={ok} />
      <Card title="Tìm xe phù hợp (UC-F04-01)">
        <div className="grid g4">
          <Field label="Loại xe"><select value={q.type} onChange={(e) => setQ({ ...q, type: e.target.value })}>
            <option value="">Tất cả</option><option value="E_BIKE">Xe đạp điện</option><option value="E_MOTORBIKE">Xe máy điện</option><option value="E_CAR">Ô tô điện</option></select></Field>
          <Field label="Trạm nhận xe"><HubSelect hubs={hubs} value={q.originHubId} allowEmpty emptyText="Mọi trạm" onChange={(v) => setQ({ ...q, originHubId: v })} /></Field>
          <Field label="Trạm trả xe (dự kiến)"><HubSelect hubs={hubs} value={q.destinationHubId} allowEmpty emptyText="Chưa chọn" onChange={(v) => setQ({ ...q, destinationHubId: v })} /></Field>
          <Field label="Pin tối thiểu (%)"><input type="number" min="0" max="100" value={q.minBattery} onChange={(e) => setQ({ ...q, minBattery: e.target.value })} /></Field>
          <Field label="Giờ nhận"><input type="datetime-local" value={times.pickupTime} onChange={(e) => setTimes({ ...times, pickupTime: e.target.value })} /></Field>
          <Field label="Giờ trả"><input type="datetime-local" value={times.returnTime} onChange={(e) => setTimes({ ...times, returnTime: e.target.value })} /></Field>
          <Field label=" "><button onClick={search} disabled={busy}>Tìm xe</button></Field>
        </div>
        {results && (results.length === 0 ? <Empty>Không có xe phù hợp</Empty> : (
          <table style={{ marginTop: 12 }}>
            <thead><tr><th>Xe</th><th>Loại</th><th>Trạm</th><th>Pin</th><th></th></tr></thead>
            <tbody>{results.map((v) => (
              <tr key={v.id}><td><b>{v.code}</b> <span className="muted">{v.model}</span></td><td>{v.type}</td><td>{v.hubName}</td>
                <td><Bar value={v.batteryLevel} /></td><td><button className="small" onClick={() => book(v)} disabled={busy}>Đặt xe</button></td></tr>))}</tbody>
          </table>))}
      </Card>
      <Card title="Lượt đặt của tôi" actions={<button className="ghost small" onClick={reloadBookings}>Làm mới</button>}>
        {!bookings?.length ? <Empty /> : (
          <table>
            <thead><tr><th>#</th><th>Xe</th><th>Nhận</th><th>Trả</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody>{bookings.map((b) => (
              <tr key={b.id}>
                <td>{b.id}</td><td><b>{b.vehicleCode}</b><div className="muted">{b.pickupHubName}</div></td>
                <td>{fmtTime(b.pickupTime)}</td><td>{fmtTime(b.returnTime)}<div className="muted">{b.destinationHubName}</div></td>
                <td><Badge value={b.status} /></td>
                <td className="row">
                  {b.status === 'RESERVED' && <><button className="small" onClick={() => pickup(b)} disabled={busy}>Nhận xe</button>
                    <button className="small danger" onClick={() => cancel(b)} disabled={busy}>Huỷ</button></>}
                  {b.status === 'IN_PROGRESS' && <><HubSelect hubs={hubs} value={returnHub[b.id]} onChange={(v) => setReturnHub({ ...returnHub, [b.id]: v })} />
                    <button className="small" onClick={() => giveBack(b)} disabled={busy || !returnHub[b.id]}>Trả xe</button></>}
                </td>
              </tr>))}</tbody>
          </table>)}
      </Card>
    </>
  );
}
