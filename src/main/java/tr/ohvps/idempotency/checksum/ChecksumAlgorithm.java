package tr.ohvps.idempotency.checksum;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

/**
 * ÖHVPS kuralı checksum algoritmasını HHS'ye bırakır (CRC32, MD5 vb.).
 * Çakışma ihtimali en düşük olduğu için varsayılan SHA256'dır.
 */
public enum ChecksumAlgorithm {

    CRC32 {
        @Override
        public String checksum(byte[]... parts) {
            CRC32 crc = new CRC32();
            for (byte[] part : parts) {
                crc.update(part);
            }
            return String.format("%08x", crc.getValue());
        }
    },

    MD5 {
        @Override
        public String checksum(byte[]... parts) {
            return digest("MD5", parts);
        }
    },

    SHA256 {
        @Override
        public String checksum(byte[]... parts) {
            return digest("SHA-256", parts);
        }
    };

    public abstract String checksum(byte[]... parts);

    private static String digest(String algorithm, byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            for (byte[] part : parts) {
                digest.update(part);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " algoritması bu JVM'de yok", e);
        }
    }
}
