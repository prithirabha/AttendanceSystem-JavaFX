package com.bsmi.attendancesystem.service;

/** A backup file can't be restored: wrong password, damaged, or holding things it shouldn't. */
public class UnreadableBackupException extends ValidationException {
    public static final String WRONG_PASSWORD_OR_DAMAGED =
            "Wrong password, or the file is damaged.";

    public UnreadableBackupException(String reason) {
        super(reason);
    }
}
