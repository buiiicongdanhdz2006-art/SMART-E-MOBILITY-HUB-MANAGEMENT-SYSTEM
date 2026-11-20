import { useState } from 'react';
import { fmtTime, get, localInput, post } from '../api.js';
import { Badge, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** F-05 parking reservation + UC-F01-08 personal vehicle registration. */
export default function ParkingPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [vehicles, reloadVehicles] = useLoad(() => get('/api/profile/vehicles'));
  const [reservations, reloadRes] = useLoad(() => get('/api/parking/reservations/my'));
  const [hubId, setHubId] = useState(null);
  const [slots, setSlots] = useState(null);
  const [f, setF] = useState({ vehicleId: '', startTime: localInput(5), endTime: localInput(185) });
  const [nv, setNv] = useState({ type: 'E_MOTORBIKE', plate: '', brand: '' });
  const [run, busy, error, ok] = useAction();

  const chooseHub = async (id) => { setHubId(id); setSlots(id ? await get(`/api/parking/hubs/${id}/slots`) : null); };
  const reserve = () => run(async () => {
    const r = await post('/api/parking/reservations', { ...f, hubId: String(hubId) });
    await reloadRes(); await chooseHub(hubId); return r;
  }, 'Đặt chỗ thành công');
  const act = (r, action, text) => run(async () => { await post(`/api/parking/reservations/${r.id}/${action}`); await reloadRes(); if (hubId) await chooseHub(hubId); }, text);
  const addVehicle = () => run(async () => { await post('/api/profile/vehicles', nv); setNv({ ...nv, plate: '', brand: '' }); await reloadVehicles(); }, 'Đã đăng ký xe');

  return (
    <>
      <h2>Đỗ xe cá nhân</h2>
      <Msg error={error} ok={ok} />
      <div className="cols">
        <Card title="Đặt chỗ đỗ (UC-F05-02)">
          <div className="grid g2">
            <Field label="Trạm"><HubSelect hubs={hubs} value={hubId} onChange={chooseHub} /></Field>
            <Field label="Xe"><select value={f.vehicleId} onChange={(e) => setF({ ...f, vehicleId: e.target.value })}>
              <option value="">— Chọn xe —</option>{(vehicles || []).map((v) => <option key={v.id} value={v.id}>{v.plate} · {v.brand}</option>)}</select></Field>
            <Field label="Bắt đầu"><input type="datetime-local" value={f.startTime} onChange={(e) => setF({ ...f, startTime: e.target.value })} /></Field>
            <Field label="Kết thúc"><input type="datetime-local" value={f.endTime} onChange={(e) => setF({ ...f, endTime: e.target.value })} /></Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}><button onClick={reserve} disabled={busy || !hubId || !f.vehicleId}>Đặt chỗ</button></div>
          {slots && <><h3 style={{ margin: '14px 0 8px' }}>Tình trạng chỗ đỗ (UC-F05-01)</h3>
            <div className="row">{slots.map((s) => <Badge key={s.id} value={s.status} text={s.code} />)}</div></>}
        </Card>
        <Card title="Xe cá nhân của tôi (UC-F01-08)">
          {!vehicles?.length ? <Empty>Chưa đăng ký xe</Empty> : vehicles.map((v) => (
            <div className="list-item" key={v.id}><b>{v.plate}</b> · {v.brand} <span className="muted">{v.type}</span></div>))}
          <div className="grid g3" style={{ marginTop: 10 }}>
            <select value={nv.type} onChange={(e) => setNv({ ...nv, type: e.target.value })}>
              <option value="E_MOTORBIKE">Xe máy điện</option><option value="E_BIKE">Xe đạp điện</option><option value="E_CAR">Ô tô điện</option></select>
            <input placeholder="Biển số, vd 59X1-123.45" value={nv.plate} onChange={(e) => setNv({ ...nv, plate: e.target.value })} />
            <input placeholder="Hãng xe" value={nv.brand} onChange={(e) => setNv({ ...nv, brand: e.target.value })} />
          </div>
          <div className="row" style={{ marginTop: 8 }}><button className="ghost" onClick={addVehicle} disabled={busy}>Đăng ký xe</button></div>
        </Card>
      </div>
      <Card title="Đặt chỗ của tôi">
        {!reservations?.length ? <Empty /> : (
          <table>
            <thead><tr><th>#</th><th>Chỗ</th><th>Xe</th><th>Thời gian</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>{reservations.map((r) => (
              <tr key={r.id}><td>{r.id}</td><td><b>{r.slotCode}</b><div className="muted">{r.hubName}</div></td><td>{r.vehiclePlate}</td>
                <td>{fmtTime(r.startTime)} → {fmtTime(r.endTime)}</td><td><Badge value={r.status} /></td>
                <td className="row">
                  {r.status === 'RESERVED' && <><button className="small" onClick={() => act(r, 'check-in', 'Đã check-in')} disabled={busy}>Check-in</button>
                    <button className="small danger" onClick={() => act(r, 'cancel', 'Đã huỷ')} disabled={busy}>Huỷ</button></>}
                  {r.status === 'CHECKED_IN' && <button className="small" onClick={() => act(r, 'check-out', 'Đã check-out')} disabled={busy}>Check-out</button>}
                </td></tr>))}</tbody>
          </table>)}
      </Card>
    </>
  );
}
