/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines channels, which represent connections to entities that are capable of
 * performing I/O operations.
 *
 * <a name="channels"></a>
 *
 * <blockquote><table cellspacing=1 cellpadding=0 summary="Lists channels and their descriptions">
 * <tr><th align="left">Channels</th><th align="left">Description</th></tr>
 * <tr><td valign=top><tt><i>{@link java.nio.channels.Channel}</i></tt></td>
 *     <td>A nexus for I/O operations</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.ReadableByteChannel}</i></tt></td>
 *     <td>Can read into a buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.ScatteringByteChannel}&nbsp;&nbsp;</i></tt></td>
 *     <td>Can read into a sequence of&nbsp;buffers</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.WritableByteChannel}</i></tt></td>
 *     <td>Can write from a buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.GatheringByteChannel}</i></tt></td>
 *     <td>Can write from a sequence of&nbsp;buffers</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;<i>{@link java.nio.channels.ByteChannel}</i></tt></td>
 *     <td>Can read/write to/from a&nbsp;buffer</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.SeekableByteChannel}</i></tt></td>
 *     <td>Supports asynchronous I/O operations.</td></tr>
 * <tr><td valign=top><tt>&nbsp;&nbsp;&nbsp;&nbsp;<i>{@link java.nio.channels.AsynchronousByteChannel}</i></tt></td>
 * <tr><td valign=top><tt>{@link java.nio.channels.Channels}</tt></td>
 *     <td>Utility methods for channel/stream interoperation</td></tr>
 * </table></blockquote>
 *
 * <p> A <i>channel</i> represents an open connection to an entity such as a
 * program component that is
 * capable of performing one or more distinct I/O operations, for example reading
 * or writing.  As specified in the {@link java.nio.channels.Channel} interface,
 * channels are either open or closed, and they are both <i>asynchronously
 * closeable</i> and <i>interruptible</i>.
 *
 * <p> The {@link java.nio.channels.Channel} interface is extended by several
 * other interfaces.
 *
 * <p> The {@link java.nio.channels.ReadableByteChannel} interface specifies a
 * {@link java.nio.channels.ReadableByteChannel#read read} method that reads bytes
 * from the channel into a buffer; similarly, the {@link
 * java.nio.channels.WritableByteChannel} interface specifies a {@link
 * java.nio.channels.WritableByteChannel#write write} method that writes bytes
 * from a buffer to the channel. The {@link java.nio.channels.ByteChannel}
 * interface unifies these two interfaces for the common case of channels that can
 * both read and write bytes. The {@link java.nio.channels.SeekableByteChannel}
 * interface extends the {@code ByteChannel} interface with methods to {@link
 * java.nio.channels.SeekableByteChannel#position() query} and {@link
 * java.nio.channels.SeekableByteChannel#position(long) modify} the channel's
 * current position, and its {@link java.nio.channels.SeekableByteChannel#size
 * size}.
 *
 * <p> The {@link java.nio.channels.ScatteringByteChannel} and {@link
 * java.nio.channels.GatheringByteChannel} interfaces extend the {@link
 * java.nio.channels.ReadableByteChannel} and {@link
 * java.nio.channels.WritableByteChannel} interfaces, respectively, adding {@link
 * java.nio.channels.ScatteringByteChannel#read read} and {@link
 * java.nio.channels.GatheringByteChannel#write write} methods that take a
 * sequence of buffers rather than a single buffer.
 *
 * <p> The {@link java.nio.channels.Channels} utility class defines static methods
 * that support the interoperation of the stream classes of the <tt>{@link
 * java.io}</tt> package with the channel classes of this package.  An appropriate
 * channel can be constructed from an {@link java.io.InputStream} or an {@link
 * java.io.OutputStream}, and conversely an {@link java.io.InputStream} or an
 * {@link java.io.OutputStream} can be constructed from a channel.  A {@link
 * java.io.Reader} can be constructed that uses a given charset to decode bytes
 * from a given readable byte channel, and conversely a {@link java.io.Writer} can
 * be constructed that uses a given charset to encode characters into bytes and
 * write them to a given writable byte channel.
 *
 * @since 1.4
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 */

package java.nio.channels;
