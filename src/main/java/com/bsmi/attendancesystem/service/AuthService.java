package com.bsmi.attendancesystem.service;

import static com.bsmi.attendancesystem.service.Fields.requireFilled;
import static com.bsmi.attendancesystem.service.Fields.trimmedOrEmpty;

import com.bsmi.attendancesystem.UserData;
import com.bsmi.attendancesystem.dao.UserDao;
import java.util.List;
import java.util.Optional;

/** Checks a username and password on the login screen. */
public final class AuthService {
    /** The logins the demo data creates; the README prints them, so anyone can read them. */
    static final List<DemoLogin> PUBLIC_DEMO_LOGINS =
            List.of(new DemoLogin("admin", "admin123"), new DemoLogin("employee", "employee123"));

    private final UserDao users;
    private final Passwords passwords;

    public AuthService(UserDao users) {
        this(users, new Passwords());
    }

    AuthService(UserDao users, Passwords passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /**
     * An unknown username and a wrong password give the same empty result, so the login screen
     * can't reveal which usernames exist.
     *
     * @return the user, if the password is theirs
     * @throws MissingFieldException if the username or password is empty
     */
    public Optional<UserData> login(String username, String password) {
        requireFilled(username, UserService.USERNAME);
        requireFilled(password, UserService.PASSWORD);
        return users.findWithPasswordHash(trimmedOrEmpty(username))
                .filter(stored -> passwords.matches(password, stored.passwordHash()))
                .map(UserDao.StoredUser::user);
    }

    /**
     * Only the two demo usernames are tried, so the login screen never shows any other username.
     * Each try is one slow bcrypt check, so call this off the JavaFX thread.
     *
     * @return the demo logins whose password nobody has changed yet
     */
    public List<DemoLogin> demoLoginsThatStillWork() {
        return PUBLIC_DEMO_LOGINS.stream()
                .filter(demo -> login(demo.username(), demo.password()).isPresent())
                .toList();
    }

    /** @return true for a demo password, whatever account it was typed for */
    public static boolean isPublicDemoPassword(String password) {
        return PUBLIC_DEMO_LOGINS.stream().anyMatch(demo -> demo.password().equals(password));
    }

    public record DemoLogin(String username, String password) {}
}
