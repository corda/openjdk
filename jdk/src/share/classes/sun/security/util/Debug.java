/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.math.BigInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Locale;

/**
 * A utility class for debuging.
 *
 * @author Roland Schemers
 */
public class Debug {

    /**
     * return a hexadecimal printed representation of the specified
     * BigInteger object. the value is formatted to fit on lines of
     * at least 75 characters, with embedded newlines. Words are
     * separated for readability, with eight words (32 bytes) per line.
     */
    public static String toHexString(BigInteger b) {
        String hexValue = b.toString(16);
        StringBuffer buf = new StringBuffer(hexValue.length()*2);

        if (hexValue.startsWith("-")) {
            buf.append("   -");
            hexValue = hexValue.substring(1);
        } else {
            buf.append("    ");     // four spaces
        }
        if ((hexValue.length()%2) != 0) {
            // add back the leading 0
            hexValue = "0" + hexValue;
        }
        int i=0;
        while (i < hexValue.length()) {
            // one byte at a time
            buf.append(hexValue.substring(i, i+2));
            i+=2;
            if (i!= hexValue.length()) {
                if ((i%64) == 0) {
                    buf.append("\n    ");     // line after eight words
                } else if (i%8 == 0) {
                    buf.append(" ");     // space between words
                }
            }
        }
        return buf.toString();
    }

    private final static char[] hexDigits = "0123456789abcdef".toCharArray();

    public static String toString(byte[] b) {
        if (b == null) {
            return "(null)";
        }
        StringBuilder sb = new StringBuilder(b.length * 3);
        for (int i = 0; i < b.length; i++) {
            int k = b[i] & 0xff;
            if (i != 0) {
                sb.append(':');
            }
            sb.append(hexDigits[k >>> 4]);
            sb.append(hexDigits[k & 0xf]);
        }
        return sb.toString();
    }

}
