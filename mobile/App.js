import React, { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Pressable, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import { API_URL } from './config';

/**
 * Mobile client for the User (emobility-mobile.apk in the deployment diagram):
 * sign in (F-01), Hub list (F-02), my bookings (F-04) and live charging status (F-06), notifications (F-13).
 * It calls the same REST API as the web client.
 */
let token = null;

async function api(path, method = 'GET', body) {
  const res = await fetch(API_URL + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message || 'Lỗi ' + res.status);
  return data;
}

function Login({ onLogin }) {
  const [id, setId] = useState('student1@hcmut.edu.vn');
  const [password, setPassword] = useState('User@123');
  const submit = async () => {
    try {
      const s = await api('/api/auth/login', 'POST', { id, password });
      if (s.role !== 'USER') throw new Error('Ứng dụng di động dành cho sinh viên');
      token = s.token;
      onLogin(s);
    } catch (e) { Alert.alert('Đăng nhập thất bại', e.message); }
  };
  return (
    <View style={styles.box}>
      <Text style={styles.title}>Smart E-Mobility Hub</Text>
      <TextInput style={styles.input} value={id} onChangeText={setId} placeholder="Email hoặc MSSV" autoCapitalize="none" />
      <TextInput style={styles.input} value={password} onChangeText={setPassword} placeholder="Mật khẩu" secureTextEntry />
      <Pressable style={styles.button} onPress={submit}><Text style={styles.buttonText}>Đăng nhập</Text></Pressable>
    </View>
  );
}

function Hubs() {
  const [hubs, setHubs] = useState([]);
  useEffect(() => { api('/api/hubs').then(setHubs).catch(() => {}); }, []);
  return (
    <FlatList data={hubs} keyExtractor={(h) => String(h.id)} renderItem={({ item: h }) => (
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{h.code} · {h.name}</Text>
        <Text>{h.availableVehicles} xe · {h.freeSlots} chỗ đỗ · {h.freePorts} cổng sạc · {h.loadLevel}</Text>
      </View>)} />
  );
}

function Charging() {
  const [items, setItems] = useState([]);
  const load = useCallback(() => api('/api/charging/requests/my').then(setItems).catch(() => {}), []);
  useEffect(() => { load(); const t = setInterval(load, 3000); return () => clearInterval(t); }, [load]);
  const act = async (r, action) => { try { await api(`/api/charging/requests/${r.id}/${action}`, 'POST'); load(); } catch (e) { Alert.alert('Lỗi', e.message); } };
  return (
    <FlatList data={items} keyExtractor={(r) => String(r.id)} renderItem={({ item: r }) => (
      <View style={styles.card}>
        <Text style={styles.cardTitle}>#{r.id} {r.vehicleRef} · {r.status}</Text>
        <Text>{r.hubName} {r.portCode ? '· cổng ' + r.portCode : ''} {r.queuePosition ? '· vị trí chờ ' + r.queuePosition : ''}</Text>
        {r.session && <Text>Pin: {r.session.currentBattery}% / mục tiêu {r.targetBattery}%{r.session.dataInterrupted ? ' (mất dữ liệu)' : ''}</Text>}
        {r.status === 'CHARGING' && <Pressable style={styles.small} onPress={() => act(r, 'stop')}><Text>Dừng sạc</Text></Pressable>}
        {['WAITING', 'ASSIGNED'].includes(r.status) && <Pressable style={styles.small} onPress={() => act(r, 'cancel')}><Text>Huỷ</Text></Pressable>}
      </View>)} />
  );
}

function Bookings() {
  const [items, setItems] = useState([]);
  useEffect(() => { api('/api/bookings/my').then(setItems).catch(() => {}); }, []);
  return (
    <FlatList data={items} keyExtractor={(b) => String(b.id)} renderItem={({ item: b }) => (
      <View style={styles.card}><Text style={styles.cardTitle}>#{b.id} {b.vehicleCode} · {b.status}</Text><Text>{b.pickupHubName} · {b.pickupTime}</Text></View>)} />
  );
}

function Notifications() {
  const [items, setItems] = useState([]);
  useEffect(() => { api('/api/notifications').then(setItems).catch(() => {}); }, []);
  return (
    <FlatList data={items} keyExtractor={(n) => String(n.id)} renderItem={({ item: n }) => (
      <View style={styles.card}><Text style={styles.cardTitle}>{n.title}</Text><Text>{n.message}</Text></View>)} />
  );
}

const TABS = { hubs: ['Trạm', Hubs], bookings: ['Thuê xe', Bookings], charging: ['Sạc', Charging], notifications: ['Thông báo', Notifications] };

export default function App() {
  const [session, setSession] = useState(null);
  const [tab, setTab] = useState('hubs');
  if (!session) return <SafeAreaView style={styles.screen}><Login onLogin={setSession} /></SafeAreaView>;
  const Screen = TABS[tab][1];
  return (
    <SafeAreaView style={styles.screen}>
      <Text style={styles.header}>Xin chào, {session.fullName}</Text>
      <View style={{ flex: 1 }}><Screen /></View>
      <View style={styles.tabs}>{Object.entries(TABS).map(([k, [label]]) => (
        <Pressable key={k} onPress={() => setTab(k)} style={[styles.tab, tab === k && styles.tabOn]}><Text>{label}</Text></Pressable>))}</View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f4f6f5' },
  box: { padding: 24, marginTop: 80 },
  title: { fontSize: 22, fontWeight: '600', color: '#0f7a5a', marginBottom: 20, textAlign: 'center' },
  input: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#dde3e0', borderRadius: 8, padding: 10, marginBottom: 10 },
  button: { backgroundColor: '#0f7a5a', padding: 12, borderRadius: 8, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '600' },
  header: { padding: 16, fontSize: 16, fontWeight: '600' },
  card: { backgroundColor: '#fff', marginHorizontal: 12, marginBottom: 10, padding: 12, borderRadius: 10, borderWidth: 1, borderColor: '#dde3e0' },
  cardTitle: { fontWeight: '600', marginBottom: 4 },
  small: { marginTop: 8, alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6, borderWidth: 1, borderColor: '#0f7a5a' },
  tabs: { flexDirection: 'row', borderTopWidth: 1, borderColor: '#dde3e0', backgroundColor: '#fff' },
  tab: { flex: 1, padding: 14, alignItems: 'center' },
  tabOn: { backgroundColor: '#e3f3ec' },
});
