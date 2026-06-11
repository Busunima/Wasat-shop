import { useCallback, useEffect, useState } from "react";
import { getPlatformAnalytics, PLANS, type PlatformAnalytics } from "./api";

/** FR-S04: дашборд платформы (GMV, MAU, состав магазинов, топ-магазины, тренд). */
export function AnalyticsDashboard() {
  const [data, setData] = useState<PlatformAnalytics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getPlatformAnalytics());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Ошибка загрузки");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <p>Загрузка…</p>;
  if (error) return <p className="error">{error}</p>;
  if (!data) return null;

  const maxDailyGmv = Math.max(1, ...data.daily.map((d) => d.gmv));

  return (
    <section>
      <p className="muted">
        Период: {data.from} — {data.to}
      </p>

      <div className="cards">
        <Metric label="GMV (мин. ед.)" value={fmt(data.gmv)} />
        <Metric label="Заказы" value={fmt(data.orders)} />
        <Metric label="Средний чек (мин. ед.)" value={fmt(data.avgCheck)} />
        <Metric label="MAU (30 дн.)" value={fmt(data.mau)} />
        <Metric label="Магазины" value={fmt(data.stores.total)} />
        <Metric label="Публичные" value={fmt(data.stores.public)} />
        <Metric label="Заблокированы" value={fmt(data.stores.blocked)} />
        <Metric label="Поиски" value={fmt(data.searches)} />
      </div>

      <h2>Тарифы</h2>
      <div className="cards">
        {PLANS.map((p) => (
          <Metric key={p} label={p} value={fmt(data.stores.byPlan[p])} />
        ))}
      </div>

      <h2>Топ-магазины по GMV</h2>
      {data.topStores.length === 0 ? (
        <p className="muted">Нет данных за период.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Магазин</th>
              <th>GMV (мин. ед.)</th>
              <th>Заказы</th>
            </tr>
          </thead>
          <tbody>
            {data.topStores.map((s) => (
              <tr key={s.storeId}>
                <td>
                  <div className="name">{s.name || s.storeId}</div>
                  <div className="muted">/{s.slug}</div>
                </td>
                <td>{fmt(s.gmv)}</td>
                <td>{fmt(s.orders)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Дневной GMV</h2>
      {data.daily.length === 0 ? (
        <p className="muted">Нет данных за период.</p>
      ) : (
        <div className="trend">
          {data.daily.map((d) => (
            <div className="trend-row" key={d.date}>
              <span className="trend-date muted">{d.date}</span>
              <span className="trend-bar" style={{ width: `${(d.gmv / maxDailyGmv) * 100}%` }} />
              <span className="trend-val">{fmt(d.gmv)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <div className="metric-value">{value}</div>
      <div className="metric-label muted">{label}</div>
    </div>
  );
}

function fmt(n: number): string {
  return n.toLocaleString("ru-RU");
}
