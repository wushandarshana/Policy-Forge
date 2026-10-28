import { useState } from 'react';
import type { FormEvent } from 'react';
import { useSession } from './SessionContext';

/** Collects a username and password and keeps them in the in-memory session. Nothing is sent until a protected call. */
export function SignInForm() {
  const { signIn } = useSession();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (username.trim() === '' || password === '') {
      setError('Enter a username and a password.');
      return;
    }
    setError(null);
    signIn({ username: username.trim(), password });
  }

  return (
    <form className="panel" onSubmit={onSubmit} noValidate data-testid="signin-form">
      <h2>Sign in</h2>
      <div className="field">
        <label htmlFor="signin-username">Username</label>
        <input
          id="signin-username"
          data-testid="signin-username"
          type="text"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor="signin-password">Password</label>
        <input
          id="signin-password"
          data-testid="signin-password"
          type="password"
          autoComplete="off"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      </div>
      {error ? (
        <p className="banner banner-error" role="alert" data-testid="signin-error">
          {error}
        </p>
      ) : null}
      <button type="submit" className="button" data-testid="signin-submit">
        Sign in
      </button>
    </form>
  );
}
