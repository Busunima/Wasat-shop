import type { StoreInspection } from "./api";

/** Модальный обзор магазина «глазами владельца» (FR-S02, read-only). */
export function StoreInspectModal({
  inspection,
  onClose,
}: {
  inspection: StoreInspection;
  onClose: () => void;
}) {
  const { store, usage, recentOrders } = inspection;

  function money(minor: number, currency: string): string {
    return `${(minor / 100).toFixed(2)} ${currency}`;
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <header className="modal-head">
          <div>
            <h2>{store.name}</h2>
            <div className="muted">
              /{store.slug} · {store.ownerEmail}
            </div>
          </div>
          <button onClick={onClose}>Закрыть</button>
        </header>

        <div className="inspect-stats">
          <div className="stat">
            <div className="muted">Товары</div>
            <div className="stat-value">{usage.products}</div>
          </div>
          <div className="stat">
            <div className="muted">Заказы</div>
            <div className="stat-value">{usage.orders}</div>
          </div>
          <div className="stat">
            <div className="muted">Сотрудники</div>
            <div className="stat-value">{usage.staff}</div>
          </div>
          <div className="stat">
            <div className="muted">Тариф</div>
            <div className="stat-value">{store.plan}</div>
          </div>
        </div>

        <h3>Последние заказы</h3>
        {recentOrders.length === 0 ? (
          <p className="muted">Заказов пока нет.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Заказ</th>
                <th>Статус</th>
                <th>Покупатель</th>
                <th>Сумма</th>
              </tr>
            </thead>
            <tbody>
              {recentOrders.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id.slice(0, 8)}</td>
                  <td>{o.status}</td>
                  <td className="muted">{o.customerEmail}</td>
                  <td>{money(o.total, o.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
