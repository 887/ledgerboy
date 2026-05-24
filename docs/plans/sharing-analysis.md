# ledgerboy + family — shared-code analysis (placeholder)

## Status: TBD — revisit after ledgerboy ships Phase A scaffold

This file is the same role as `whisperboy/docs/plans/sharing-analysis.md`: a placeholder for the cross-app shared-code analysis. The family-wide rule (validated on whisperboy) is:

> **A piece of code should be promoted to a shared library only if all five are true:**
>
> 1. Both (now multiple) apps need it.
> 2. The shape is the same.
> 3. It's atomic — single concern, not a "framework".
> 4. It's substantial — > ~600 LOC, OR demanding to write correctly, OR likely to evolve.
> 5. The cost of duplication exceeds the cost of a subrepo.

Cross-family-with-ledgerboy potential is **thinner than the audio sibling-pair** (tonearmboy ↔ whisperboy) because ledgerboy's data model (ledger + transactions + multi-currency + asset registry) is unique in the family. But a few candidates are worth listing now and reconsidering after Phase A:

### Plausible shared candidates (revisit post-Phase A)

- **SAF folder/document-picker patterns.** Tonearmboy doesn't use SAF (MediaStore); whisperboy, shutterboy, strictlykeptboy, pageboy, and ledgerboy all do (or will). Five SAF users in the family is potentially enough to clear bar (1) and (5). The `CachedDocumentFile` shape from whisperboy is the obvious starting point.
- **Settings catalog DSL.** Every app in the family lands the same catalog → entries → render pattern (see [`ui-shell.md`](ui-shell.md)). Currently each app copies it from tonearmboy. If the DSL evolves in tonearmboy and we want every app to benefit, that's a (5)-clearing signal. Probably still under bar (4) — it's a few hundred LOC of DSL, not load-bearing per-call.
- **Licensee plugin setup + LicensesScreen.** Identical across the family. Each app currently copies. Pattern is shared; code isn't worth lifting until the divergence rate is visible.
- **`Money` value class.** Ledgerboy specific — no other family member tracks money. Stays in ledgerboy.
- **Vertical-rail + top-bar nav shell.** Identical across the family. Same reasoning as the settings catalog DSL — probably not worth lifting until divergence visible.

### Definitely-not-shared (ledgerboy-specific)

- Banking connectors. Nothing else in the family touches banking.
- Asset valuation connectors. Same.
- Chart Composables. Same.
- CAMT.053 / MT940 / OFX / QIF parsers. Same.
- FX rate cache + `MoneyConverter`. Same.
- Encrypted database setup (SQLCipher). Nothing else in the family is encrypted by default (yet). Possibly strictlykeptboy adopts later — if so, two consumers might clear bar (1) but not bar (5) until something nontrivial diverges.

## Decision

**Don't share anything yet.** Build ledgerboy as an independent repo. Revisit this file after Phase A scaffold lands — at that point we'll have concrete LOC and concrete API shape for the candidates above, and the cost of premature abstraction will be visible.

The user's rule was: *only share if the win is biiiiig — the cost of a subrepo, separate versioning, and change-coordination overhead is real, and "shared" must clear that bar comfortably.*

Re-open this file when:

- A bug fix lands in one app and someone says "we'd want that in ledgerboy too" — non-trivially, twice within six months.
- Four+ apps in the family agree on a `CachedDocumentFile` shape that's identical and load-bearing — extract.
- A SOLID-refactor sweep across the family produces a clean abstraction over the settings catalog DSL that genuinely fits ledgerboy + the four other SAF apps' needs — extract.

If revisited, change the "Don't share anything yet" line above, write the new decision below it, and don't delete the prior analysis. Future-self wants to see what the previous "no" was based on.
