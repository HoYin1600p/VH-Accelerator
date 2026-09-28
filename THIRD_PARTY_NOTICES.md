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

ModernFix credits Uncandango's AllTheLeaks as the original inspiration for its
ingredient item-value deduplication. VH Accelerator's adaptation is derived
from ModernFix's implementation and preserves that discovery credit.

## FerriteCore

- Project: [FerriteCore](https://github.com/malte0811/FerriteCore)
- Author: [malte0811](https://github.com/malte0811)
- Upstream license notice: `Copyright (c) 2020 malte0811`
- License: MIT
- License source:
  [malte0811/FerriteCore LICENSE](https://github.com/malte0811/FerriteCore/blob/1.21.1/LICENSE)
- Adapted source: `modelsides` (`ModelSidesImpl`, `SimpleBakedModelMixin`) from
  branch `1.21.1`, commit `7baeea0bd337188114f889c581a28f02b74f3364`

VH Accelerator backports FerriteCore's model face-list compaction to Minecraft
1.18.2, where FerriteCore 4.2.2 has no equivalent option. Separately, VH
Accelerator's `compactFerriteCorePropertyMaps` builds on FerriteCore 4.2.2 at
runtime (compile-only; no FerriteCore code is bundled). These modifications
are maintained by HoYin1600p and are not official FerriteCore releases. The
MIT license text is reproduced here; the adapted files' headers reference it:

```text
MIT License

Copyright (c) 2020 malte0811

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Other projects

Projects used only for discovery, compatibility research, APIs, or testing are
credited in [CREDITS.md](CREDITS.md). Their source and binaries are not bundled
unless a future notice explicitly says otherwise.
