# ModernFix backport provenance and ownership

This ledger is the release authority for source adapted from
[embeddedt/ModernFix](https://github.com/embeddedt/ModernFix). A ModernFix
backport may not ship unless its row is complete and its destination files
carry matching provenance headers.

## Licensing baseline

- VH Accelerator post-1.0.13 development: LGPL-3.0-or-later
- ModernFix: LGPL-3.0-or-later
- ModernFix 1.18.2 baseline: tag `5.18.0+1.18.2`, commit
  `fe855f15304ed788122a27cda4c2495a78374528`
- Upstream repository: `https://github.com/embeddedt/ModernFix`
- Upstream license:
  `https://github.com/embeddedt/ModernFix/blob/fe855f15304ed788122a27cda4c2495a78374528/LICENSE`

Published releases through VH Accelerator 1.0.13 remain under their original
MIT terms. Relicensing future VH Accelerator versions does not revoke those
permissions.

## Required file header

Every copied or adapted source file must include a comment equivalent to:

```text
SPDX-License-Identifier: LGPL-3.0-or-later

Adapted for VH Accelerator from ModernFix.
Upstream repository: https://github.com/embeddedt/ModernFix
Upstream source: <path>
Upstream commit: <full commit ID>
Original copyright: <preserved upstream notice>
VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
Modified: <YYYY-MM-DD; concise summary>
```

If the upstream file names an earlier project or license, preserve that notice
and add the earlier source to `THIRD_PARTY_NOTICES.md`.

## Port ledger

| Feature | VH Accelerator destination | Upstream source path | Upstream commit | Original notice and license | Modification summary | Default and side |
| --- | --- | --- | --- | --- | --- | --- |
| Forge handshake batching/stall correction | `backport/modernfix/network/ForgeHandshakeBatcher.java`<br>`mixin/backport/modernfix/network/HandshakeHandlerAccess.java`<br>`mixin/backport/modernfix/network/HandshakeHandlerMixin.java` | `src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_handshake_stall/HandshakeHandlerMixin.java` | `c2f585da9551d925c01b391ddd151e02c5037382` (includes introduction at `79d2b28d5b8098779874b01e4d46a9567ae788f0`) | Copyright (c) 2022; embeddedt and ModernFix contributors; LGPL-3.0-or-later | Replaced MixinExtras wrappers with standard Mixin redirects, extracted the progress loop for unit testing, retained the synchronized acknowledgement list, and retained the Forge off-by-one completion correction. | Off; common (the accelerated path executes on the server handshake owner and remains wire-compatible with unmodified peers) |

## Ownership rules

- Each backport receives an independent configuration option.
- Client, common, and dedicated-server targets remain physically separated.
- VH Accelerator must not apply a backport when stock ModernFix already owns
  the same active feature unless the exact ModernFix option is disabled and
  the ownership decision is verified.
- Unknown ModernFix option state fails closed.
- Compare Mode must bypass every performance-changing backport while retaining
  explicitly enabled measurements.
- Recipe, JEI, DFU, resource-pack, BlockState, and stronghold work require an
  explicit ownership review before implementation.

## Release gate

Before publishing a build containing a ModernFix-derived port:

1. Verify every port's exact upstream commit and source path.
2. Preserve upstream and transitive copyright/license notices.
3. Record the adaptation and behavior changes in the ledger above.
4. Confirm the release jar contains `LICENSE`, `THIRD_PARTY_NOTICES.md`, and
   this provenance ledger, or provides prominent links to their corresponding
   source copies.
5. Publish the complete corresponding source for the exact release tag.
6. Validate the option independently with ModernFix absent and present, then
   test client-only, server-only, and both-sides installations as applicable.
