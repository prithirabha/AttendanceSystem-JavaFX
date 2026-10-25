package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupCipherTest {
    private static final String PASSWORD = "Correct Horse Battery";
    private static final int SCHEMA_VERSION = 3;
    private static final int CHUNK_SIZE = 64;
    // The layout BackupCipher writes: "ATTBAK", format version, 16-byte salt, schema version
    private static final int HEADER_LENGTH = 6 + 1 + 16 + 4;
    private static final int SALT_START = 7;
    private static final int SCHEMA_VERSION_START = 23;
    // Each chunk: a "last" flag, the sealed length, then the sealed bytes (data + 16-byte tag)
    private static final int CHUNK_OVERHEAD = 1 + 4 + 16;
    private static final int FULL_CHUNK_LENGTH = CHUNK_OVERHEAD + CHUNK_SIZE;
    private static final int FIRST_CHUNK_START = HEADER_LENGTH;
    private static final int SECOND_CHUNK_START = FIRST_CHUNK_START + FULL_CHUNK_LENGTH;
    private static final int THIRD_CHUNK_START = SECOND_CHUNK_START + FULL_CHUNK_LENGTH;

    private final BackupCipher cipher = new BackupCipher(1000, CHUNK_SIZE);
    // Two full chunks and a part one
    private final byte[] data = randomBytes(2 * CHUNK_SIZE + 22);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, CHUNK_SIZE - 1, CHUNK_SIZE, 2 * CHUNK_SIZE, 2 * CHUNK_SIZE + 22})
    void decryptingGivesBackTheSameBytes(int length) throws IOException {
        byte[] original = randomBytes(length);

        assertThat(decrypt(encrypt(original), PASSWORD)).isEqualTo(original);
    }

    @Test
    void bytesWrittenOneAtATimeComeBackTheSame() throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (OutputStream out = cipher.encryptingTo(file, PASSWORD, SCHEMA_VERSION)) {
            for (byte b : data) {
                out.write(b);
            }
        }
        try (InputStream in = cipher.decrypting(input(file.toByteArray()), PASSWORD)) {
            ByteArrayOutputStream plain = new ByteArrayOutputStream();
            for (int b = in.read(); b != -1; b = in.read()) {
                plain.write(b);
            }
            assertThat(plain.toByteArray()).isEqualTo(data);
            assertThat(in.read()).isEqualTo(-1);
        }
    }

    @Test
    void encryptedFileDoesNotContainTheData() throws IOException {
        byte[] secret = "Asha Rabha, roll number 4211".repeat(4).getBytes();

        assertThat(indexOf(encrypt(secret), "Asha".getBytes())).isEqualTo(-1);
    }

    @Test
    void sameDataEncryptsDifferentlyEachTime() throws IOException {
        assertThat(encrypt(data)).isNotEqualTo(encrypt(data));
    }

    @Test
    void schemaVersionIsReadFromTheHeader() throws IOException {
        try (BackupCipher.DecryptingStream in = cipher.decrypting(input(encrypt(data)), PASSWORD)) {
            assertThat(in.schemaVersion()).isEqualTo(SCHEMA_VERSION);
        }
    }

    @Test
    void readingNothingReturnsZero() throws IOException {
        try (InputStream in = cipher.decrypting(input(encrypt(data)), PASSWORD)) {
            assertThat(in.read(new byte[4], 0, 0)).isZero();
        }
    }

    @Test
    void wrongPasswordIsRefusedBeforeAnythingIsRead() throws IOException {
        byte[] file = encrypt(data);

        assertThatThrownBy(() -> cipher.decrypting(input(file), "Wrong Horse Battery"))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
    }

    @Test
    void fileWithoutTheBackupMarkIsNotABackup() {
        byte[] file = randomBytes(200);

        assertThatThrownBy(() -> cipher.decrypting(input(file), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage("This is not a backup file made by this app.");
    }

    @Test
    void fileShorterThanTheHeaderIsNotABackup() {
        byte[] file = "ATTBAK".getBytes();

        assertThatThrownBy(() -> cipher.decrypting(input(file), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage("This is not a backup file made by this app.");
    }

    @Test
    void newerFormatAsksToUpdateTheApp() throws IOException {
        byte[] file = encrypt(data);
        file[6] = 2;

        assertThatThrownBy(() -> cipher.decrypting(input(file), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessageContaining("Update the app first");
    }

    @ParameterizedTest
    @ValueSource(ints = {SALT_START, SCHEMA_VERSION_START + 3})
    void changedHeaderByteIsCaught(int position) throws IOException {
        byte[] file = encrypt(data);
        file[position] ^= 1;

        assertDamaged(file);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {FIRST_CHUNK_START + 5, SECOND_CHUNK_START + 10, THIRD_CHUNK_START + 5 + 21})
    void changedChunkByteIsCaught(int position) throws IOException {
        byte[] file = encrypt(data);
        file[position] ^= 1;

        assertDamaged(file);
    }

    @Test
    void fileCutOffBeforeTheLastChunkIsCaught() throws IOException {
        byte[] file = Arrays.copyOf(encrypt(data), THIRD_CHUNK_START);

        assertDamaged(file);
    }

    @Test
    void fileCutOffInsideAChunkIsCaught() throws IOException {
        byte[] file = Arrays.copyOf(encrypt(data), SECOND_CHUNK_START + 20);

        assertDamaged(file);
    }

    @Test
    void swappedChunksAreCaught() throws IOException {
        byte[] file = encrypt(data);
        byte[] first = Arrays.copyOfRange(file, FIRST_CHUNK_START, SECOND_CHUNK_START);
        System.arraycopy(file, SECOND_CHUNK_START, file, FIRST_CHUNK_START, FULL_CHUNK_LENGTH);
        System.arraycopy(first, 0, file, SECOND_CHUNK_START, FULL_CHUNK_LENGTH);

        assertDamaged(file);
    }

    @Test
    void bytesAfterTheLastChunkAreCaught() throws IOException {
        byte[] encrypted = encrypt(data);
        byte[] file = Arrays.copyOf(encrypted, encrypted.length + 1);

        assertDamaged(file);
    }

    @Test
    void lastChunkPretendingNotToBeLastIsCaught() throws IOException {
        byte[] file = encrypt(data);
        file[THIRD_CHUNK_START] = 0;

        assertDamaged(file);
    }

    @Test
    void unknownLastFlagIsCaught() throws IOException {
        byte[] file = encrypt(data);
        file[FIRST_CHUNK_START] = 2;

        assertDamaged(file);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 15, (1 << 20) + 17, Integer.MAX_VALUE})
    void impossibleChunkLengthIsCaughtBeforeReservingMemory(int length) throws IOException {
        byte[] file = encrypt(data);
        ByteBuffer.wrap(file).putInt(FIRST_CHUNK_START + 1, length);

        assertDamaged(file);
    }

    @Test
    void writingAfterCloseFails() throws IOException {
        OutputStream out = cipher.encryptingTo(new ByteArrayOutputStream(), PASSWORD, 1);
        out.close();
        out.close();

        assertThatThrownBy(() -> out.write(1)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, (1 << 20) + 1})
    void chunkSizeOutsideTheLimitsIsRefused(int chunkSize) {
        assertThatThrownBy(() -> new BackupCipher(1000, chunkSize))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertDamaged(byte[] file) {
        assertThatThrownBy(() -> decrypt(file, PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
    }

    private byte[] encrypt(byte[] plain) throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (OutputStream out = cipher.encryptingTo(file, PASSWORD, SCHEMA_VERSION)) {
            out.write(plain);
        }
        return file.toByteArray();
    }

    private byte[] decrypt(byte[] file, String password) throws IOException {
        try (InputStream in = cipher.decrypting(input(file), password)) {
            return in.readAllBytes();
        }
    }

    private static InputStream input(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new Random(length).nextBytes(bytes);
        return bytes;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }
}
