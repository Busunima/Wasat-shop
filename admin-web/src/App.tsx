import { useEffect, useState } from "react";
import {
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from "firebase/auth";
import { firebaseAuth, isFirebaseConfigured } from "./firebase";
import { StoresPage } from "./StoresPage";

type Session =
  | { state: "loading" }
  | { state: "not-configured" }
  | { state: "signed-out" }
  | { state: "not-superadmin"; user: User }
  | { state: "ready"; user: User };

/** Корень панели: гейт по входу Google + claim superadmin (ТЗ §7). */
export function App() {
  const [session, setSession] = useState<Session>({ state: "loading" });

  useEffect(() => {
    const auth = firebaseAuth();
    if (!auth) {
      setSession({ state: "not-configured" });
      return;
    }
    return onAuthStateChanged(auth, async (user) => {
      if (!user) {
        setSession({ state: "signed-out" });
        return;
      }
      const token = await user.getIdTokenResult();
      setSession(
        token.claims.superadmin === true
          ? { state: "ready", user }
          : { state: "not-superadmin", user },
      );
    });
  }, []);

  async function login() {
    const auth = firebaseAuth();
    if (auth) await signInWithPopup(auth, new GoogleAuthProvider());
  }

  function logout() {
    const auth = firebaseAuth();
    if (auth) void signOut(auth);
  }

  return (
    <div className="app">
      <header className="topbar">
        <h1>Wasat — Суперадмин</h1>
        {(session.state === "ready" || session.state === "not-superadmin") && (
          <button onClick={logout}>Выйти</button>
        )}
      </header>

      <main>
        {session.state === "loading" && <p>Загрузка…</p>}

        {session.state === "not-configured" && (
          <div className="card">
            <p>
              Firebase не сконфигурирован. Заполните <code>VITE_FIREBASE_*</code> в{" "}
              <code>.env</code> (см. <code>.env.example</code>) и пересоберите.
            </p>
          </div>
        )}

        {session.state === "signed-out" && isFirebaseConfigured && (
          <div className="card">
            <p>Доступ только для суперадминистраторов платформы.</p>
            <button onClick={login}>Войти через Google</button>
          </div>
        )}

        {session.state === "not-superadmin" && (
          <div className="card">
            <p>
              Учётная запись <strong>{session.user.email}</strong> не имеет роли
              суперадмина. Назначьте её командой{" "}
              <code>npm run grant-superadmin -- &lt;uid&gt;</code> на сервере.
            </p>
          </div>
        )}

        {session.state === "ready" && <StoresPage />}
      </main>
    </div>
  );
}
