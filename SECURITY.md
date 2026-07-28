# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| `1.0.x` | Yes |
| Development snapshots | Best effort |

Only the latest patch release in a supported line receives security fixes.

## Reporting a vulnerability

Do not open a public issue for a vulnerability involving:

- arbitrary code execution or unsafe deserialization;
- path traversal or writing outside `cache/vhaccelerator/`;
- exposure of authentication/session data;
- a denial of service that can be triggered by an untrusted server;
- a crafted cache, packet, recipe, tag, model, or resource payload that crosses
  a validation boundary.

Use GitHub's private
[security advisory form](https://github.com/HoYin1600p/VH-Accelerator/security/advisories/new).
Include the affected version, impact, reproduction, and the smallest safe log
or sample needed to demonstrate the issue.

Please redact account tokens, session IDs, private server addresses, and local
profile-directory names.

## Non-security bugs

Crashes, missing textures, cache misses, performance regressions, and ordinary
mod conflicts should use the public
[issue forms](https://github.com/HoYin1600p/VH-Accelerator/issues/new/choose)
unless they provide an untrusted party with a security impact.
