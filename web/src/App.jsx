import { useState } from 'react';
import { Navigate, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { getSession, post, setSession } from './api.js';
import AuthPage from './pages/AuthPage.jsx';
import HubsPage from './pages/HubsPage.jsx';
import BookingPage from './pages/BookingPage.jsx';
import ParkingPage from './pages/ParkingPage.jsx';
import ChargingPage from './pages/ChargingPage.jsx';
import NotificationsPage from './pages/NotificationsPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import ComplaintsPage from './pages/ComplaintsPage.jsx';
import OperatorDashboard from './pages/OperatorDashboard.jsx';
import SchedulePage from './pages/SchedulePage.jsx';
import FleetPage from './pages/FleetPage.jsx';
import IncidentsPage from './pages/IncidentsPage.jsx';
import SimulationPage from './pages/SimulationPage.jsx';
import MonitoringPage from './pages/MonitoringPage.jsx';
import AdminPage from './pages/AdminPage.jsx';

const MENUS = {
  USER: [
    ['/hubs', 'Trạm (F-02)'], ['/booking', 'Thuê xe (F-04)'], ['/parking', 'Đỗ xe (F-05)'], ['/charging', 'Sạc xe (F-06)'],
    ['/notifications', 'Thông báo (F-13)'], ['/complaints', 'Khiếu nại (F-11)'], ['/profile', 'Hồ sơ (F-01)'],
  ],
  OPERATOR: [
    ['/operator', 'Tổng quan (F-02)'], ['/schedule', 'Lịch sạc (F-07)'], ['/fleet', 'Đội xe (F-08)'], ['/incidents', 'Sự cố (F-09)'],
    ['/simulation', 'Mô phỏng (F-10)'], ['/monitoring', 'Giám sát (F-11)'], ['/notifications', 'Thông báo'], ['/profile', 'Hồ sơ'],
  ],
  ADMIN: [['/admin', 'Quản trị (F-03)'], ['/hubs', 'Trạm'], ['/profile', 'Hồ sơ']],
};
const HOME = { USER: '/hubs', OPERATOR: '/operator', ADMIN: '/admin' };

export default function App() {
  const [session, setS] = useState(getSession());
  const navigate = useNavigate();

  const onLogin = (s) => { setSession(s); setS(s); navigate(HOME[s.role] || '/'); };
  const logout = async () => {
    try { await post('/api/auth/logout'); } catch { /* session already gone */ }
    setSession(null); setS(null); navigate('/');
  };

  if (!session) {
    return <Routes><Route path="*" element={<AuthPage onLogin={onLogin} />} /></Routes>;
  }
  const menu = MENUS[session.role] || [];
  return (
    <div className="layout">
      <nav className="side">
        <div className="logo">Smart E-Mobility Hub<small>Khu đô thị ĐHQG-HCM</small></div>
        {menu.map(([to, text]) => <NavLink key={to} to={to}>{text}</NavLink>)}
        <div className="who">
          {session.fullName}<br />{session.email} · {session.role}
          <div style={{ marginTop: 8 }}><button className="ghost small" onClick={logout}>Đăng xuất</button></div>
        </div>
      </nav>
      <main className="main">
        <Routes>
          <Route path="/hubs" element={<HubsPage />} />
          <Route path="/booking" element={<BookingPage />} />
          <Route path="/parking" element={<ParkingPage />} />
          <Route path="/charging" element={<ChargingPage session={session} />} />
          <Route path="/notifications" element={<NotificationsPage session={session} />} />
          <Route path="/complaints" element={<ComplaintsPage />} />
          <Route path="/profile" element={<ProfilePage session={session} />} />
          <Route path="/operator" element={<OperatorDashboard />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/fleet" element={<FleetPage />} />
          <Route path="/incidents" element={<IncidentsPage />} />
          <Route path="/simulation" element={<SimulationPage />} />
          <Route path="/monitoring" element={<MonitoringPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to={HOME[session.role] || '/hubs'} replace />} />
        </Routes>
      </main>
    </div>
  );
}
