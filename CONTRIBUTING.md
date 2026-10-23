# Contributing

Thanks for helping. [Prithiraj Rabha](https://github.com/prithirabha) maintains this project and
reviews every pull request.

## Before you start

For anything bigger than a small fix, open an issue first and describe the change, so we can agree
on it before you spend time on it.

## Making a change

1. Fork the repo and create a branch from `main`.
2. Build and run it as described in the [README](README.md#build-from-source). You need JDK 25 or
   newer; Maven comes with the wrapper.
3. Make your change, with tests for it, including the error cases.
4. Run `./mvnw verify` (Windows: `mvnw.cmd verify`). It must pass, including the 90% coverage gate
   on `dao`, `service` and `scanner`. See [Running the tests](README.md#running-the-tests).
5. Open a pull request against `main` that says what changed and why. CI runs on Linux, Windows
   and macOS; the maintainer reviews it once CI is green.

## Guidelines

- Keep a pull request to one change.
- Schema changes go in a new `V<next number>__<description>.sql` migration. Never edit one that
  has already been released.
- New SQL must work on H2 (for example `CURRENT_DATE`, not `CURDATE()`).
- Name things so the code explains itself, and keep comments for the *why*.
- If users will notice the change, end the commit message with `Changelog: new` or
  `Changelog: fix`. It puts the commit title under What's new or Fixes in the next release notes.

## License

By contributing, you agree that your contribution is licensed under the
[GNU General Public License v3.0](LICENSE), like the rest of the project.
