import type { ReactNode } from 'react';
import { useSession } from './SessionContext';
import { SignInForm } from './SignInForm';

/** Shows the sign-in form until the user has signed in, then the children. The shell uses it as the gate. */
export function RequireSession({ children }: { children: ReactNode }) {
  const { credentials } = useSession();
  return credentials ? <>{children}</> : <SignInForm />;
}
