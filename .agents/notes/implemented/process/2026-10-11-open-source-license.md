# Agent Note: Adopt Apache-2.0 for the project

Status: implemented

## Problem

The repository is intended for public release but has no root project license. Its source code, adapted examples, and third-party assets need a clear boundary so downstream users can tell which permission applies to which material.

## Decision

License the project-owned code under Apache License 2.0, with Bang Liu and contributors as the copyright holders. Add the complete license text at the repository root and link it from the README. Keep dependency, icon, and adapted-example notices separate because the project license cannot replace their original terms.

Apache-2.0 keeps commercial use, modification, redistribution, and proprietary integration possible while adding an explicit patent grant and patent-termination clause. The existing Android and Backdrop dependencies already use Apache-2.0, so the project boundary is consistent with the largest set of shipped code. MIT was not selected because it lacks the explicit patent terms needed for a long-lived public client; GPL was not selected because the project does not require downstream derivatives to remain open source.

## Alternatives considered

- MIT: shorter and permissive, but does not provide Apache-2.0's explicit patent grant and termination language.
- GPL-3.0: protects reciprocity, but would impose source-sharing obligations on integrations that the project does not intend to require.
- No root license: leaves reuse governed by default copyright law and makes the repository unsuitable for clear public collaboration.

## Consequences

Users may reuse project-owned code under Apache-2.0 when they preserve the license and notices. Third-party terms remain separately applicable, including CC BY attribution for Font Awesome icons and the Apache/MIT/BSD terms listed in `THIRD_PARTY_NOTICES.md`. The copyright holder name is now explicit; future contributors should retain the existing notice rather than adding conflicting project-wide terms.

## Verification

The root license text is the complete Apache License 2.0 text, README links resolve to the root file, and the decision note passes the repository note-tree and note-format checks.
