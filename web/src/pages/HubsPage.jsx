import { useState } from 'react';
import { get } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Card, Empty, Msg, useAction, useLoad } from '../ui.jsx';

/** F-02: Hub list, Hub status, nearest Hubs (browser geolocation = LocationService). */
export default function HubsPage() {
  const [hubs, reload, error] = useLoad(() => get('/api/hubs'));
  const [selected, setSelected] = useState(null);
  const [status, setStatus] = useState(null);
  const [slots, setSlots] = useState([]);
  const [ports, setPorts] = useState([]);
  const [nearest, setNearest] = useState(null);
  const [need, setNeed] = useState('');
  const [run, busy, err] = useAction();

  useLive(['hub-status', 'port', 'charging'], () => { reload(); if (selected) open(selected); });

  const open = async (id) => {
    setSelected(id);
    const [st, sl, po] = await Promise.all([get(`/api/hubs/${id}/status`), get(`/api/parking/hubs/${id}/slots`), get(`/api/charging/hubs/${id}/ports`)]);
    setStatus(st); setSlots(sl); setPorts(po);
  };

  const findNearest = () => run(() => new Promise((resolve, reject) => {
    const query = async (lat, lng) => {
      try { setNearest(await get(`/api/hubs/nearest?lat=${lat}&lng=${lng}${need ? '&need=' + need : ''}`)); resolve(); } catch (e) { reject(e); }
    };
    if (!navigator.geolocation) return query(10.8700, 106.8030);
    navigator.geolocation.getCurrentPosition((p) => query(p.coords.latitude, p.coords.longitude),
      () => query(10.8700, 106.8030), { timeout: 4000 }); // fallback: centre of the campus
  }));

  return (
    <>
      <h2>Mobility Hub</h2>
      <Msg error={error || err} />
      <Card title="Tìm trạm gần nhất" actions={<>
        <select value={need} onChange={(e) => setNeed(e.target.value)}>
          <option value="">Mọi nhu cầu</option><option value="VEHICLE">Thuê xe</option><option value="PARKING">Đỗ xe</option><option value="CHARGING">Sạc xe</option>
        </select>
        <button onClick={findNearest} disabled={busy}>Dùng vị trí của tôi</button></>}>
        {nearest == null ? <div className="muted">Cho phép trình duyệt truy cập vị trí (nếu từ chối sẽ dùng vị trí trung tâm ĐHQG).</div>
          : nearest.length === 0 ? <Empty>Không có trạm phù hợp</Empty>
            : <div className="grid g3">{nearest.map((h) => (
              <div key={h.id} className="stat"><b>{h.distanceKm} km</b><span>{h.name}</span><div className="muted">{h.availableVehicles} xe · {h.freeSlots} chỗ · {h.freePorts} cổng</div></div>))}</div>}
      </Card>
      <div className="cols">
        <Card title="Danh sách trạm">
          <table>
            <thead><tr><th>Trạm</th><th>Xe</th><th>Chỗ đỗ</th><th>Cổng sạc</th><th>Tải</th></tr></thead>
            <tbody>{(hubs || []).map((h) => (
              <tr key={h.id} onClick={() => open(h.id)} style={{ cursor: 'pointer', background: selected === h.id ? '#f0f7f3' : undefined }}>
                <td><b>{h.code}</b> {h.name}<div className="muted">{h.address}</div></td>
                <td>{h.availableVehicles}</td><td>{h.freeSlots}</td><td>{h.freePorts}</td><td><Badge value={h.loadLevel} /></td>
              </tr>))}</tbody>
          </table>
        </Card>
        <Card title={status ? `Trạng thái: ${status.name}` : 'Chọn một trạm'}>
          {!status ? <Empty>Bấm vào một trạm để xem chi tiết</Empty> : (
            <>
              <div className="grid g3">
                <div className="stat"><b>{Math.round(status.utilisation * 100)}%</b><span>Mức sử dụng · <Badge value={status.loadLevel} /></span></div>
                <div className="stat"><b>{status.freeSlots}/{status.totalSlots}</b><span>Chỗ đỗ trống</span></div>
                <div className="stat"><b>{status.freePorts}/{status.totalPorts}</b><span>Cổng trống · {status.waitingRequests} chờ</span></div>
              </div>
              <h3 style={{ margin: '14px 0 8px' }}>Cổng sạc</h3>
              <div className="ports">{ports.map((p) => (
                <div className="port" key={p.id}><b>{p.code}</b><span className="muted">Khu {p.area} · {p.powerKw} kW</span><div><Badge value={p.status} /></div></div>))}</div>
              <h3 style={{ margin: '14px 0 8px' }}>Chỗ đỗ</h3>
              <div className="row">{slots.map((s) => <Badge key={s.id} value={s.status} text={s.code.split('-').pop()} />)}</div>
              <h3 style={{ margin: '14px 0 8px' }}>Xe theo trạng thái</h3>
              <div className="row">{Object.entries(status.vehiclesByStatus).filter(([, n]) => n > 0).map(([k, n]) => <Badge key={k} value={k} text={`${n} ${k}`} />)}</div>
            </>
          )}
        </Card>
      </div>
    </>
  );
}
