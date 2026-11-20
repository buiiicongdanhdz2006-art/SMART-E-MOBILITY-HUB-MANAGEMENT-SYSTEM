import { fmtTime, get, post } from '../api.js';
import { useLive } from '../live.js';
import { Badge, Card, Empty, useLoad } from '../ui.jsx';

/** F-13 / F-11: in-app notifications, refreshed live through the WebSocket. */
export default function NotificationsPage({ session }) {
  const [items, reload] = useLoad(() => get('/api/notifications'));
  useLive(['notification'], (m) => {
    if (m.recipientId === session.accountId || (m.recipientId == null && m.recipientRole === session.role)) reload();
  });
  const read = async (n) => { await post(`/api/notifications/${n.id}/read`); reload(); };
  return (
    <>
      <h2>Thông báo</h2>
      <Card actions={<button className="ghost small" onClick={reload}>Làm mới</button>}>
        {!items?.length ? <Empty>Chưa có thông báo</Empty> : items.map((n) => (
          <div key={n.id} className={'list-item' + (n.read ? '' : ' unread')}>
            <div className="row"><b>{n.title}</b><Badge value={n.type} text={n.type} /><span className="muted">{fmtTime(n.createdAt)} · {n.channel}</span>
              {!n.read && <button className="ghost small" onClick={() => read(n)}>Đã đọc</button>}</div>
            <div>{n.message}</div>
          </div>))}
      </Card>
    </>
  );
}
