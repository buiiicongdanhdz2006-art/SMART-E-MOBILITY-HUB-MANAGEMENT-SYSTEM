import { useState } from 'react';
import { post } from '../api.js';
import { Card, Field, Msg, useAction } from '../ui.jsx';

/** F-01: sign in, register, reset password with OTP. */
export default function AuthPage({ onLogin }) {
  const [tab, setTab] = useState('login');
  const [run, busy, error, ok] = useAction();
  const [f, setF] = useState({ id: 'student1@hcmut.edu.vn', password: 'User@123' });
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const login = async (e) => { e.preventDefault(); const s = await run(() => post('/api/auth/login', { id: f.id, password: f.password })); if (s) onLogin(s); };
  const register = (e) => { e.preventDefault(); run(() => post('/api/auth/register', f), 'Đăng ký thành công, hãy đăng nhập.'); };
  const otp = () => run(() => post('/api/auth/otp', { email: f.email }), 'Đã gửi mã OTP (MVP: xem log của backend).');
  const reset = (e) => { e.preventDefault(); run(() => post('/api/auth/reset-password', { email: f.email, otp: f.otp, newPassword: f.newPassword }), 'Đổi mật khẩu thành công.'); };

  return (
    <div className="auth">
      <div className="brand"><h1>Smart E-Mobility Hub</h1><div className="muted">Quản lý xe điện, bãi đỗ và trạm sạc - Khu đô thị ĐHQG-HCM</div></div>
      <div className="tabs">
        {[['login', 'Đăng nhập'], ['register', 'Đăng ký'], ['reset', 'Quên mật khẩu']].map(([k, t]) => (
          <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{t}</button>
        ))}
      </div>
      <Card>
        <Msg error={error} ok={ok} />
        {tab === 'login' && (
          <form className="grid" onSubmit={login}>
            <Field label="Email hoặc MSSV"><input value={f.id || ''} onChange={set('id')} /></Field>
            <Field label="Mật khẩu"><input type="password" value={f.password || ''} onChange={set('password')} /></Field>
            <button disabled={busy}>Đăng nhập</button>
            <div className="muted">Tài khoản demo: student1@hcmut.edu.vn / User@123 · operator@emobility.vn / Operator@123 · admin@emobility.vn / Admin@123</div>
          </form>
        )}
        {tab === 'register' && (
          <form className="grid" onSubmit={register}>
            <Field label="Họ tên"><input value={f.name || ''} onChange={set('name')} /></Field>
            <Field label="Email"><input value={f.email || ''} onChange={set('email')} /></Field>
            <div className="grid g2">
              <Field label="MSSV"><input value={f.studentId || ''} onChange={set('studentId')} /></Field>
              <Field label="Số điện thoại"><input value={f.phone || ''} onChange={set('phone')} /></Field>
            </div>
            <Field label="Mật khẩu (≥ 6 ký tự)"><input type="password" value={f.password || ''} onChange={set('password')} /></Field>
            <button disabled={busy}>Đăng ký</button>
          </form>
        )}
        {tab === 'reset' && (
          <form className="grid" onSubmit={reset}>
            <Field label="Email"><div className="row"><input style={{ flex: 1 }} value={f.email || ''} onChange={set('email')} /><button type="button" className="ghost" onClick={otp} disabled={busy}>Gửi OTP</button></div></Field>
            <Field label="Mã OTP (6 số, hiệu lực 5 phút)"><input value={f.otp || ''} onChange={set('otp')} /></Field>
            <Field label="Mật khẩu mới"><input type="password" value={f.newPassword || ''} onChange={set('newPassword')} /></Field>
            <button disabled={busy}>Đặt lại mật khẩu</button>
          </form>
        )}
      </Card>
    </div>
  );
}
