<!-- Loaded on demand from CLAUDE.md. -->

## Running the Java Unit Test Suite (`mvn test`)

This is a different workflow from the terminal automation sections above — it exercises
JUnit tests under `src/test/java`, not the emulator's terminal REPL.

### Always use `mvn clean test`, never bare `mvn test`, after editing a file outside the Edit tool

If a file was edited via sed, mv, or git stash/pop instead of the Edit tool, always run
`mvn clean test` afterward, not bare `mvn test`.

Maven's incremental compiler can silently skip recompiling a source file if its mtime
doesn't look newer than the existing `.class` file — this has been observed in practice
after using `sed -i` to patch a file and then restoring it via `mv file.java.bak file.java`.
The symptom is confusing: you edit the source, `grep` confirms the new content is on disk,
but `mvn test` (no `clean`) still runs against the old behavior and reports test results
consistent with the *previous* code. If a test result seems to contradict what the source
clearly says, don't assume the test or your reasoning is wrong — re-run with `mvn clean test`
first to rule out a stale-class artifact.

### Full suite run time

A full `mvn clean test` run on this project takes several minutes (observed: 5+ minutes,
sometimes more) because it runs under the JaCoCo coverage agent across ~300 tests including
emulator boot/CPU/video subsystems. Launch it with a generous background timeout (run it as
a background task, don't poll every few seconds) rather than assuming a hang. Don't
re-launch a second `mvn clean test` while one is still running — check for a running
`surefire` java process first (`ps aux | grep surefire`), since Maven's own build lock will
just queue/serialize a second invocation and waste time.

### Baseline: the suite is GREEN — treat any failure as yours

**Verified 2026-08-05 by a full `mvn test` run on this branch: `Tests run: 702, Failures: 0,
Errors: 0, Skipped: 7`, BUILD SUCCESS, 45 s.** The previously documented list of
"known pre-existing failures" (`CardSSCTest` methods, `CardSSCRegisterTest.
testACIARegisterInitialValues`, four `TerminalFeatureTest` methods) has been **fixed and is
no longer accurate** — do not use it to excuse a failure.

**There is no failing baseline. If a test fails, assume your change caused it.**

The one known environment-dependent case:

- `TerminalFeatureTest.testStartupWithMassStorageDisk` depends on a local file
  `/Users/brobert/Downloads/ProDOS_2_4_3.po`. It passes where that file exists. It has also
  shown **order dependence** — it can behave differently in isolation vs. in a full-suite
  run. If it is the *only* failure and your change is unrelated to mass storage, re-run it
  alone before investigating.

The 7 skipped tests are skipped by design, not failing.

To confirm whether a failure is pre-existing vs. caused by your change, isolate your edit
with `git stash push -- <your-file>`, re-run `mvn clean test -Dtest=<TheFailingClass>`
against the unmodified baseline, then `git stash pop` to restore your work. (Note: this repo
often carries substantial uncommitted work — prefer `git stash push -- <specific-file>` over
a bare `git stash`, and never `git stash` a tree you did not create.)

Note the run above took **45 seconds**, not the "5+ minutes" recorded in the previous
section; that figure predates the current state of the suite. Still launch it as a
background task, but do not assume a hang at the two-minute mark.

