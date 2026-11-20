import { useEffect, useState } from 'react';
import { fmtTime, get, put } from '../api.js';
import { Badge, Card, Empty, Field, Msg, useAction, useLoad } from '../ui.jsx';

/** F-01: view / update profile, usage history. */
export default function ProfilePage({ session }) {
  const [profile, reload] = useLoad(() => get('/api/profile'));
  const [history] = useLoad(() => (session.role === 'USER' ? get('/api/profile/history?days=30') : Promise.resolve([])));
  const [f, setF] = useState({ fullName: '', phone: '' });
  const [run, busy, error, ok] = useAction();
  useEffect(() => { if (profile) setF({ fullName: profile.fullName, phone: profile.phone || '' }); }, [profile]);
  const save = () => run(async () => { await put('/api/profile', f); await reload(); }, 'Đã cập nhật hồ sơ');
  if (!profile) return null;
  return (
    <>
      <h2>Hồ sơ</h2>
      <Msg error={error} ok={ok} />
      <div className="cols">
        <Card title="Thông tin tài khoản">
          <div className="grid">
            <Field label="Email (không đổi được)"><input value={profile.email} disabled /></Field>
            {profile.studentId && <Field label="MSSV (không đổi được)"><input value={profile.studentId} disabled /></Field>}
            <Field label="Họ tên"><input value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
            <Field label="Số điện thoại"><input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
            <button onClick={save} disabled={busy}>Lưu</button>
          </div>
        </Card>
        <Card title="Đang hoạt động">
          {profile.vehicles?.map((v) => <div className="list-item" key={v.id}>Xe cá nhân <b>{v.plate}</b> · {v.brand}</div>)}
          {!profile.activeBookings?.length ? <Empty>Không có lượt đặt xe đang hoạt động</Empty> : profile.activeBookings.map((b) => (
            <div className="list-item" key={b.id}>Đặt xe #{b.id} · {b.vehicleCode} · {fmtTime(b.pickupTime)} <Badge value={b.status} /></div>))}
        </Card>
      </div>
      {session.role === 'USER' && (
        <Card title="Lịch sử sử dụng 30 ngày (UC-F01-07)">
          {!history?.length ? <Empty /> : (
            <table><thead><tr><th>#</th><th>Xe</th><th>Nhận</th><th>Kết thúc</th><th>Trạng thái</th></tr></thead>
              <tbody>{history.map((b) => <tr key={b.id}><td>{b.id}</td><td>{b.vehicleCode}</td><td>{fmtTime(b.pickupTime)}</td><td>{fmtTime(b.endedAt)}</td><td><Badge value={b.status} /></td></tr>)}</tbody></table>)}
        </Card>)}
    </>
  );
}
