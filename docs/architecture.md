# Architecture

A JavaFX desktop app in one Java module (`com.bsmi.attendancesystem`), with three layers: screens,
services and DAOs. Each layer only calls the one below it.

![The layers: FXML screens, controllers, services, DAOs and the H2 database, plus the phone scanner and startup](diagrams/architecture.excalidraw.svg)

## Startup and data folder

- `AttendanceApplication.init()` first runs `PendingRestore.applyIfReady` (see "Backup and
  restore" below), then `DatabaseMigrations.applyPending`, which lets Flyway create or upgrade
  the schema. `start()` then loads the bundled Inter font and shows the login screen.
- `AppPaths` picks the per-user data folder:
  - Windows: `%APPDATA%\AttendanceSystem`
  - macOS: `~/Library/Application Support/AttendanceSystem`
  - Linux: `$XDG_DATA_HOME/AttendanceSystem`, or `~/.local/share/AttendanceSystem` if that isn't set
  - `-Dattendance.home=<folder>` overrides all of these, which demos and tests use.
- `DatabaseSettings` builds the H2 URL for that folder. The `ATTENDANCE_DB_URL`,
  `ATTENDANCE_DB_USER` and `ATTENDANCE_DB_PASSWORD` environment variables point the app at a
  different database.

## dao

The only code that runs SQL. Each DAO opens a connection through `Database.open()`, uses
`PreparedStatement` with `?` placeholders (so typed text can never become SQL), and closes
everything with try-with-resources. An `SQLException` becomes an unchecked `DataAccessException`
whose message names the operation, such as "delete course BCA".

## service

The rules, kept out of the controllers so they can be tested without a screen:

- `AuthService` checks a login against the bcrypt hash (`Passwords`). An unknown user, a wrong
  password and a broken hash all give the same "wrong username or password" answer.
- `StudentService`, `CourseService`, `AttendanceService` and `UserService` check forms and
  permissions, such as required fields, duplicates, a course still in use, or removing the last
  Admin.
- `ImageStore` copies a chosen photo into `images/` in the data folder under a random name, and
  stores only that name. It accepts only `.png`, `.jpg` and `.jpeg`, and refuses names that would
  point outside the folder.
- `AttendanceService.scan(cardId)` is the one entry point for every card reader. It checks the
  student in, or out if they have an open entry today, and ignores the same card again within 5
  seconds.
- `StudentCards` draws a printable PNG card with the card ID as a QR code (ZXing) and makes new
  IDs like `STU-K7Q2MX9A`.
- `UserSession` (in the main package) remembers who logged in, so the dashboard can hide User
  Management from Employees.

## Backup and restore

- `Backup.write` asks H2 for a safe copy of the open database (`BackupDao.backupTo`, H2's
  `BACKUP TO`), and zips it with the photos in `images/`. `BackupCipher` encrypts the zip while
  it is written, into a temporary file next to the target that is moved into place only at the
  end, so a failed backup leaves no half-written file.
- `Backup.restore` decrypts and unpacks into `restore-pending/` in the data folder, and writes a
  `complete` marker last. It refuses the whole file on a wrong password, a changed byte, a name
  that isn't the database or a photo (which also blocks "zip slip", `../` names), more than 5 GB
  unpacked, or a database newer than this app knows.
- The open database file can't be swapped, so the app closes. On the next start,
  `PendingRestore.applyIfReady` runs before anything opens the database: it moves the current
  database and photos into `before-restore-<time>/` and the restored ones into their place. A
  pending folder without the marker is an unfinished restore and is deleted. Flyway then
  upgrades a backup from an older version as usual.
- Both work only with the built-in H2 file; `requireBuiltInDatabase` refuses a database server.

## scanner

Lets a phone act as a card reader:

- `PhoneScanServer` runs the JDK's built-in HTTP server on a free port while "Use phone as
  scanner" is ticked. Every path starts with a random 128-bit token. `GET /<token>/` serves the
  phone page (`scanner/phone-page.html`); `POST /<token>/scan` takes a photo or a typed ID, reads
  the QR code with `QrPhotoReader`, checks the ID, and hands it to the UI thread, which calls
  `AttendanceService.scan`. The phone gets the same message the desktop shows.
- `QrPhotoReader` returns every QR code in a photo. When ZXing reads nothing, it searches for the
  fourth corner of a tilted card itself (see "Reading tilted photos" in `decisions.md`).
- `ScanReply` is that answer: whether the scan worked, and the message.
- `NetworkAddresses` lists the computer's local IPv4 addresses, so the pairing QR code holds an
  address the phone can reach. If there are several, the user picks one.

## Controllers and FXML

The screens are FXML files in `src/main/resources/com/bsmi/attendancesystem/fxml`, styled by
`theme.css` (color tokens) plus one stylesheet per screen. `LoginController` and
`DashboardController` read the form fields, call a service, and show the result. They hold no SQL
and no business rules.

The windows have no title bar or border from the operating system (`StageStyle.TRANSPARENT`), so
`WindowFrame` does that job: dragging moves a window, and on the dashboard dragging an edge resizes
it and the title bar button maximizes it. The dashboard screens use anchors, an `HBox` and a
`GridPane` instead of fixed positions, so their tables and charts grow with the window.

## Errors

| Thrown by | Type | What the user sees |
|---|---|---|
| service | `ValidationException` subclasses (`MissingFieldException`, `AlreadyExistsException`, `StillInUseException`, ...) | An error alert with the exception's message, written for the user |
| scan field | `UnknownCardException`, other `ValidationException`s | The message in red under the scan field, no alert |
| scanner | Bad token, a bad card ID (typed or in the QR code), a file that isn't a photo, no QR code or several, a file or picture too large | 404, 400, 415, 422 or 413 with a plain message on the phone |
| dao | `DataAccessException` | A "database error" alert; the app keeps running |
| startup | `DatabaseSetupException` | JavaFX stops the launch and prints the cause. A friendlier dialog is a known gap |

## Tests

Run all of them with `./mvnw verify`.

| Suite | Covers |
|---|---|
| `dao/*Test` | Each DAO against its own in-memory H2 with the real migrations: CRUD, not-found, duplicates, injection strings stored as plain text, closed connections |
| `service/*Test` | Every validation rule and exception, every login outcome, photo copy and rejection, scan in/out/repeat, QR cards drawn and read back |
| `scanner/*Test` | The phone server over real http: page, typed ID, card photo, wrong token, bad and oversized uploads, megapixel bombs, bad QR text, two cards, crashing image readers, stop; tilted and damaged QR codes; address choice |
| `WindowFrameTest` | Which edges a press resizes, the size after a drag and its minimum, fitting the window on the screen |
| `AppPathsTest`, `DatabaseSettingsTest`, `DatabaseMigrationsTest`, ... | Folder choice per OS, env variables, a fresh and an already-current database, a broken one |
| `ui/*Test` (TestFX, tagged `ui`) | Login success and failure, form errors, course delete confirm/cancel, logout, the sidebar highlight, scanning a card, the phone pairing panel, resizing and maximizing the dashboard |

- UI tests need a display. On Linux, CI gives them one with `xvfb-run`. Without a display they are skipped, and
  `-DexcludedGroups=ui` skips them on purpose.
- JaCoCo fails the build if line coverage of `dao`, `service` or `scanner` drops below 90%.
