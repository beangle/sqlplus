# Changelog

All notable changes to this project are documented in this file.

## [0.2.5] - 2026-08-07

### Changed
- Upgrade build to sbt 2.x (bare settings and plugins)
- Delegate SQL script parsing/execution to `beangle-jdbc` `Parser` / `Runner` (shell `@file` / `source` and transport SQL actions)
- Propagate SQL action failures while still running later best-effort statements
- Align `sqlplus.sh` / `sqlplus.ps1` bootstrap dependency versions; pin launcher artifact to `0.2.5`
- Depend on `beangle-jdbc` 1.1.12

## [0.2.4] - 2026-07-26

### Added
- Case-insensitive Tab completion for shell commands and object names
- Per-task transport result reporting; clearer best-effort JDBC transport summaries

### Changed
- Improve transport actions and metadata handling
- Hide verbose scan details from the transport summary

### Fixed
- Bind terminal Enter capability so Return works reliably in more environments

## [0.2.3] - 2026-07-25

### Added
- Windows PowerShell launcher `sqlplus.ps1`

### Fixed
- Terminal Enter key handling in the interactive shell

## [0.2.2] - 2026-07-25

### Changed
- `spool` always streams full SELECT results as CSV (ignores console `set limit` / `format`)
- `set format` is console-only: `table` | `vertical` (CSV is no longer a format option)

## [0.2.1] - 2026-07-25

### Added
- Interactive shell on **JLine 4** (JNI): history (`~/.sqlplus_history`), line editing, Ctrl+R
- psql-style multi-line SQL continuation (`   -> `) with one history entry per statement
- Tab completion for shell commands, SQL keywords, schemas, and tables/views
- psql-style aligned / vertical result display (CJK display-width aware); once-off `\G`
- `set limit` / `set width` / `set format`, `spool` / `spool off`, `@file.sql` / `source`, query timing
- Improved `desc` column order (primary-key columns first)

### Changed
- Document launch via `sqlplus.sh` (including transport / validate modes)

## [0.2.0] - 2026-05-23

### Changed
- Rebase on `beangle-commons` console API
- Simplify schema report structure

## [0.1.1] - 2026-03-06

### Added
- Report transport success / failure outcomes

### Changed
- Module logging updates

## [0.1.0] - 2026-01-13

### Changed
- Update to `beangle-commons` 5.7.0

## Earlier

See git tags `v0.0.1` … `v0.0.46` for pre-0.1 history (transport, validate, schema dump/report, and related fixes).
