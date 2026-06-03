package security;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tier-1 unit tests for {@link Signature} (static, pure HMAC-SHA1). No Play app or DB.
 *
 * The pinned base64 values were computed independently (HMAC-SHA1 + base64 of the same
 * inputs) and guard against accidental algorithm/encoding changes.
 */
public class SignatureTest {

    @Test
    public void getSignatureIsStableForKnownInput() throws Exception {
        String sig = Signature.getSignature(
            "AWSECommerceService", "GetItems", "2009-01-01T12:00:00Z", "1234567890");
        assertEquals("dO/jwM/JPGeMydTIbY/DNwtl+8I=", sig);
    }

    @Test
    public void getSignatureEqualsHmacOfConcatenation() throws Exception {
        String viaGet = Signature.getSignature(
            "AWSECommerceService", "GetItems", "2009-01-01T12:00:00Z", "1234567890");
        String viaHmac = Signature.calculateRFC2104HMAC(
            "AWSECommerceServiceGetItems2009-01-01T12:00:00Z", "1234567890");
        assertEquals("getSignature must equal HMAC of service+operation+timestamp",
            viaHmac, viaGet);
    }

    @Test
    public void calculateRfc2104HmacKnownAnswer() throws Exception {
        assertEquals("URIFXAX5RPhXVe/FzYlw4ZTp9Fs=",
            Signature.calculateRFC2104HMAC("hello", "secret"));
    }

    @Test
    public void differentKeysProduceDifferentSignatures() throws Exception {
        String a = Signature.calculateRFC2104HMAC("data", "key1");
        String b = Signature.calculateRFC2104HMAC("data", "key2");
        assertNotEquals("Distinct keys must yield distinct signatures", a, b);
    }

    @Test(expected = java.security.SignatureException.class)
    public void wrapsFailuresAsSignatureException() throws Exception {
        // null key -> NPE inside -> wrapped and rethrown as SignatureException
        Signature.calculateRFC2104HMAC("data", null);
    }
}
