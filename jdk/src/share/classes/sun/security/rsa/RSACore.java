/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.rsa;

import java.math.BigInteger;
import java.util.*;

import java.security.SecureRandom;
import java.security.interfaces.*;

import javax.crypto.BadPaddingException;

import sun.security.jca.JCAUtil;

/**
 * Core of the RSA implementation. Has code to perform public and private key
 * RSA operations (with and without CRT for private key ops). Private CRT ops
 * also support blinding to twart timing attacks.
 *
 * The code in this class only does the core RSA operation. Padding and
 * unpadding must be done externally.
 *
 * Note: RSA keys should be at least 512 bits long
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSACore {

    private RSACore() {
        // empty
    }

    /**
     * Return the number of bytes required to store the magnitude byte[] of
     * this BigInteger. Do not count a 0x00 byte toByteArray() would
     * prefix for 2's complement form.
     */
    public static int getByteLength(BigInteger b) {
        int n = b.bitLength();
        return (n + 7) >> 3;
    }

    /**
     * Return the number of bytes required to store the modulus of this
     * RSA key.
     */
    public static int getByteLength(RSAKey key) {
        return getByteLength(key.getModulus());
    }

    // temporary, used by RSACipher and RSAPadding. Move this somewhere else
    public static byte[] convert(byte[] b, int ofs, int len) {
        if ((ofs == 0) && (len == b.length)) {
            return b;
        } else {
            byte[] t = new byte[len];
            System.arraycopy(b, ofs, t, 0, len);
            return t;
        }
    }

    /**
     * Perform an RSA public key operation.
     */
    public static byte[] rsa(byte[] msg, RSAPublicKey key)
            throws BadPaddingException {
        return crypt(msg, key.getModulus(), key.getPublicExponent());
    }

    /**
     * Perform an RSA private key operation. Uses CRT if the key is a
     * CRT key with additional verification check after the signature
     * is computed.
     */
    @Deprecated
    public static byte[] rsa(byte[] msg, RSAPrivateKey key)
            throws BadPaddingException {
        return rsa(msg, key, true);
    }

    /**
     * Perform an RSA private key operation. Uses CRT if the key is a
     * CRT key. Set 'verify' to true if this function is used for
     * generating a signature.
     */
    public static byte[] rsa(byte[] msg, RSAPrivateKey key, boolean verify)
            throws BadPaddingException {
        if (key instanceof RSAPrivateCrtKey) {
            return crtCrypt(msg, (RSAPrivateCrtKey)key, verify);
        } else {
            return priCrypt(msg, key.getModulus(), key.getPrivateExponent());
        }
    }

    /**
     * RSA public key ops. Simple modPow().
     */
    private static byte[] crypt(byte[] msg, BigInteger n, BigInteger exp)
            throws BadPaddingException {
        BigInteger m = parseMsg(msg, n);
        BigInteger c = m.modPow(exp, n);
        return toByteArray(c, getByteLength(n));
    }

    /**
     * RSA non-CRT private key operations.
     */
    private static byte[] priCrypt(byte[] msg, BigInteger n, BigInteger exp)
            throws BadPaddingException {

        BigInteger c = parseMsg(msg, n);
        BigInteger m = c.modPow(exp, n);
        return toByteArray(m, getByteLength(n));
    }

    /**
     * RSA private key operations with CRT. Algorithm and variable naming
     * are taken from PKCS#1 v2.1, section 5.1.2.
     */
    private static byte[] crtCrypt(byte[] msg, RSAPrivateCrtKey key,
            boolean verify) throws BadPaddingException {
        BigInteger n = key.getModulus();
        BigInteger c0 = parseMsg(msg, n);
        BigInteger c = c0;
        BigInteger p = key.getPrimeP();
        BigInteger q = key.getPrimeQ();
        BigInteger dP = key.getPrimeExponentP();
        BigInteger dQ = key.getPrimeExponentQ();
        BigInteger qInv = key.getCrtCoefficient();
        BigInteger e = key.getPublicExponent();
        BigInteger d = key.getPrivateExponent();

        // m1 = c ^ dP mod p
        BigInteger m1 = c.modPow(dP, p);
        // m2 = c ^ dQ mod q
        BigInteger m2 = c.modPow(dQ, q);

        // h = (m1 - m2) * qInv mod p
        BigInteger mtmp = m1.subtract(m2);
        if (mtmp.signum() < 0) {
            mtmp = mtmp.add(p);
        }
        BigInteger h = mtmp.multiply(qInv).mod(p);

        // m = m2 + q * h
        BigInteger m = h.multiply(q).add(m2);

        if (verify && !c0.equals(m.modPow(e, n))) {
            throw new BadPaddingException("RSA private key operation failed");
        }

        return toByteArray(m, getByteLength(n));
    }

    /**
     * Parse the msg into a BigInteger and check against the modulus n.
     */
    private static BigInteger parseMsg(byte[] msg, BigInteger n)
            throws BadPaddingException {
        BigInteger m = new BigInteger(1, msg);
        if (m.compareTo(n) >= 0) {
            throw new BadPaddingException("Message is larger than modulus");
        }
        return m;
    }

    /**
     * Return the encoding of this BigInteger that is exactly len bytes long.
     * Prefix/strip off leading 0x00 bytes if necessary.
     * Precondition: bi must fit into len bytes
     */
    private static byte[] toByteArray(BigInteger bi, int len) {
        byte[] b = bi.toByteArray();
        int n = b.length;
        if (n == len) {
            return b;
        }
        // BigInteger prefixed a 0x00 byte for 2's complement form, remove it
        if ((n == len + 1) && (b[0] == 0)) {
            byte[] t = new byte[len];
            System.arraycopy(b, 1, t, 0, len);
            return t;
        }
        // must be smaller
        assert (n < len);
        byte[] t = new byte[len];
        System.arraycopy(b, 0, t, (len - n), n);
        return t;
    }

    /**
     * Parameters (u,v) for RSA Blinding.  This is described in the RSA
     * Bulletin#2 (Jan 96) and other places:
     *
     *     ftp://ftp.rsa.com/pub/pdfs/bull-2.pdf
     *
     * The standard RSA Blinding decryption requires the public key exponent
     * (e) and modulus (n), and converts ciphertext (c) to plaintext (p).
     *
     * Before the modular exponentiation operation, the input message should
     * be multiplied by (u (mod n)), and afterward the result is corrected
     * by multiplying with (v (mod n)).  The system should reject messages
     * equal to (0 (mod n)).  That is:
     *
     *     1.  Generate r between 0 and n-1, relatively prime to n.
     *     2.  Compute x = (c*u) mod n
     *     3.  Compute y = (x^d) mod n
     *     4.  Compute p = (y*v) mod n
     *
     * The Java APIs allows for either standard RSAPrivateKey or
     * RSAPrivateCrtKey RSA keys.
     *
     * If the public exponent is available to us (e.g. RSAPrivateCrtKey),
     * choose a random r, then let (u, v):
     *
     *     u = r ^ e mod n
     *     v = r ^ (-1) mod n
     *
     * The proof follows:
     *
     *     p = (((c * u) ^ d mod n) * v) mod n
     *       = ((c ^ d) * (u ^ d) * v) mod n
     *       = ((c ^ d) * (r ^ e) ^ d) * (r ^ (-1))) mod n
     *       = ((c ^ d) * (r ^ (e * d)) * (r ^ (-1))) mod n
     *       = ((c ^ d) * (r ^ 1) * (r ^ (-1))) mod n  (see below)
     *       = (c ^ d) mod n
     *
     * because in RSA cryptosystem, d is the multiplicative inverse of e:
     *
     *    (r^(e * d)) mod n
     *       = (r ^ 1) mod n
     *       = r mod n
     *
     * However, if the public exponent is not available (e.g. RSAPrivateKey),
     * we mitigate the timing issue by using a similar random number blinding
     * approach using the private key:
     *
     *     u = r
     *     v = ((r ^ (-1)) ^ d) mod n
     *
     * This returns the same plaintext because:
     *
     *     p = (((c * u) ^ d mod n) * v) mod n
     *       = ((c ^ d) * (u ^ d) * v) mod n
     *       = ((c ^ d) * (u ^ d) * ((u ^ (-1)) ^d)) mod n
     *       = (c ^ d) mod n
     *
     * Computing inverses mod n and random number generation is slow, so
     * it is often not practical to generate a new random (u, v) pair for
     * each new exponentiation.  The calculation of parameters might even be
     * subject to timing attacks.  However, (u, v) pairs should not be
     * reused since they themselves might be compromised by timing attacks,
     * leaving the private exponent vulnerable.  An efficient solution to
     * this problem is update u and v before each modular exponentiation
     * step by computing:
     *
     *     u = u ^ 2
     *     v = v ^ 2
     *
     * The total performance cost is small.
     */
    private final static class BlindingRandomPair {
        final BigInteger u;
        final BigInteger v;

        BlindingRandomPair(BigInteger u, BigInteger v) {
            this.u = u;
            this.v = v;
        }
    }

}
