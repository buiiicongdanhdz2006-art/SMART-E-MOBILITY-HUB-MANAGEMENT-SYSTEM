import { useState } from 'react';
import { fmtTime, get, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

const CHANNELS = ['IN_APP', 'EMAIL', 'SMS', 'PUSH'];

/** MonitoringDashboard boundary of F-11: operational data, alerts, complaints, escalation, resolution. */
export default function MonitoringPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [channels] = useLoad(() => get('/api/monitoring/channels'));
  const [status, setStatus] = useState('');
  const [cases, reloadCases] = useLoad(() => get('/api/monitoring/cases' + (status ? '?status=' + status : '')), [status]);
  const [hubId, setHubId] = useState(null);
  const [state, setState] = useState(null);
  const [a, setA] = useState({ title: '', description: '', alertType: 'GENERAL', severity: 'MEDIUM', channel: 'IN_APP', recipientUserId: '', resource: '', hubId: null });
  const [note, setNote] = useState({});
  const [retryCh, setRetryCh] = useState({});
  const [run, busy, error, ok] = useAction();
  useLive(['notification'], () => reloadCases(), { filter: (m) => m.recipientRole === 'OPERATOR' });

  const review = async (id) => { setHubId(id); setState(id ? await get(`/api/monitoring/hubs/${id}`) : null); };
  const issue = () => run(async () => {
    await post('/api/monitoring/alerts', { ...a, hubId: a.hubId ? String(a.hubId) : '', recipientUserId: a.recipientUserId || '' });
    setA({ ...a, title: '', description: '' }); await reloadCases();
  }, 'Đã tạo và gửi cảnh báo');
  const caseAct = (c, path, body, text) => run(async () => { await post(`/api/monitoring/${path}`, body); setNote({ ...note, [c.id]: '' }); await reloadCases(); }, text);

  return (
    <>
      <h2>Giám sát & cảnh báo</h2>
      <Msg error={error} ok={ok} />
      <div className="cols">
        <Card title="Dữ liệu vận hành (F11-NF-01)" actions={<HubSelect hubs={hubs} value={hubId} onChange={review} />}>
          {!state ? <Empty>Chọn trạm</Empty> : (
            <div className="grid g3">
              <div className="stat"><b>{state.availableVehicles}/{state.totalVehicles}</b><span>Xe sẵn sàng</span></div>
              <div className="stat"><b>{state.freeSlots}/{state.totalSlots}</b><span>Chỗ đỗ trống</span></div>
              <div className="stat"><b>{state.workingPorts}/{state.totalPorts}</b><span>Cổng hoạt động</span></div>
              <div className="stat"><b>{state.chargingNow}</b><span>Đang sạc</span></div>
              <div className="stat"><b>{state.waitingRequests}</b><span>Yêu cầu chờ</span></div>
              <div className="stat"><b>{state.activeBookings}</b><span>Lượt đặt xe 1 giờ tới</span></div>
            </div>)}
          <h3 style={{ margin: '14px 0 8px' }}>Kênh gửi</h3>
          <div className="row">{channels && Object.entries(channels).map(([k, v]) => <Badge key={k} value={v ? 'AVAILABLE' : 'FIXING'} text={`${k}: ${v ? 'hoạt động' : 'lỗi'}`} />)}</div>
        </Card>
        <Card title="Tạo cảnh báo (F11-NF-03)">
          <div className="grid g2">
            <Field label="Tiêu đề"><input value={a.title} onChange={(e) => setA({ ...a, title: e.target.value })} /></Field>
            <Field label="Loại"><select value={a.alertType} onChange={(e) => setA({ ...a, alertType: e.target.value })}>
              {['GENERAL', 'CONGESTION', 'LOW_BATTERY', 'PORT_FAULT', 'HUB_OVERLOAD', 'MAINTENANCE'].map((t) => <option key={t}>{t}</option>)}</select></Field>
            <Field label="Mức độ"><select value={a.severity} onChange={(e) => setA({ ...a, severity: e.target.value })}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((t) => <option key={t}>{t}</option>)}</select></Field>
            <Field label="Kênh"><select value={a.channel} onChange={(e) => setA({ ...a, channel: e.target.value })}>{CHANNELS.map((t) => <option key={t}>{t}</option>)}</select></Field>
            <Field label="Trạm"><HubSelect hubs={hubs} value={a.hubId} allowEmpty emptyText="—" onChange={(v) => setA({ ...a, hubId: v })} /></Field>
            <Field label="ID người nhận (trống = tất cả)"><input value={a.recipientUserId} onChange={(e) => setA({ ...a, recipientUserId: e.target.value })} /></Field>
          </div>
          <Field label="Nội dung"><textarea value={a.description} onChange={(e) => setA({ ...a, description: e.target.value })} /></Field>
          <div className="row" style={{ marginTop: 8 }}><button onClick={issue} disabled={busy}>Gửi cảnh báo</button>
            <span className="muted">Kênh SMS bị tắt sẵn để minh hoạ gửi thất bại và gửi lại (F11-AF-01).</span></div>
        </Card>
      </div>
      <Card title="Cảnh báo & khiếu nại" actions={<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Mọi trạng thái</option>
        {['OPEN', 'DELIVERING', 'DELIVERY_FAILED', 'IN_PROGRESS', 'ESCALATED', 'RESOLVED'].map((s) => <option key={s}>{s}</option>)}</select>}>
        {!cases?.length ? <Empty /> : cases.map((c) => (
          <div className="list-item" key={c.id}>
            <div className="row"><b>#{c.id} {c.title}</b><Badge value={c.caseType === 'ALERT' ? 'CHARGING' : 'WARNING'} text={c.caseType === 'ALERT' ? 'Cảnh báo' : 'Khiếu nại'} />
              <Badge value={c.status} /><Badge value={c.severity} /><span className="muted">{fmtTime(c.createdAt)}{c.channel ? ' · ' + c.channel : ''}{c.ownerId ? ' · phụ trách #' + c.ownerId : ''}</span></div>
            <div>{c.description}</div>
            {c.failureReason && <div className="muted">Lỗi gửi: {c.failureReason}</div>}
            {c.actionLog?.length > 0 && <div className="muted">{c.actionLog.map((l, i) => <div key={i}>• {l}</div>)}</div>}
            {c.status !== 'RESOLVED' && (
              <div className="row" style={{ marginTop: 6 }}>
                {c.status === 'DELIVERY_FAILED' && <><select value={retryCh[c.id] || 'IN_APP'} onChange={(e) => setRetryCh({ ...retryCh, [c.id]: e.target.value })}>{CHANNELS.map((t) => <option key={t}>{t}</option>)}</select>
                  <button className="small" onClick={() => caseAct(c, `alerts/${c.id}/retry`, { channel: retryCh[c.id] || 'IN_APP' }, 'Đã gửi lại')} disabled={busy}>Gửi lại</button></>}
                {c.caseType === 'COMPLAINT' && c.status === 'OPEN' && <button className="small" onClick={() => caseAct(c, `complaints/${c.id}/investigate`, {}, 'Đã nhận xử lý')} disabled={busy}>Nhận xử lý</button>}
                <input placeholder="Ghi chú xử lý / kết quả" value={note[c.id] || ''} onChange={(e) => setNote({ ...note, [c.id]: e.target.value })} />
                <button className="small ghost" onClick={() => caseAct(c, `cases/${c.id}/actions`, { note: note[c.id] || '' }, 'Đã ghi nhận hành động')} disabled={busy}>Ghi hành động</button>
                <button className="small ghost" onClick={() => caseAct(c, `cases/${c.id}/escalate`, {}, 'Đã chuyển tiếp')} disabled={busy}>Chuyển tiếp</button>
                <button className="small" onClick={() => caseAct(c, `cases/${c.id}/resolve`, { summary: note[c.id] || '' }, 'Đã đóng trường hợp')} disabled={busy}>Đã giải quyết</button>
              </div>)}
          </div>))}
      </Card>
    </>
  );
}
