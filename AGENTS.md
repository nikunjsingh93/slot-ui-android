# AI Development Instructions

This project (an Android port of the "slot." handheld frontend) is developed with several AI coding agents:
Codex, Claude Code, OpenCode and Pi Agent. The shared project knowledge lives in the repository, not in any
agent's chat history.

## Starting a session

Before making changes:

1. Read `.ai/PROJECT.md` for architecture, decisions, pitfalls and the build/test/release procedure.
2. Read `.ai/HANDOFF.md` for the current state, recent work, open issues and next steps.
3. Run `git status` and `git log --oneline -10`, and inspect the files you are about to touch, to check the
   documented state is still accurate.
4. Continue from the latest confirmed state instead of restarting work.

## During development

- Follow the existing patterns. The UI lives in the Kotlin Multiplatform module `ui/`, the Android shell in `app/`.
- Prefer modifying existing code over rewrites. Preserve unrelated user changes.
- Record important technical decisions, discoveries and failed approaches in `.ai/PROJECT.md`.
- If a document and the code disagree, the code and the tests are the source of truth. Fix the document.
- Verify UI changes visually: headless previews (`./gradlew :ui:renderPreviews`) and, for anything touching input,
  files, other apps or emulators, on the real device through adb (see `.ai/PROJECT.md`).

## Rules specific to this project

- **Never delete, reset or "clean up" the user's data on their devices** while testing: `files/apps.json`,
  `settings.json`, `meta.json`, saves, states, or `<ROM folder>/.slot-backup.json`. Entries that look like test
  leftovers are usually the user's own choices. If a test must change state, `cat` the file first and restore the
  exact previous value. Never run `adb uninstall` on the user's devices: it wipes their saves and folder permission.
  Use `adb install -r` only.
- Never change the signing key (`app/slot-signing.keystore`). A different key forces users to uninstall and lose data.
- Do not commit, push or publish unless the user asks. When the user asks you to push an app change: bump
  `versionCode` and `versionName` in `app/build.gradle.kts`, build the release APK, push, and attach the APK to a new
  GitHub release (steps in `.ai/PROJECT.md`). Never print or store the GitHub token.
- Never put passwords, tokens or keys in any file in this repository, including these notes.

## Ending a session

After a substantial task, and whenever asked to hand off:

1. Update `.ai/HANDOFF.md`: what was completed, what remains, important files changed, known bugs and blockers.
2. Record the commands you ran and their actual results. Do not claim work was tested unless it was.
3. Write the specific next step another agent should take.
4. Move lasting lessons (not just status) into `.ai/PROJECT.md`.

Keep notes concise and accurate. Do not overwrite earlier findings without checking they are still true.
