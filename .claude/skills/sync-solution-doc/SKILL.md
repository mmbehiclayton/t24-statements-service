---
name: sync-solution-doc
description: Export the solution design Claude Doc to docs/solution-design.md (restore screenshots, stamp the revision) and commit. Use after any edit to the Claude Doc, or when asked to refresh or push the docs.
---

# Sync the solution design doc into the repo

The Claude Doc "Customer Statement Generation Solution — T24 R21" is the master copy:
- doc id `ea4a1281-f7b1-40aa-9cbb-ab066ab5d635`
- tab/file `f835ccae-30bc`
- link https://claude.ai/artifact/Vw13UDKTd8XdmurPR1edV6

Load the docs skill (or `guide topic.index`) before calling the docs tools.

## Steps

1. Check the doc's current revision: read the project, or run a `search` on body node `e27d82cb-1363`. The `rev` is in the result.
2. Export: `export(container={"kind":"project","id":"ea4a1281-f7b1-40aa-9cbb-ab066ab5d635"}, file="f835ccae-30bc", format="markdown")`. The tool saves a large result to a file. Find the `bytes_b64` value recursively in it and base64-decode it (Python, `-I`).
3. Post-process the markdown:
   - Put the header line under the title: `> Exported from the Claude Doc on <date> (rev <N>). The Claude Doc is the master copy.`
   - Screenshot placeholders export as `&#91;image: ALT\]`. Replace each with `![ALT](images/<file>.png)`, using the four files in `docs/images/` and matching on the alt text. Build the backslash with `chr(92)` in Python; backslashes in a bash heredoc break the script.
   - Diagrams (widgets) stay as `&#91;embedded content: <caption>\]`. That is expected.
4. Diff against the previous file and check for leaks: no client or bank name, country, IP or host, "R25" or "model bank":
   ```bash
   grep -n -i -E "R25|model bank|ssedev|[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" docs/solution-design.md
   ```
   Expect no hits.
5. Commit on `main` with a message naming the revision, for example `docs: solution design rev <N> (<what changed>)`. **Ask before pushing.**
6. Update the status line in `context/progress-tracker.md` (doc revision, pushed or not).
