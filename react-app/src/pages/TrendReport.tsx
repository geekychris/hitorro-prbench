import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, Bot, Suite } from '../api/client';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function TrendReport() {
  const [suiteId, setSuiteId] = useState<number>(0);
  const [botId, setBotId] = useState<number>(0);

  const suites = useQuery({ queryKey: ['suites'], queryFn: () => api.get<Suite[]>('/suites') });
  const bots = useQuery({ queryKey: ['bots'], queryFn: () => api.get<Bot[]>('/bots') });

  const trend = useQuery({
    queryKey: ['trend', botId, suiteId],
    queryFn: () => api.get<any[]>(`/reports/bots/${botId}/trend?suiteId=${suiteId}&limit=20`),
    enabled: botId > 0 && suiteId > 0,
  });

  return (
    <div>
      <div className="page-header"><h1>Performance Trends</h1></div>

      <div className="card">
        <div className="form-row">
          <div className="form-group">
            <label>Suite</label>
            <select value={suiteId} onChange={e => setSuiteId(Number(e.target.value))}>
              <option value={0}>Select...</option>
              {suites.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label>Bot</label>
            <select value={botId} onChange={e => setBotId(Number(e.target.value))}>
              <option value={0}>Select...</option>
              {bots.data?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </div>
        </div>
      </div>

      {trend.data && trend.data.length > 0 && (
        <div className="card">
          <h3>F1 / Precision / Recall Over Time</h3>
          <div style={{height:400}}>
            <ResponsiveContainer>
              <LineChart data={trend.data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="runName" />
                <YAxis domain={[0, 1]} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="f1Score" stroke="#e74c3c" strokeWidth={2} name="F1" />
                <Line type="monotone" dataKey="precision" stroke="#3498db" strokeWidth={2} />
                <Line type="monotone" dataKey="recall" stroke="#2ecc71" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <table style={{marginTop:16}}>
            <thead><tr><th>Run</th><th>Precision</th><th>Recall</th><th>F1</th></tr></thead>
            <tbody>
              {trend.data.map((t: any, i: number) => (
                <tr key={i}>
                  <td>{t.runName}</td>
                  <td>{t.precision}</td>
                  <td>{t.recall}</td>
                  <td><strong>{t.f1Score}</strong></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {trend.data?.length === 0 && botId > 0 && suiteId > 0 && (
        <div className="card"><div className="empty-state">No trend data available yet.</div></div>
      )}
    </div>
  );
}
