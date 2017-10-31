/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import sun.misc.Unsafe;
import sun.misc.Cleaner;
import sun.security.action.GetPropertyAction;


public class Util {

    // -- Caches --

    // The max size allowed for a cached temp buffer, in bytes
    private static final long MAX_CACHED_BUFFER_SIZE = getMaxCachedBufferSize();

    /**
     * Returns the max size allowed for a cached temp buffers, in
     * bytes. It defaults to Long.MAX_VALUE. It can be set with the
     * jdk.nio.maxCachedBufferSize property. Even though
     * ByteBuffer.capacity() returns an int, we're using a long here
     * for potential future-proofing.
     */
    private static long getMaxCachedBufferSize() {
        String s = java.security.AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty("jdk.nio.maxCachedBufferSize");
                }
            });
        if (s != null) {
            try {
                long m = Long.parseLong(s);
                if (m >= 0) {
                    return m;
                } else {
                    // if it's negative, ignore the system property
                }
            } catch (NumberFormatException e) {
                // if the string is not well formed, ignore the system property
            }
        }
        return Long.MAX_VALUE;
    }

    /**
     * Returns true if a buffer of this size is too large to be
     * added to the buffer cache, false otherwise.
     */
    private static boolean isBufferTooLarge(int size) {
        return size > MAX_CACHED_BUFFER_SIZE;
    }

    /**
     * Returns true if the buffer is too large to be added to the
     * buffer cache, false otherwise.
     */
    private static boolean isBufferTooLarge(ByteBuffer buf) {
        return isBufferTooLarge(buf.capacity());
    }

    // -- Random stuff --

    static ByteBuffer[] subsequence(ByteBuffer[] bs, int offset, int length) {
        if ((offset == 0) && (length == bs.length))
            return bs;
        int n = length;
        ByteBuffer[] bs2 = new ByteBuffer[n];
        for (int i = 0; i < n; i++)
            bs2[i] = bs[offset + i];
        return bs2;
    }

    static <E> Set<E> ungrowableSet(final Set<E> s) {
        return new Set<E>() {

                public int size()                 { return s.size(); }
                public boolean isEmpty()          { return s.isEmpty(); }
                public boolean contains(Object o) { return s.contains(o); }
                public Object[] toArray()         { return s.toArray(); }
                public <T> T[] toArray(T[] a)     { return s.toArray(a); }
                public String toString()          { return s.toString(); }
                public Iterator<E> iterator()     { return s.iterator(); }
                public boolean equals(Object o)   { return s.equals(o); }
                public int hashCode()             { return s.hashCode(); }
                public void clear()               { s.clear(); }
                public boolean remove(Object o)   { return s.remove(o); }

                public boolean containsAll(Collection<?> coll) {
                    return s.containsAll(coll);
                }
                public boolean removeAll(Collection<?> coll) {
                    return s.removeAll(coll);
                }
                public boolean retainAll(Collection<?> coll) {
                    return s.retainAll(coll);
                }

                public boolean add(E o){
                    throw new UnsupportedOperationException();
                }
                public boolean addAll(Collection<? extends E> coll) {
                    throw new UnsupportedOperationException();
                }

        };
    }


    // -- Unsafe access --

    private static Unsafe unsafe = Unsafe.getUnsafe();

    private static byte _get(long a) {
        return unsafe.getByte(a);
    }

    private static void _put(long a, byte b) {
        unsafe.putByte(a, b);
    }

    static void erase(ByteBuffer bb) {
        unsafe.setMemory(((DirectBuffer)bb).address(), bb.capacity(), (byte)0);
    }

    static Unsafe unsafe() {
        return unsafe;
    }

    private static int pageSize = -1;

    static int pageSize() {
        if (pageSize == -1)
            pageSize = unsafe().pageSize();
        return pageSize;
    }

    private static volatile Constructor<?> directByteBufferConstructor = null;

    private static void initDBBConstructor() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        Class<?> cl = Class.forName("java.nio.DirectByteBuffer");
                        Constructor<?> ctor = cl.getDeclaredConstructor(
                            new Class<?>[] { int.class,
                                             long.class,
                                             FileDescriptor.class,
                                             Runnable.class });
                        ctor.setAccessible(true);
                        directByteBufferConstructor = ctor;
                    } catch (ClassNotFoundException   |
                             NoSuchMethodException    |
                             IllegalArgumentException |
                             ClassCastException x) {
                        throw new InternalError(x);
                    }
                    return null;
                }});
    }

    private static volatile Constructor<?> directByteBufferRConstructor = null;

    private static void initDBBRConstructor() {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        Class<?> cl = Class.forName("java.nio.DirectByteBufferR");
                        Constructor<?> ctor = cl.getDeclaredConstructor(
                            new Class<?>[] { int.class,
                                             long.class,
                                             FileDescriptor.class,
                                             Runnable.class });
                        ctor.setAccessible(true);
                        directByteBufferRConstructor = ctor;
                    } catch (ClassNotFoundException |
                             NoSuchMethodException |
                             IllegalArgumentException |
                             ClassCastException x) {
                        throw new InternalError(x);
                    }
                    return null;
                }});
    }

    // -- Bug compatibility --

    private static volatile String bugLevel = null;

    static boolean atBugLevel(String bl) {              // package-private
        if (bugLevel == null) {
            if (!sun.misc.VM.isBooted())
                return false;
            String value = AccessController.doPrivileged(
                new GetPropertyAction("sun.nio.ch.bugLevel"));
            bugLevel = (value != null) ? value : "";
        }
        return bugLevel.equals(bl);
    }

}
