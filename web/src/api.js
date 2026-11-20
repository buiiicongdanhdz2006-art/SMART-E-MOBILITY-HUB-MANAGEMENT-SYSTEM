// REST client of the backend. The session token is kept in localStorage (best effort).
const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* ignore */ } },
};

let session = (() => { try { return JSON.parse(store.get('emobility.session') || 'null'); } catch { return null; } })();

export function getSession() { return session; }
export function setSession(s) { session = s; store.set('emobility.session', s ? JSON.stringify(s) : null); }

export class ApiError extends Error {
  constructor(status, message, errors) { super(message); this.status = status; this.errors = errors || []; }
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (session?.token) headers.Authorization = 'Bearer ' + session.token;
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
  if (!res.ok) {
    if (res.status === 401 && session) setSession(null);
    throw new ApiError(res.status, data?.message || res.statusText, data?.errors);
  }
  return data;
}

export const get = (p) => api(p);
export const post = (p, body = {}) => api(p, { method: 'POST', body });
export const put = (p, body = {}) => api(p, { method: 'PUT', body });

export function errorText(e) {
  if (!e) return '';
  return [e.message, ...(e.errors || [])].filter(Boolean).join(' · ');
}

// ---- formatting helpers ------------------------------------------------------------------
export const fmtTime = (s) => (s ? new Date(s).toLocaleString('vi-VN', { hour12: false }) : '—');
export const fmtClock = (s) => (s ? new Date(s).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }) : '—');

/** Value for <input type="datetime-local"> at now + minutes. */
export function localInput(minutes = 0) {
  const d = new Date(Date.now() + minutes * 60000);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const LABELS = {
  AVAILABLE: 'Sẵn sàng', RESERVED: 'Đã đặt', IN_USE: 'Đang dùng', IN_TRANSIT: 'Đang điều phối', CHARGING: 'Đang sạc',
  MAINTENANCE: 'Bảo trì', OUT_OF_SERVICE: 'Ngưng hoạt động', OCCUPIED: 'Có xe', FIXING: 'Đang sửa',
  CREATED: 'Mới tạo', WAITING: 'Đang chờ', ASSIGNED: 'Đã gán cổng', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã huỷ', EXPIRED: 'Hết hạn',
  IN_PROGRESS: 'Đang xử lý', CHECKED_IN: 'Đã check-in', REPORTED: 'Mới báo', INVESTIGATING: 'Đang kiểm tra', RESOLVED: 'Đã xử lý',
  INVALID_CLOSED: 'Báo động giả', OPEN: 'Mở', DELIVERING: 'Đang gửi', DELIVERY_FAILED: 'Gửi lỗi', ESCALATED: 'Đã chuyển tiếp',
  PENDING: 'Chờ', NORMAL: 'Bình thường', WARNING: 'Cảnh báo', OVERLOADED: 'Quá tải', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp',
  CRITICAL: 'Nghiêm trọng', ACTIVE: 'Hoạt động', INACTIVE: 'Tạm ngưng', LOCKED: 'Bị khoá', SHORTAGE: 'Thiếu xe', SURPLUS: 'Dư xe',
  E_BIKE: 'Xe đạp điện', E_MOTORBIKE: 'Xe máy điện', E_CAR: 'Ô tô điện',
};
export const label = (v) => LABELS[v] || v;

const TONE = {
  AVAILABLE: 'ok', COMPLETED: 'ok', RESOLVED: 'ok', NORMAL: 'ok', ACTIVE: 'ok', IN_PROGRESS: 'info', LOW: 'ok',
  CHARGING: 'info', ASSIGNED: 'info', IN_USE: 'info', IN_TRANSIT: 'info', CHECKED_IN: 'info', DELIVERING: 'info', INVESTIGATING: 'info',
  RESERVED: 'warn', WAITING: 'warn', WARNING: 'warn', MEDIUM: 'warn', PENDING: 'warn', OPEN: 'warn', REPORTED: 'warn', OCCUPIED: 'warn', ESCALATED: 'warn',
  FIXING: 'bad', MAINTENANCE: 'bad', OVERLOADED: 'bad', HIGH: 'bad', CRITICAL: 'bad', EXPIRED: 'bad', CANCELLED: 'mute',
  DELIVERY_FAILED: 'bad', OUT_OF_SERVICE: 'bad', LOCKED: 'bad', SHORTAGE: 'bad', SURPLUS: 'info', INVALID_CLOSED: 'mute', INACTIVE: 'mute',
};
export const tone = (v) => TONE[v] || 'mute';
