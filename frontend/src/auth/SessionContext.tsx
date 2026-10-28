import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';
import type { Credentials } from '../api/types';

export interface Session {
  credentials: Credentials | null;
  signIn: (credentials: Credentials) => void;
  signOut: () => void;
}

const SessionContext = createContext<Session>({ credentials: null, signIn: () => {}, signOut: () => {} });

/** Stub: implemented in the green step. */
export function SessionProvider({ children }: { children: ReactNode; initialCredentials?: Credentials | null }) {
  return <SessionContext.Provider value={{ credentials: null, signIn: () => {}, signOut: () => {} }}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  return useContext(SessionContext);
}
