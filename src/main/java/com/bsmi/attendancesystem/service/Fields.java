package com.bsmi.attendancesystem.service;

/** Checks and clean-up shared by the services for text typed into a form. */
final class Fields {
    private Fields() {}

    /** @throws MissingFieldException if the value is null, empty or only spaces */
    static void requireFilled(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new MissingFieldException(fieldName);
        }
    }

    /** Strong enough means long enough, with both uppercase and lowercase letters. */
    static boolean isStrongPassword(String password, int minLength) {
        return password.length() >= minLength
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase);
    }

    static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
