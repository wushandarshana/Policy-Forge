import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { Credentials } from '../api/types';

export interface Session {
  credentials: Credentials | null;
  signIn: (credentials: Credentials) => void;
  signOut: () => void;
}

const SessionContext = createContext<Session>({ credentials: null, signIn: () => {}, signOut: () => {} });

/**
 * Holds the Basic-auth credentials in React state only: never in localStorage, sessionStorage, cookies or a URL.
 * A reload therefore means signing in again (spec UI "Sign-in"). `initialCredentials` exists for tests and for a
 * shell that already knows the user.
 */
export function SessionProvider({
  children,
  initialCredentials = null,
}: {
  children: ReactNode;
  initialCredentials?: Credentials | null;
}) {
  const [credentials, setCredentials] = useState<Credentials | null>(initialCredentials);
  const value = useMemo<Session>(
    () => ({ credentials, signIn: setCredentials, signOut: () => setCredentials(null) }),
    [credentials],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  return useContext(SessionContext);
}
