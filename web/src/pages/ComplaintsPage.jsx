import { useState } from 'react';
import { fmtTime, get, post } from '../api.js';
import { Badge, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** F-11 User.reportIssue(): student reports a problem and follows its processing. */
export default function ComplaintsPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [items, reload] = useLoad(() => get('/api/complaints/my'));
  const [f, setF] = useState({ title: '', description: '', hubId: null, severity: 'MEDIUM' });
  const [run, busy, error, ok] = useAction();
  const send = () => run(async () => {
    await post('/api/complaints', { ...f, hubId: f.hubId ? String(f.hubId) : '' });
    setF({ title: '', description: '', hubId: null, severity: 'MEDIUM' }); await reload();
  }, 'Đã gửi khiếu nại');
  return (
    <>
      <h2>Khiếu nại / phản ánh</h2>
      <Msg error={error} ok={ok} />
      <div className="cols">
        <Card title="Gửi phản ánh">
          <div className="grid">
            <Field label="Tiêu đề"><input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} /></Field>
            <Field label="Mô tả"><textarea value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
            <div className="grid g2">
              <Field label="Trạm liên quan"><HubSelect hubs={hubs} value={f.hubId} allowEmpty emptyText="Không" onChange={(v) => setF({ ...f, hubId: v })} /></Field>
              <Field label="Mức độ"><select value={f.severity} onChange={(e) => setF({ ...f, severity: e.target.value })}>
                {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <option key={s}>{s}</option>)}</select></Field>
            </div>
            <button onClick={send} disabled={busy}>Gửi</button>
          </div>
        </Card>
        <Card title="Phản ánh của tôi">
          {!items?.length ? <Empty /> : items.map((c) => (
            <div className="list-item" key={c.id}>
              <div className="row"><b>#{c.id} {c.title}</b><Badge value={c.status} /><span className="muted">{fmtTime(c.createdAt)}</span></div>
              <div className="muted">{c.description}</div>
              {c.resolutionSummary && <div>Kết quả: {c.resolutionSummary}</div>}
            </div>))}
        </Card>
      </div>
    </>
  );
}
