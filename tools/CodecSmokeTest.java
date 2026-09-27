import com.sione.ztetype6.XmlConfigTools;
import com.sione.ztetype6.crypto.ZteType6Codec;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Lightweight CI smoke test; no Android SDK classes required. */
public final class CodecSmokeTest {
    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((byte) (value >>> 24));
        out.write((byte) (value >>> 16));
        out.write((byte) (value >>> 8));
        out.write((byte) value);
    }

    private static byte[] syntheticType6Template() throws Exception {
        byte[] outer = new byte[128];
        ByteBuffer b = ByteBuffer.wrap(outer).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0, 0x99999999);
        b.putInt(4, 0x44444444);
        b.putInt(8, 0x55555555);
        b.putInt(12, 0xAAAAAAAA);
        b.putInt(24, 4);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(outer);
        writeU32(out, 0x01020304);
        writeU32(out, 6);
        for (int i = 0; i < 13; i++) writeU32(out, 0);
        writeU32(out, 0);
        writeU32(out, 0);
        writeU32(out, 0);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        String xml = "<?xml version=\"1.0\"?><Config>"
                + "<Tbl name=\"PPPIF\" RowCount=\"1\"><Row No=\"0\">"
                + "<DM name=\"Username\" val=\"sample\"/><DM name=\"Password\" val=\"samplepass\"/>"
                + "</Row></Tbl>"
                + "<Tbl name=\"DevAuthInfo\" RowCount=\"2\">"
                + "<Row No=\"0\"><DM name=\"Enable\" val=\"1\"/><DM name=\"User\" val=\"admin\"/><DM name=\"Pass\" val=\"secret\"/></Row>"
                + "<Row No=\"1\"><DM name=\"Enable\" val=\"0\"/><DM name=\"User\" val=\"disabled\"/></Row>"
                + "</Tbl></Config>";

        XmlConfigTools.validateWellFormed(xml);
        if (!XmlConfigTools.extractPppoe(xml).contains("Username")) {
            throw new AssertionError("PPPIF extraction failed");
        }
        String auth = XmlConfigTools.extractActiveDevAuthInfo(xml);
        if (!auth.contains("secret") || auth.contains("disabled")) {
            throw new AssertionError("DevAuthInfo active-row extraction failed");
        }
        if (XmlConfigTools.find(xml, "DevAuthInfo", 0, true).start() < 0) {
            throw new AssertionError("XML search failed");
        }

        byte[] original = xml.getBytes(StandardCharsets.UTF_8);
        String serial = "ZTEG12345678";
        String mac = "AA:BB:CC:00:11:22";
        ZteType6Codec.EncodeResult encoded = ZteType6Codec.encrypt(
                original, syntheticType6Template(), serial, mac);
        byte[] decoded = ZteType6Codec.decrypt(encoded.bin(), serial, mac).xml();
        if (!Arrays.equals(original, decoded)) {
            throw new AssertionError("Type-6 round-trip mismatch");
        }
        System.out.println("Smoke test OK: XML tools + Type-6 round-trip");
    }
}
