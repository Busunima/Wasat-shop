import { useCallback, useEffect, useState } from "react";
import {
  listStores,
  setStoreBlocked,
  setStorePlan,
  PLANS,
  type AdminStore,
  type Plan,
} from "./api";

/** FR-S01 список магазинов + FR-S02 блокировка/тариф. */
export function StoresPage() {
  const [stores, setStores] = useState<AdminStore[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (q: string) => {
    setLoading(true);
    setError(null);
    try {
      const page = await listStores({ q: q.trim() || undefined });
      setStores(page.items);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Ошибка загрузки");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load("");
  }, [load]);

  async function toggleBlock(store: AdminStore) {
    const blocked = !store.isBlocked;
    const reason = blocked ? window.prompt("Причина блокировки:") ?? undefined : undefined;
    try {
      const updated = await setStoreBlocked(store.storeId, blocked, reason);
      setStores((prev) => prev.map((s) => (s.storeId === updated.storeId ? updated : s)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось изменить блокировку");
    }
  }

  async function changePlan(store: AdminStore, plan: Plan) {
    try {
      const updated = await setStorePlan(store.storeId, plan);
      setStores((prev) => prev.map((s) => (s.storeId === updated.storeId ? updated : s)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось сменить тариф");
    }
  }

  return (
    <section>
      <form
        className="search"
        onSubmit={(e) => {
          e.preventDefault();
          void load(query);
        }}
      >
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Поиск: storeId, slug, email, название"
        />
        <button type="submit">Найти</button>
      </form>

      {error && <p className="error">{error}</p>}
      {loading && <p>Загрузка…</p>}

      {!loading && stores.length === 0 && <p>Магазины не найдены.</p>}

      {stores.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Магазин</th>
              <th>Владелец</th>
              <th>Тариф</th>
              <th>Статус</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {stores.map((store) => (
              <tr key={store.storeId} className={store.isBlocked ? "blocked" : undefined}>
                <td>
                  <div className="name">{store.name}</div>
                  <div className="muted">/{store.slug}</div>
                </td>
                <td className="muted">{store.ownerEmail}</td>
                <td>
                  <select
                    value={store.plan}
                    onChange={(e) => void changePlan(store, e.target.value as Plan)}
                  >
                    {PLANS.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                </td>
                <td>{store.isBlocked ? "🚫 заблокирован" : store.isPublic ? "🟢 публичный" : "⚪ черновик"}</td>
                <td>
                  <button onClick={() => void toggleBlock(store)}>
                    {store.isBlocked ? "Разблокировать" : "Заблокировать"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
