import { useState } from 'react';
import { fmtTime, get, post, put } from '../api.js';
import { Badge, Bar, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

/** Admin: accounts, Hubs and vehicles (F-03 Vehicle management, UC-F03-01 .. 03). */
export default function AdminPage() {
  const [tab, setTab] = useState('vehicles');
  const [accounts, reloadAccounts] = useLoad(() => get('/api/admin/accounts'));
  const [hubs, reloadHubs] = useLoad(() => get('/api/hubs'));
  const [type, setType] = useState('');
  const [vehicles, reloadVehicles] = useLoad(() => get('/api/vehicles' + (type ? '?type=' + type : '')), [type]);
  const [v, setV] = useState({ id: null, code: '', type: 'E_BIKE', model: '', hubId: null, batteryLevel: 100 });
  const [h, setH] = useState({ code: '', name: '', address: '', latitude: 10.875, longitude: 106.801, expectedDemand: 5, slots: 10, ports: 4 });
  const [op, setOp] = useState({ email: '', phone: '', fullName: '', password: '', permissions: 'ALL' });
  const [run, busy, error, ok] = useAction();

  const saveVehicle = () => run(async () => {
    const body = { code: v.code, type: v.type, model: v.model, hubId: v.hubId, batteryLevel: v.batteryLevel };
    if (v.id) await put(`/api/vehicles/${v.id}`, body); else await post('/api/vehicles', body);
    setV({ id: null, code: '', type: 'E_BIKE', model: '', hubId: null, batteryLevel: 100 }); await reloadVehicles();
  }, 'Đã lưu xe');
  const createHub = () => run(async () => { await post('/api/admin/hubs', h); await reloadHubs(); }, 'Đã tạo trạm');
  const hubState = (hub, state) => run(async () => { await put(`/api/admin/hubs/${hub.id}`, { state }); await reloadHubs(); }, 'Đã cập nhật trạm');
  const lock = (a, status) => run(async () => { await put(`/api/admin/accounts/${a.id}/status`, { status }); await reloadAccounts(); }, 'Đã cập nhật tài khoản');
  const createOp = () => run(async () => { await post('/api/admin/accounts/operators', op); await reloadAccounts(); }, 'Đã tạo Operator');

  return (
    <>
      <h2>Quản trị hệ thống</h2>
      <div className="tabs">{[['vehicles', 'Xe (F-03)'], ['hubs', 'Trạm'], ['accounts', 'Tài khoản']].map(([k, t]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{t}</button>)}</div>
      <Msg error={error} ok={ok} />
      {tab === 'vehicles' && <>
        <Card title={v.id ? `Cập nhật xe ${v.code}` : 'Thêm xe (UC-F03-03)'}>
          <div className="grid g4">
            <Field label="Mã xe"><input value={v.code} disabled={!!v.id} onChange={(e) => setV({ ...v, code: e.target.value })} /></Field>
            <Field label="Loại"><select value={v.type} onChange={(e) => setV({ ...v, type: e.target.value })}><option>E_BIKE</option><option>E_MOTORBIKE</option><option>E_CAR</option></select></Field>
            <Field label="Model"><input value={v.model} onChange={(e) => setV({ ...v, model: e.target.value })} /></Field>
            <Field label="Trạm"><HubSelect hubs={hubs} value={v.hubId} onChange={(x) => setV({ ...v, hubId: x })} /></Field>
            <Field label="Pin (%)"><input type="number" value={v.batteryLevel} onChange={(e) => setV({ ...v, batteryLevel: e.target.value })} /></Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}><button onClick={saveVehicle} disabled={busy}>Lưu</button>{v.id && <button className="ghost" onClick={() => setV({ id: null, code: '', type: 'E_BIKE', model: '', hubId: null, batteryLevel: 100 })}>Huỷ sửa</button>}</div>
        </Card>
        <Card title="Danh sách xe (UC-F03-01, 02)" actions={<select value={type} onChange={(e) => setType(e.target.value)}><option value="">Mọi loại</option><option>E_BIKE</option><option>E_MOTORBIKE</option><option>E_CAR</option></select>}>
          <table><thead><tr><th>Mã</th><th>Loại / model</th><th>Trạm</th><th>Pin</th><th>Trạng thái</th><th>Vị trí</th><th></th></tr></thead>
            <tbody>{(vehicles || []).map((x) => (
              <tr key={x.id}><td><b>{x.code}</b></td><td>{x.type}<div className="muted">{x.model}</div></td><td>{x.hubName || '—'}</td><td><Bar value={x.batteryLevel} /></td><td><Badge value={x.status} /></td>
                <td className="muted">{x.latitude?.toFixed(4)}, {x.longitude?.toFixed(4)}</td>
                <td><button className="small ghost" onClick={() => setV({ id: x.id, code: x.code, type: x.type, model: x.model || '', hubId: x.hubId, batteryLevel: x.batteryLevel })}>Sửa</button></td></tr>))}</tbody></table>
        </Card></>}
      {tab === 'hubs' && <>
        <Card title="Tạo trạm mới">
          <div className="grid g4">{[['code', 'Mã'], ['name', 'Tên'], ['address', 'Địa chỉ'], ['latitude', 'Vĩ độ'], ['longitude', 'Kinh độ'], ['expectedDemand', 'Nhu cầu xe'], ['slots', 'Số chỗ đỗ'], ['ports', 'Số cổng sạc']].map(([k, t]) => (
            <Field key={k} label={t}><input value={h[k]} onChange={(e) => setH({ ...h, [k]: e.target.value })} /></Field>))}</div>
          <div className="row" style={{ marginTop: 10 }}><button onClick={createHub} disabled={busy}>Tạo trạm</button></div>
        </Card>
        <Card title="Danh sách trạm">
          <table><thead><tr><th>Trạm</th><th>Trạng thái</th><th>Xe</th><th>Chỗ</th><th>Cổng</th><th></th></tr></thead>
            <tbody>{(hubs || []).map((x) => (
              <tr key={x.id}><td><b>{x.code}</b> {x.name}</td><td><Badge value={x.state} /></td><td>{x.availableVehicles}</td><td>{x.freeSlots}</td><td>{x.freePorts}</td>
                <td>{x.state === 'ACTIVE' ? <button className="small danger" onClick={() => hubState(x, 'INACTIVE')}>Tạm ngưng</button> : <button className="small" onClick={() => hubState(x, 'ACTIVE')}>Kích hoạt</button>}</td></tr>))}</tbody></table>
        </Card></>}
      {tab === 'accounts' && <>
        <Card title="Tạo tài khoản Operator">
          <div className="grid g4">{[['email', 'Email'], ['phone', 'SĐT'], ['fullName', 'Họ tên'], ['password', 'Mật khẩu'], ['permissions', 'Quyền (ALL hoặc danh sách)']].map(([k, t]) => (
            <Field key={k} label={t}><input value={op[k]} type={k === 'password' ? 'password' : 'text'} onChange={(e) => setOp({ ...op, [k]: e.target.value })} /></Field>))}</div>
          <div className="row" style={{ marginTop: 10 }}><button onClick={createOp} disabled={busy}>Tạo</button></div>
        </Card>
        <Card title="Tài khoản">
          {!accounts?.length ? <Empty /> : (
            <table><thead><tr><th>#</th><th>Tài khoản</th><th>Vai trò</th><th>Trạng thái</th><th>Tạo lúc</th><th></th></tr></thead>
              <tbody>{accounts.map((x) => (
                <tr key={x.id}><td>{x.id}</td><td><b>{x.fullName}</b><div className="muted">{x.email}{x.studentId ? ' · ' + x.studentId : ''}</div></td><td>{x.role}{x.permissions ? <div className="muted">{x.permissions}</div> : null}</td>
                  <td><Badge value={x.status} /></td><td>{fmtTime(x.createdAt)}</td>
                  <td>{x.role !== 'ADMIN' && (x.status === 'ACTIVE' ? <button className="small danger" onClick={() => lock(x, 'LOCKED')}>Khoá</button> : <button className="small" onClick={() => lock(x, 'ACTIVE')}>Mở khoá</button>)}</td></tr>))}</tbody></table>)}
        </Card></>}
    </>
  );
}
