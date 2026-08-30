# Third-party notices

VH Accelerator post-1.0.13 development is distributed under
LGPL-3.0-or-later. The complete license is in [LICENSE](LICENSE). Releases
through 1.0.13 remain available under the MIT license terms attached to those
releases.

No third-party mod jar is bundled in VH Accelerator. When source is adapted,
the notices below supplement the project license and the file-level provenance
headers; they do not replace either one.

## ModernFix

- Project: [ModernFix](https://github.com/embeddedt/ModernFix)
- Author and maintainer: [embeddedt](https://github.com/embeddedt)
- Contributors: the contributors recorded in ModernFix's Git history
- Upstream license notice: `Copyright (c) 2022`
- License: LGPL-3.0-or-later
- License source:
  [embeddedt/ModernFix LICENSE at the audited baseline](https://github.com/embeddedt/ModernFix/blob/fe855f15304ed788122a27cda4c2495a78374528/LICENSE)
- Minecraft 1.18.2 baseline: tag `5.18.0+1.18.2`, commit
  `fe855f15304ed788122a27cda4c2495a78374528`

VH Accelerator adapts selected upstream ModernFix corrections for Minecraft
1.18.2. These modifications are maintained by HoYin1600p and are not official
ModernFix releases. Exact source paths, commits, destination files, and
modification summaries are recorded in
[docs/MODERNFIX_BACKPORTS.md](docs/MODERNFIX_BACKPORTS.md).

ModernFix's README states that its configuration system is directly derived
from Sodium and used under LGPL-3.0. Any VH Accelerator port derived from that
part of ModernFix must additionally preserve the relevant Sodium copyright and
provenance in this notice and in its file header.

## Other projects

Projects used only for discovery, compatibility research, APIs, or testing are
credited in [CREDITS.md](CREDITS.md). Their source and binaries are not bundled
unless a future notice explicitly says otherwise.
