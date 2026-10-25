package com.bsmi.attendancesystem.service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Locks a backup file with a password, using only what is built into Java.
 *
 * <p>The file starts with a header: the bytes {@code ATTBAK}, the format version, a random salt
 * and the database version of the backup. PBKDF2 turns the password and salt into an AES-256 key.
 * The rest is AES-GCM in chunks, so a big backup never has to fit in memory. Each chunk is a
 * "last" flag, the length of the sealed bytes, and the sealed bytes. GCM checks every chunk:
 * its nonce holds the chunk's number and "last" flag, and the header is bound into each one, so a
 * changed, swapped or cut-off file fails like a wrong password does.
 */
final class BackupCipher {
    static final int DEFAULT_KEY_ROUNDS = 600_000;
    static final int DEFAULT_CHUNK_SIZE = 1 << 20;

    private static final byte[] MAGIC = "ATTBAK".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;
    private static final int SALT_LENGTH = 16;
    private static final int HEADER_LENGTH = MAGIC.length + 1 + SALT_LENGTH + Integer.BYTES;
    private static final int KEY_BITS = 256;
    private static final int TAG_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int keyRounds;
    private final int chunkSize;

    BackupCipher() {
        this(DEFAULT_KEY_ROUNDS, DEFAULT_CHUNK_SIZE);
    }

    /** Tests use few rounds and small chunks, so they run fast and cover many chunks. */
    BackupCipher(int keyRounds, int chunkSize) {
        if (chunkSize < 1 || chunkSize > DEFAULT_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize must be 1 to " + DEFAULT_CHUNK_SIZE);
        }
        this.keyRounds = keyRounds;
        this.chunkSize = chunkSize;
    }

    /**
     * Writes the header to {@code out} and returns a stream that encrypts what is written to it.
     * Closing that stream writes the last chunk and closes {@code out}.
     */
    OutputStream encryptingTo(OutputStream out, String password, int schemaVersion)
            throws IOException {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] header =
                ByteBuffer.allocate(HEADER_LENGTH)
                        .put(MAGIC)
                        .put((byte) FORMAT_VERSION)
                        .put(salt)
                        .putInt(schemaVersion)
                        .array();
        out.write(header);
        return new EncryptingStream(out, keyFrom(password, salt), header);
    }

    /**
     * Reads the header from {@code in} and checks the password on the first chunk.
     *
     * @throws UnreadableBackupException if it is not a backup, is from a newer format, the
     *     password is wrong or the first chunk is damaged
     */
    DecryptingStream decrypting(InputStream in, String password) throws IOException {
        byte[] header = in.readNBytes(HEADER_LENGTH);
        boolean isBackup =
                header.length == HEADER_LENGTH
                        && Arrays.equals(header, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
        if (!isBackup) {
            throw new UnreadableBackupException("This is not a backup file made by this app.");
        }
        ByteBuffer fields = ByteBuffer.wrap(header, MAGIC.length, HEADER_LENGTH - MAGIC.length);
        if (fields.get() != FORMAT_VERSION) {
            throw new UnreadableBackupException(
                    "This backup was made by a newer version of the app. Update the app first.");
        }
        byte[] salt = new byte[SALT_LENGTH];
        fields.get(salt);
        int schemaVersion = fields.getInt();
        return new DecryptingStream(in, keyFrom(password, salt), header, schemaVersion);
    }

    private SecretKey keyFrom(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, keyRounds, KEY_BITS);
        try {
            byte[] key =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                            .generateSecret(spec)
                            .getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException builtIntoEveryJava) {
            throw new IllegalStateException(builtIntoEveryJava);
        } finally {
            spec.clearPassword();
        }
    }

    private static GCMParameterSpec nonce(long chunkNumber, boolean last) {
        byte[] nonce =
                ByteBuffer.allocate(NONCE_LENGTH)
                        .putLong(chunkNumber)
                        .put(new byte[3])
                        .put((byte) (last ? 1 : 0))
                        .array();
        return new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nonce);
    }

    private static byte[] seal(
            SecretKey key, GCMParameterSpec nonce, byte[] header, byte[] plain, int length) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, nonce);
            cipher.updateAAD(header);
            return cipher.doFinal(plain, 0, length);
        } catch (GeneralSecurityException builtIntoEveryJava) {
            throw new IllegalStateException(builtIntoEveryJava);
        }
    }

    private static byte[] open(
            SecretKey key, GCMParameterSpec nonce, byte[] header, byte[] sealed) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, nonce);
            cipher.updateAAD(header);
            return cipher.doFinal(sealed);
        } catch (AEADBadTagException wrongKeyOrChangedBytes) {
            throw damaged();
        } catch (GeneralSecurityException builtIntoEveryJava) {
            throw new IllegalStateException(builtIntoEveryJava);
        }
    }

    private static UnreadableBackupException damaged() {
        return new UnreadableBackupException(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
    }

    private final class EncryptingStream extends OutputStream {
        private final DataOutputStream out;
        private final SecretKey key;
        private final byte[] header;
        private final byte[] chunk = new byte[chunkSize];
        private int filled;
        private long chunkNumber;
        private boolean closed;

        EncryptingStream(OutputStream out, SecretKey key, byte[] header) {
            this.out = new DataOutputStream(out);
            this.key = key;
            this.header = header;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) {
                throw new IOException("The backup is already closed");
            }
            while (length > 0) {
                // A full chunk is sealed only when more comes, so the last one is always "last"
                if (filled == chunk.length) {
                    writeChunk(false);
                }
                int copied = Math.min(length, chunk.length - filled);
                System.arraycopy(bytes, offset, chunk, filled, copied);
                filled += copied;
                offset += copied;
                length -= copied;
            }
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try (out) {
                writeChunk(true);
            }
        }

        private void writeChunk(boolean last) throws IOException {
            byte[] sealed = seal(key, nonce(chunkNumber++, last), header, chunk, filled);
            out.writeBoolean(last);
            out.writeInt(sealed.length);
            out.write(sealed);
            filled = 0;
        }
    }

    /** Decrypts as it is read; every check failure is an {@link UnreadableBackupException}. */
    static final class DecryptingStream extends InputStream {
        private final DataInputStream in;
        private final SecretKey key;
        private final byte[] header;
        private final int schemaVersion;
        private byte[] plain;
        private int position;
        private long chunkNumber;
        private boolean lastChunkRead;

        private DecryptingStream(InputStream in, SecretKey key, byte[] header, int schemaVersion)
                throws IOException {
            this.in = new DataInputStream(in);
            this.key = key;
            this.header = header;
            this.schemaVersion = schemaVersion;
            // Decrypting the first chunk now tells a wrong password before anything is unpacked
            readChunk();
        }

        /** @return the database version the backup was made with */
        int schemaVersion() {
            return schemaVersion;
        }

        @Override
        public int read() throws IOException {
            return hasMore() ? plain[position++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (!hasMore()) {
                return -1;
            }
            int copied = Math.min(length, plain.length - position);
            System.arraycopy(plain, position, bytes, offset, copied);
            position += copied;
            return copied;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

        private boolean hasMore() throws IOException {
            while (position == plain.length) {
                if (lastChunkRead) {
                    return false;
                }
                readChunk();
            }
            return true;
        }

        private void readChunk() throws IOException {
            try {
                int lastFlag = in.readUnsignedByte();
                int length = in.readInt();
                boolean sensibleLength =
                        length >= TAG_LENGTH && length <= DEFAULT_CHUNK_SIZE + TAG_LENGTH;
                if (lastFlag > 1 || !sensibleLength) {
                    throw damaged();
                }
                byte[] sealed = new byte[length];
                in.readFully(sealed);
                boolean last = lastFlag == 1;
                plain = open(key, nonce(chunkNumber++, last), header, sealed);
                position = 0;
                if (last) {
                    lastChunkRead = true;
                    if (in.read() != -1) {
                        throw damaged();
                    }
                }
            } catch (EOFException cutOffBeforeTheLastChunk) {
                throw damaged();
            }
        }
    }
}
