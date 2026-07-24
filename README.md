# Database Utility

Beangle Database Development Utility — JDBC-based tools for schema work, data transport, and an interactive SQL shell.

## Launch

Download the launcher and run it against a config XML (real terminal recommended for history / Tab):

```bash
wget https://raw.githubusercontent.com/beangle/sqlplus/main/src/main/scripts/sqlplus.sh
chmod +x sqlplus.sh

./sqlplus.sh /path/to/db.xml
./sqlplus.sh transport /path/to/conversion.xml
./sqlplus.sh validate /path/to/basis.xml
```

| Mode | Example | Description |
|------|---------|-------------|
| Interactive shell | `./sqlplus.sh db.xml` | Connect and run SQL / meta commands |
| Transport | `./sqlplus.sh transport conversion.xml` | Copy data between databases |
| Validate | `./sqlplus.sh validate basis.xml` | Diff live schema against a basis XML |

Database config is an XML with a `<source>` (driver, url, user, password). See test resources under `src/test/resources/` for samples.

### What `sqlplus.sh` does

The script is a zero-install style launcher (similar to other Beangle boot scripts):

1. **Bootstrap jars** — downloads Scala runtime, `beangle-boot`, `beangle-commons`, logging, and `beangle-sqlplus` into `$M2_REPO` (default `~/.m2/repository`) from `$M2_REMOTE_REPO` (default Aliyun Maven).
2. **Resolve dependencies** — runs `beangle-boot` `AppResolver` so transitive JDBC drivers and libraries are fetched into the local Maven repo.
3. **Build classpath & start** — resolves the main class and full classpath, then executes:

   ```text
   java -cp <classpath> org.beangle.sqlplus.shell.Main <your args...>
   ```

All arguments after the script are passed through unchanged. Optional env vars:

| Variable | Default | Meaning |
|----------|---------|---------|
| `M2_REMOTE_REPO` | `https://maven.aliyun.com/repository/public` | Remote Maven repository |
| `M2_REPO` | `$HOME/.m2/repository` | Local Maven cache |

For development from source you can still use `sbt "run /path/to/db.xml"` instead of the published jar.

---

## Interactive shell

### Line editing & history

- **↑ / ↓** browse history (persisted in `~/.sqlplus_history`)
- **← / →** move cursor; **Ctrl+R** search history
- **Ctrl+C** clear current line; **Ctrl+D** or `exit` / `quit` / `q` leave
- Multi-line SQL: continue until `;` or `/`; prompt becomes `   -> `
- Wrong / failed commands are still stored in history (same as bash)

Use a real TTY (system terminal or IDEA “Emulate terminal”). Plain IDE consoles may fall back to dumb mode without arrow/Tab support.

### Meta commands

| Command | Description |
|---------|-------------|
| `help` | Show help |
| `info` | Test connection / show DB info |
| `list schema` | List schemas |
| `use schema` | Switch schema |
| `find pattern` | Find tables/views (optional `table` / `view` prefix) |
| `desc name` | Describe table/view |
| `list tmp` / `drop tmp` | List or drop temporary-like tables |
| `dump schema` | Dump schema to XML |
| `report schema` | HTML schema report |
| `validate schema` | Validate against `basis.xml` |
| `dump data` | Dump data into local H2 |
| `@file.sql` / `source file.sql` | Run a SQL script (`;`-separated) |

### SQL

- Supported starters: `select` / `insert` / `update` / `delete` / `alter` / `create` / `drop` / `grant`
- End a statement with `;` or `/`
- Once-off vertical layout: end with `\G` (psql-like expanded / mysql `\G`)

### Result display & settings

Default table format is **psql aligned** (display-width aware for CJK).

```text
set                  # show settings
set limit 50         # max rows (0 = unlimited, default 10)
set width 40         # max column display width (default 50)
set format table     # psql-style aligned table
set format vertical  # expanded records
set format csv       # CSV
spool /tmp/out.csv   # write result data to file only; console shows summary
spool off
```

### Tab completion

Press **Tab** to complete. Scope:

| Context | Completes |
|---------|-----------|
| Start of line | Shell commands (`help`, `dump schema`, `set format …`, …) |
| Mid-line (generic) | Fixed SQL keywords (`select`, `from`, `where`, `join`, …) — **not** dialect-specific |
| After `use` | Schema names from the **current** connection |
| After `find` / `desc` / `from` / `join` / `update` / `into` / `table` | Table/view names from cached JDBC metadata (same cache as `find` / `desc`) |

Notes:

- Keywords are a **static ANSI-ish list**, shared for all databases (not parsed SQL grammar).
- Object names come from the **current database** metadata (lazy-loaded, shared with `find`).
- At most **100** name candidates; if more: `... (N more)` at the end of the list.
- First metadata load may take a moment (same cost as first `find`).

**Not supported (by design for now):**

- Column-name completion (e.g. after `select` / `where`)
- Engine-specific keywords (`ILIKE`, `RETURNING`, `dual`, …)
- Context-aware SQL parsing (keywords may still appear where they are not valid)

---

## Transport data from db1 to db2

Edit config file (oracle to postgresql etc.)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<transport maxthreads="10">
  <source>
    <driver>oracle</driver>
    <url>jdbc:oracle:thin:@//192.168.100.1:1521/public</url>
    <user>user</user>
    <password>password</password>
  </source>
  <target>
    <driver>postgresql</driver>
    <url>jdbc:postgresql://192.168.100.2:5432/urp</url>
    <user>user</user>
    <password>password</password>
  </target>

  <task from="user" to="user">
    <tables lowcase="true" index="true" constraint="true">
      <includes>*</includes>
      <excludes></excludes>
    </tables>
  </task>

  <actions>
     <before>
       <sql file="/path/to/sql/file/do/something/in/oracle.sql"/>
     </before>
     <after>
       <sql file="/path/to/sql/file/do/something/in/postgresql.sql"/>
     </after>
  </actions>
</transport>
```

Run with:

```bash
./sqlplus.sh transport /path/to/your.xml
```
