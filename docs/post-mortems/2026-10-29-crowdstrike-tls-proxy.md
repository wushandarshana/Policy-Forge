# Post-mortem: harness agents can't reach the API (corporate TLS inspection)

**Date:** 2026-10-29 · **Phase:** Sprint 2, follow-up harness run `wf_273f9690-e40` · **Severity:** blocked all agent
work · **Status:** resolved (pending a Claude Code restart)

## Symptom
The follow-up run errored about 2 seconds after it started. All three agents (generator, aggregator, evaluator) failed
with `API Error: Unable to connect to API: SSL certificate verification failed (UNABLE_TO_VERIFY_LEAF_SIGNATURE)`. They
spent 0 tokens and changed nothing. The previous run (`wf_e4b8b7d1-027`, about 12 hours on 2026-10-28) had worked on
the same machine.

## Environment-first investigation
No code was touched.

1. **Proxy and CA variables:** `NODE_EXTRA_CA_CERTS`, `HTTPS_PROXY` and `SSL_CERT_FILE` were all unset.
2. **Reachability:** `curl https://api.anthropic.com/` failed with `schannel: CRYPT_E_NO_REVOCATION_CHECK`, while
   `https://github.com` returned 200. That points to a TLS anomaly specific to the API host.
3. **Presented certificate:** `openssl s_client` showed `subject=CN=api.anthropic.com` with
   `issuer=O=CrowdStrike, CN=Falcon ROOT CA Proxy`. The traffic is intercepted and re-signed by CrowdStrike Falcon's
   TLS-inspection proxy.
4. **Is the interception sanctioned?** `Falcon ROOT CA Proxy` (thumbprint `B576D14B…95B7`) is installed by IT in both
   the `LocalMachine\Root` and `CurrentUser\Root` Windows stores. Its `NotAfter` is 2027-10-29, exactly one year from
   the failure date, which suggests it was rolled out or rotated that day. That explains why the earlier run worked.

## Root cause
Node.js, the runtime Claude Code runs on, uses its own CA bundle and doesn't trust Windows-store CAs by default. Once
CrowdStrike started re-signing `api.anthropic.com`, the agents' TLS handshakes failed verification.

## Resolution
With the user's approval, Claude Code was pointed at the corporate CA. TLS verification was **not** disabled:
`NODE_TLS_REJECT_UNAUTHORIZED=0` was explicitly rejected.

- Exported the IT-installed CA from `Cert:\CurrentUser\Root` to `%USERPROFILE%\.certs\crowdstrike-falcon-root.pem`.
  Its SHA-1 fingerprint matches the store thumbprint.
- Set the user environment variable `NODE_EXTRA_CA_CERTS` to that file.
- **Verification:** a Node `https.get('https://api.anthropic.com/')` fails with `UNABLE_TO_VERIFY_LEAF_SIGNATURE`
  without the variable, which reproduces the agents' error exactly. With the variable it succeeds (status 404 on `/`,
  issuer `CrowdStrike`).
- Claude Code must be **restarted** to inherit the variable. Running processes keep their old environment (compare
  KD-001).

## Prevention
- Knowledge deposit KD-015.
- If the CA rotates again (the new one expires 2027-10-29), re-export it by thumbprint and update the PEM.
