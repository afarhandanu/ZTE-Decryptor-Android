package com.sione.ztetype6.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * ZTE config.bin Type-6 codec.
 *
 * Implements the Type-6 path used by the referenced ZTE utilities:
 * - key material (KP) from serial + byte-reversed MAC
 * - AES-256-CBC key = SHA-256(KP)
 * - IV = first 16 bytes of SHA-256("ZTE%FN$GponNJ025")
 * - ZLIB chunk packing inside a ZTE payload header
 * - template preamble/header reuse for re-encryption
 */
public final class ZteType6Codec {
    private ZteType6Codec() {}

    public static final int PAYLOAD_MAGIC = 0x01020304;
    public static final int SIGNATURE_MAGIC = 0x04030201;
    private static final int[] ZTE_MAGIC = {
            0x99999999, 0x44444444, 0x55555555, 0xAAAAAAAA
    };
    private static final int CHUNK_SIZE = 65536;
    private static final String TYPE6_IV_PREFIX = "ZTE%FN$GponNJ025";

    public static final class DecodeResult {
        private final byte[] xml;
        private final String signature;
        private final int payloadType;
        private final int sourceOffset;

        public DecodeResult(byte[] xml, String signature, int payloadType, int sourceOffset) {
            this.xml = xml;
            this.signature = signature;
            this.payloadType = payloadType;
            this.sourceOffset = sourceOffset;
        }

        public byte[] xml() { return xml; }
        public String signature() { return signature; }
        public int payloadType() { return payloadType; }
        public int sourceOffset() { return sourceOffset; }
    }

    public static final class EncodeResult {
        private final byte[] bin;
        private final boolean verified;

        public EncodeResult(byte[] bin, boolean verified) {
            this.bin = bin;
            this.verified = verified;
        }

        public byte[] bin() { return bin; }
        public boolean verified() { return verified; }
    }

    private static final class Layout {
        final String signature;
        final int payloadType;
        final int payloadHeaderStart;
        final int payloadDataStart;
        final int preambleLength;
        final boolean outerHeader;
        final boolean littleEndian;

        Layout(String signature, int payloadType, int payloadHeaderStart, int payloadDataStart,
               int preambleLength, boolean outerHeader, boolean littleEndian) {
            this.signature = signature;
            this.payloadType = payloadType;
            this.payloadHeaderStart = payloadHeaderStart;
            this.payloadDataStart = payloadDataStart;
            this.preambleLength = preambleLength;
            this.outerHeader = outerHeader;
            this.littleEndian = littleEndian;
        }
    }

    public static DecodeResult decrypt(byte[] source, String serial, String mac) throws Exception {
        validateIdentity(serial, mac);
        Exception first = null;
        for (int offset : new int[]{0, 145}) {
            if (offset >= source.length) continue;
            try {
                byte[] slice = Arrays.copyOfRange(source, offset, source.length);
                Layout layout = inspect(slice);
                if (layout.payloadType != 6) {
                    throw new IllegalArgumentException("Payload bukan Type 6 (terdeteksi Type " + layout.payloadType + ").");
                }
                byte[] xml = decryptType6FromLayout(slice, layout, serial, mac);
                return new DecodeResult(xml, layout.signature, layout.payloadType, offset);
            } catch (Exception ex) {
                if (first == null) first = ex;
            }
        }
        throw new IllegalArgumentException("Gagal membaca/dekripsi config.bin Type 6. " +
                (first != null ? first.getMessage() : "Format tidak dikenali."), first);
    }

    public static EncodeResult encrypt(byte[] xml, byte[] templateBin, String serial, String mac) throws Exception {
        validateIdentity(serial, mac);
        Layout template = inspect(templateBin);
        if (template.payloadType != 6) {
            throw new IllegalArgumentException("Template config.bin bukan payload Type 6.");
        }
        if (!template.outerHeader) {
            throw new IllegalArgumentException("Template Type 6 tidak memiliki outer header ZTE yang diperlukan.");
        }

        byte[] inner = buildCompressedPayload(xml);
        String kp = deriveType6Kp(serial, mac);
        byte[] encrypted = aesCbcEncryptZeroPadded(inner, kp, TYPE6_IV_PREFIX);

        ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
        writeU32BE(wrapped, PAYLOAD_MAGIC);
        writeU32BE(wrapped, 6);
        writeU32BE(wrapped, 0);
        writeU32BE(wrapped, 0);
        writeU32BE(wrapped, 0);
        writeU32BE(wrapped, 0);
        for (int i = 0; i < 9; i++) writeU32BE(wrapped, 0);
        writeU32BE(wrapped, inner.length);
        writeU32BE(wrapped, encrypted.length);
        writeU32BE(wrapped, 0);
        wrapped.write(encrypted);
        byte[] wrappedBytes = wrapped.toByteArray();

        byte[] preamble = Arrays.copyOf(templateBin, template.preambleLength);
        int remainingSize = template.preambleLength - 128 + wrappedBytes.length;
        putU32(preamble, 0x48, remainingSize, template.littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

        ByteArrayOutputStream output = new ByteArrayOutputStream(preamble.length + wrappedBytes.length);
        output.write(preamble);
        output.write(wrappedBytes);
        byte[] finalBin = output.toByteArray();

        boolean verified;
        try {
            byte[] check = decrypt(finalBin, serial, mac).xml();
            verified = Arrays.equals(check, xml);
        } catch (Exception ex) {
            verified = false;
        }
        if (!verified) {
            throw new IllegalStateException("Round-trip verification gagal; output BIN tidak disimpan.");
        }
        return new EncodeResult(finalBin, true);
    }

    public static String deriveType6Kp(String serial, String mac) {
        String cleanMac = normalizeMac(mac);
        byte[] macBytes = hexToBytes(cleanMac);
        StringBuilder reversed = new StringBuilder(12);
        for (int i = macBytes.length - 1; i >= 0; i--) {
            reversed.append(String.format(Locale.US, "%02x", macBytes[i] & 0xFF));
        }

        final String serialSuffix;
        if (serial.length() == 12) {
            serialSuffix = serial.substring(4);
        } else if (serial.length() == 19) {
            serialSuffix = serial.substring(11);
        } else {
            throw new IllegalArgumentException("Serial Number harus 12 atau 19 karakter.");
        }
        return serialSuffix + reversed;
    }

    public static void validateIdentity(String serial, String mac) {
        if (serial == null) serial = "";
        if (serial.length() != 12 && serial.length() != 19) {
            throw new IllegalArgumentException("Serial Number harus 12 atau 19 karakter.");
        }
        normalizeMac(mac);
    }

    public static String normalizeMac(String mac) {
        if (mac == null) throw new IllegalArgumentException("MAC Address wajib diisi.");
        String cleaned = mac.trim().replace(":", "").replace("-", "").toLowerCase(Locale.US);
        if (!cleaned.matches("[0-9a-f]{12}")) {
            throw new IllegalArgumentException("MAC Address harus berisi 12 digit hex, contoh AA:BB:CC:00:11:22.");
        }
        return cleaned;
    }

    private static byte[] decryptType6FromLayout(byte[] source, Layout layout, String serial, String mac) throws Exception {
        int pos = layout.payloadDataStart;
        ByteArrayOutputStream encryptedAll = new ByteArrayOutputStream();
        long totalPlain = 0;

        while (true) {
            ensure(source, pos, 12, "mini header AES");
            long plainLength = readU32BE(source, pos);
            long encryptedLength = readU32BE(source, pos + 4);
            long more = readU32BE(source, pos + 8);
            pos += 12;
            if (encryptedLength > Integer.MAX_VALUE) throw new IllegalArgumentException("Chunk terlalu besar.");
            ensure(source, pos, (int) encryptedLength, "payload AES");
            encryptedAll.write(source, pos, (int) encryptedLength);
            pos += (int) encryptedLength;
            totalPlain += plainLength;
            if (more == 0) break;
        }
        if (totalPlain > Integer.MAX_VALUE) throw new IllegalArgumentException("Payload hasil dekripsi terlalu besar.");

        String kp = deriveType6Kp(serial, mac);
        byte[] decrypted = aesCbcDecrypt(encryptedAll.toByteArray(), kp, TYPE6_IV_PREFIX);
        if (totalPlain > decrypted.length) throw new IllegalArgumentException("Panjang plaintext pada header tidak valid.");
        byte[] inner = Arrays.copyOf(decrypted, (int) totalPlain);
        return decompressPayload(inner);
    }

    private static Layout inspect(byte[] source) {
        if (source.length < 60) throw new IllegalArgumentException("File terlalu kecil.");
        int pos = 0;
        boolean header = false;
        boolean little = false;

        if (source.length >= 128 && isOuterMagic(source)) {
            header = true;
            int beField = (int) readU32(source, 16 + (2 * 4), ByteOrder.BIG_ENDIAN);
            int leField = (int) readU32(source, 16 + (2 * 4), ByteOrder.LITTLE_ENDIAN);
            if (leField == 4) little = true;
            else if (beField == 4) little = false;
            else throw new IllegalArgumentException("Outer header ZTE tidak valid.");
            pos = 128;
        }

        String signature = "";
        if (source.length - pos >= 12) {
            int sigMagic = (int) readU32BE(source, pos);
            if (sigMagic == SIGNATURE_MAGIC) {
                long sigLen = readU32BE(source, pos + 8);
                if (sigLen > 4096 || sigLen > Integer.MAX_VALUE) throw new IllegalArgumentException("Signature length tidak valid.");
                ensure(source, pos + 12, (int) sigLen, "signature");
                signature = new String(source, pos + 12, (int) sigLen, StandardCharsets.UTF_8);
                pos += 12 + (int) sigLen;
            }
        }

        int preambleLength = pos;
        ensure(source, pos, 60, "payload header");
        int magic = (int) readU32BE(source, pos);
        if (magic != PAYLOAD_MAGIC) throw new IllegalArgumentException("Payload magic 0x01020304 tidak ditemukan.");
        int payloadType = (int) readU32BE(source, pos + 4);
        return new Layout(signature, payloadType, pos, pos + 60, preambleLength, header, little);
    }

    private static boolean isOuterMagic(byte[] source) {
        for (int i = 0; i < 4; i++) {
            if ((int) readU32BE(source, i * 4) != ZTE_MAGIC[i]) return false;
        }
        return true;
    }

    private static byte[] decompressPayload(byte[] inner) throws Exception {
        ensure(inner, 0, 60, "inner payload header");
        if ((int) readU32BE(inner, 0) != PAYLOAD_MAGIC) {
            throw new IllegalArgumentException("Hasil AES tidak berisi inner payload ZTE. SN/MAC mungkin salah.");
        }
        int innerType = (int) readU32BE(inner, 4);
        if (innerType != 0 && innerType != 1) {
            throw new IllegalArgumentException("Inner payload bukan ZLIB/plain yang didukung (Type " + innerType + ").");
        }

        int pos = 60;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            ensure(inner, pos, 12, "header chunk ZLIB");
            int decompressedLength = checkedInt(readU32BE(inner, pos), "decompressed length");
            int compressedLength = checkedInt(readU32BE(inner, pos + 4), "compressed length");
            long more = readU32BE(inner, pos + 8);
            pos += 12;
            ensure(inner, pos, compressedLength, "chunk ZLIB");
            byte[] compressed = Arrays.copyOfRange(inner, pos, pos + compressedLength);
            pos += compressedLength;
            byte[] plain = inflateZlib(compressed, decompressedLength);
            if (plain.length != decompressedLength) {
                throw new IllegalArgumentException("Panjang hasil ZLIB tidak sesuai header.");
            }
            out.write(plain);
            if (more == 0) break;
        }
        return out.toByteArray();
    }

    private static byte[] buildCompressedPayload(byte[] raw) throws Exception {
        List<byte[]> compressedChunks = new ArrayList<>();
        List<Integer> plainLengths = new ArrayList<>();
        CRC32 crc = new CRC32();

        if (raw.length == 0) {
            compressedChunks.add(deflateZlib(new byte[0]));
            plainLengths.add(0);
            crc.update(compressedChunks.get(0));
        } else {
            for (int pos = 0; pos < raw.length; pos += CHUNK_SIZE) {
                int len = Math.min(CHUNK_SIZE, raw.length - pos);
                byte[] chunk = Arrays.copyOfRange(raw, pos, pos + len);
                byte[] compressed = deflateZlib(chunk);
                compressedChunks.add(compressed);
                plainLengths.add(len);
                crc.update(compressed);
            }
        }

        int compressedSizeMarker = 60;
        for (int i = 0; i < compressedChunks.size() - 1; i++) {
            compressedSizeMarker += 12 + compressedChunks.get(i).length;
        }

        ByteArrayOutputStream first24 = new ByteArrayOutputStream(24);
        writeU32BE(first24, PAYLOAD_MAGIC);
        writeU32BE(first24, 0);
        writeU32BE(first24, raw.length);
        writeU32BE(first24, compressedSizeMarker);
        writeU32BE(first24, CHUNK_SIZE);
        writeU32BE(first24, crc.getValue());
        byte[] header24 = first24.toByteArray();
        CRC32 headerCrc = new CRC32();
        headerCrc.update(header24);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header24);
        writeU32BE(out, headerCrc.getValue());
        for (int i = 0; i < 8; i++) writeU32BE(out, 0);

        int cumulative = 60;
        for (int i = 0; i < compressedChunks.size(); i++) {
            byte[] comp = compressedChunks.get(i);
            boolean last = i == compressedChunks.size() - 1;
            if (!last) cumulative += 12 + comp.length;
            writeU32BE(out, plainLengths.get(i));
            writeU32BE(out, comp.length);
            writeU32BE(out, last ? 0 : cumulative);
            out.write(comp);
        }
        return out.toByteArray();
    }

    private static byte[] aesCbcDecrypt(byte[] encrypted, String keyMaterial, String ivMaterial) throws Exception {
        if ((encrypted.length & 0x0F) != 0) throw new IllegalArgumentException("AES payload tidak aligned 16 byte.");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec key = new SecretKeySpec(sha256(keyMaterial), "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOf(sha256(ivMaterial), 16));
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(encrypted);
    }

    private static byte[] aesCbcEncryptZeroPadded(byte[] plain, String keyMaterial, String ivMaterial) throws Exception {
        int paddedLength = (plain.length + 15) & ~15;
        byte[] padded = Arrays.copyOf(plain, paddedLength);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec key = new SecretKeySpec(sha256(keyMaterial), "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOf(sha256(ivMaterial), 16));
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(padded);
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] deflateZlib(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflateZlib(byte[] compressed, int expectedLength) throws DataFormatException {
        Inflater inflater = new Inflater(false);
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(expectedLength, 64));
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count > 0) {
                out.write(buffer, 0, count);
            } else if (inflater.needsInput() || inflater.needsDictionary()) {
                break;
            }
        }
        inflater.end();
        return out.toByteArray();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static long readU32BE(byte[] data, int offset) {
        return readU32(data, offset, ByteOrder.BIG_ENDIAN);
    }

    private static long readU32(byte[] data, int offset, ByteOrder order) {
        ensure(data, offset, 4, "uint32");
        return Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).order(order).getInt());
    }

    private static void putU32(byte[] data, int offset, long value, ByteOrder order) {
        ensure(data, offset, 4, "uint32 output");
        ByteBuffer.wrap(data, offset, 4).order(order).putInt((int) value);
    }

    private static void writeU32BE(ByteArrayOutputStream out, long value) {
        out.write((byte) ((value >>> 24) & 0xFF));
        out.write((byte) ((value >>> 16) & 0xFF));
        out.write((byte) ((value >>> 8) & 0xFF));
        out.write((byte) (value & 0xFF));
    }

    private static int checkedInt(long value, String name) {
        if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " tidak valid.");
        return (int) value;
    }

    private static void ensure(byte[] data, int offset, int length, String what) {
        if (offset < 0 || length < 0 || offset > data.length || data.length - offset < length) {
            throw new IllegalArgumentException("Data tidak cukup untuk " + what + ".");
        }
    }
}
