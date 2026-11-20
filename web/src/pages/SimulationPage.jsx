import { useState } from 'react';
import { fmtTime, get, localInput, post } from '../api.js';
import { Badge, Card, Empty, Field, HubSelect, Msg, useAction, useLoad } from '../ui.jsx';

const PARAMS = {
  HOURS_SETTING: [['openHour', 'Giờ mở cửa (0-23)', 6], ['closeHour', 'Giờ đóng cửa (1-24)', 15]],
  CAPACITY_LIMIT: [['maxVehicles', 'Số xe tối đa', 5], ['maxPorts', 'Số cổng sạc tối đa', ''], ['maxSlots', 'Số chỗ đỗ tối đa', '']],
  HARDWARE_FAILURE: [['failedPorts', 'Số cổng hỏng', 2], ['failedVehicles', 'Số xe hỏng', 0]],
  CONGESTION_SETTING: [['demandMultiplier', 'Hệ số nhu cầu (0.1-5)', 1.8]],
};
const NAMES = { HOURS_SETTING: 'Thay đổi giờ hoạt động', CAPACITY_LIMIT: 'Giới hạn công suất', HARDWARE_FAILURE: 'Hỏng thiết bị', CONGESTION_SETTING: 'Tắc nghẽn / tăng nhu cầu' };

/** SimulationUI boundary of F-10 (UC-F10-01 .. 04). */
export default function SimulationPage() {
  const [hubs] = useLoad(() => get('/api/hubs'));
  const [history, reloadHistory] = useLoad(() => get('/api/simulation/scenarios'));
  const [type, setType] = useState('HARDWARE_FAILURE');
  const [hubId, setHubId] = useState(null);
  const [win, setWin] = useState({ windowStart: localInput(0), windowEnd: localInput(120) });
  const [values, setValues] = useState({});
  const [result, setResult] = useState(null);
  const [run, busy, error] = useAction();

  const val = (k, def) => (values[k] ?? def);
  const submit = () => run(async () => {
    const params = { type, hubId, ...win };
    PARAMS[type].forEach(([k, , def]) => { const v = val(k, def); if (v !== '' && v != null) params[k] = v; });
    setResult(await post('/api/simulation/scenarios', params));
    await reloadHistory();
  });
  const rerun = () => run(async () => {
    const s = await get('/api/simulation/rerun');
    setType(s.type); setHubId(s.hubId); setValues(s.parameters || {});
    if (s.window) setWin({ windowStart: s.window.start.slice(0, 16), windowEnd: s.window.end.slice(0, 16) });
  });

  const m = result?.metrics;
  return (
    <>
      <h2>Mô phỏng What-if</h2>
      <Msg error={error} />
      <Card title="Cấu hình kịch bản (UC-F10-01)" actions={<button className="ghost" onClick={rerun} disabled={busy}>Chạy lại kịch bản trước</button>}>
        <div className="grid g4">
          <Field label="Loại kịch bản"><select value={type} onChange={(e) => { setType(e.target.value); setValues({}); }}>{Object.keys(PARAMS).map((t) => <option key={t} value={t}>{NAMES[t]}</option>)}</select></Field>
          <Field label="Trạm"><HubSelect hubs={hubs} value={hubId} onChange={setHubId} /></Field>
          <Field label="Từ"><input type="datetime-local" value={win.windowStart} onChange={(e) => setWin({ ...win, windowStart: e.target.value })} /></Field>
          <Field label="Đến"><input type="datetime-local" value={win.windowEnd} onChange={(e) => setWin({ ...win, windowEnd: e.target.value })} /></Field>
          {PARAMS[type].map(([k, text, def]) => (
            <Field key={k} label={text}><input type="number" step="any" value={val(k, def)} onChange={(e) => setValues({ ...values, [k]: e.target.value })} /></Field>))}
        </div>
        <div className="row" style={{ marginTop: 10 }}><button onClick={submit} disabled={busy || !hubId}>Chạy mô phỏng (UC-F10-02)</button></div>
      </Card>
      {result && !result.successful && <div className="msg bad">{result.error}</div>}
      {result?.successful && (
        <div className="cols">
          <Card title="Đánh giá tác động (UC-F10-03)" actions={<Badge value={m.overallSeverity} text={'Mức độ ' + m.overallSeverity} />}>
            <table><thead><tr><th>Chỉ số</th><th>Hiện tại</th><th>Sau kịch bản</th></tr></thead>
              <tbody>
                <tr><td>Tỉ lệ xe đang dùng</td><td>{Math.round(m.utilizationBefore * 100)}%</td><td>{Math.round(m.utilizationAfter * 100)}%</td></tr>
                <tr><td>Tải cổng sạc</td><td>{Math.round(m.portLoadBefore * 100)}%</td><td>{Math.round(m.portLoadAfter * 100)}%</td></tr>
                <tr><td>Thời gian chờ sạc TB</td><td>{m.avgWaitMinutesBefore} phút</td><td>{m.avgWaitMinutesAfter} phút</td></tr>
                <tr><td>Thiếu xe giờ cao điểm</td><td>{m.vehicleShortageBefore} xe/giờ</td><td>{m.vehicleShortageAfter} xe/giờ</td></tr>
                <tr><td>Lượt sạc không đáp ứng</td><td>{m.unmetChargingBefore}/ngày</td><td>{m.unmetChargingAfter}/ngày</td></tr>
                <tr><td>Cổng hoạt động</td><td>{result.before.workingPorts}</td><td>{result.after.workingPorts}</td></tr>
                <tr><td>Xe sẵn sàng</td><td>{result.before.availableVehicles}</td><td>{result.after.availableVehicles}</td></tr>
              </tbody></table>
            <p className="muted">{result.summary}</p>
          </Card>
          <Card title="Đề xuất hành động (UC-F10-04)">
            {result.suggestions.map((s, k) => <div className="list-item" key={k}><div className="row"><b>{s.title}</b><Badge value={s.priority >= 80 ? 'HIGH' : s.priority >= 50 ? 'MEDIUM' : 'LOW'} text={'Ưu tiên ' + s.priority} /></div><div className="muted">{s.detail}</div></div>)}
            <p className="muted">Đề xuất chỉ mang tính tham khảo, hệ thống không tự thực hiện.</p>
          </Card>
        </div>)}
      <Card title="Kịch bản gần đây">
        {!history?.length ? <Empty /> : (
          <table><thead><tr><th>#</th><th>Loại</th><th>Trạm</th><th>Tham số</th><th>Kết quả</th><th>Lúc</th></tr></thead>
            <tbody>{history.map((s) => (
              <tr key={s.id}><td>{s.id}</td><td>{NAMES[s.type] || s.type}</td><td>{hubs?.find((h) => h.id === s.hubId)?.code}</td>
                <td className="muted">{Object.entries(s.parameters || {}).map(([k, v]) => `${k}=${v}`).join(', ')}</td>
                <td>{s.resultSeverity ? <Badge value={s.resultSeverity} /> : <span className="muted">{s.resultSummary}</span>}</td><td>{fmtTime(s.createdAt)}</td></tr>))}</tbody></table>)}
      </Card>
    </>
  );
}
