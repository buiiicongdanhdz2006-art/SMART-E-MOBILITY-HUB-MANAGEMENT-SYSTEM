import { useState } from 'react';
import { fmtTime, get, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Card, Empty, Msg, useAction, useLoad } from '../ui.jsx';

/** Operator overview: load of every Hub (F-02) and overload alerts (UC-F02-04). */
export default function OperatorDashboard() {
  const [hubs, reloadHubs] = useLoad(() => get('/api/hubs'));
  const [alerts, reloadAlerts] = useLoad(() => get('/api/alerts/overload'));
  const [stats, reloadStats] = useLoad(() => get('/api/fleet/statistics'));
  const [note, setNote] = useState({});
  const [run, busy, error, ok] = useAction();
  useLive(['hub-status', 'charging', 'dispatch', 'notification'], () => { reloadHubs(); reloadAlerts(); reloadStats(); });
  const resolve = (a) => run(async () => { await post(`/api/alerts/overload/${a.id}/resolve`, { note: note[a.id] || '' }); await reloadAlerts(); }, 'Đã xử lý cảnh báo');

  return (
    <>
      <h2>Tổng quan vận hành</h2>
      <Msg error={error} ok={ok} />
      {stats && <div className="grid g4" style={{ marginBottom: 16 }}>
        <div className="stat"><b>{stats.total}</b><span>Tổng số xe</span></div>
        <div className="stat"><b>{stats.byStatus.AVAILABLE}</b><span>Sẵn sàng</span></div>
        <div className="stat"><b>{stats.byStatus.IN_USE + stats.byStatus.RESERVED}</b><span>Đang thuê / đã đặt</span></div>
        <div className="stat"><b>{stats.lowBattery}</b><span>Pin dưới 20% · TB {stats.averageBattery}%</span></div>
      </div>}
      <div className="cols">
        <Card title="Tải các trạm">
          <table><thead><tr><th>Trạm</th><th>Xe</th><th>Chỗ</th><th>Cổng</th><th>Mức tải</th></tr></thead>
            <tbody>{(hubs || []).map((h) => (
              <tr key={h.id}><td><b>{h.code}</b> {h.name}</td><td>{h.availableVehicles}</td><td>{h.freeSlots}</td><td>{h.freePorts}</td><td><Badge value={h.loadLevel} /></td></tr>))}</tbody></table>
        </Card>
        <Card title="Cảnh báo quá tải đang mở">
          {!alerts?.length ? <Empty>Không có cảnh báo</Empty> : alerts.map((a) => (
            <div className="list-item" key={a.id}>
              <div className="row"><b>{a.hubName}</b><Badge value={a.level} /><span className="muted">{fmtTime(a.createdAt)}</span></div>
              <div>{a.message}</div>
              <div className="row" style={{ marginTop: 6 }}><input placeholder="Ghi chú xử lý" value={note[a.id] || ''} onChange={(e) => setNote({ ...note, [a.id]: e.target.value })} />
                <button className="small" onClick={() => resolve(a)} disabled={busy}>Đánh dấu đã xử lý</button></div>
            </div>))}
        </Card>
      </div>
    </>
  );
}
