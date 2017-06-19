/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.security.AccessController;
import java.io.IOException;
import sun.security.action.*;
import sun.net.InetAddressCachePolicy;
import sun.net.util.IPAddressUtil;

/**
 * This class represents an Internet Protocol (IP) address.
 *
 * <p> An IP address is either a 32-bit or 128-bit unsigned number
 * used by IP, a lower-level protocol on which protocols like UDP and
 * TCP are built. The IP address architecture is defined by <a
 * href="http://www.ietf.org/rfc/rfc790.txt"><i>RFC&nbsp;790:
 * Assigned Numbers</i></a>, <a
 * href="http://www.ietf.org/rfc/rfc1918.txt"> <i>RFC&nbsp;1918:
 * Address Allocation for Private Internets</i></a>, <a
 * href="http://www.ietf.org/rfc/rfc2365.txt"><i>RFC&nbsp;2365:
 * Administratively Scoped IP Multicast</i></a>, and <a
 * href="http://www.ietf.org/rfc/rfc2373.txt"><i>RFC&nbsp;2373: IP
 * Version 6 Addressing Architecture</i></a>. An instance of an
 * InetAddress consists of an IP address and possibly its
 * corresponding host name (depending on whether it is constructed
 * with a host name or whether it has already done reverse host name
 * resolution).
 *
 * <h3> Address types </h3>
 *
 * <blockquote><table cellspacing=2 summary="Description of unicast and multicast address types">
 *   <tr><th valign=top><i>unicast</i></th>
 *       <td>An identifier for a single interface. A packet sent to
 *         a unicast address is delivered to the interface identified by
 *         that address.
 *
 *         <p> The Unspecified Address -- Also called anylocal or wildcard
 *         address. It must never be assigned to any node. It indicates the
 *         absence of an address. One example of its use is as the target of
 *         bind, which allows a server to accept a client connection on any
 *         interface, in case the server host has multiple interfaces.
 *
 *         <p> The <i>unspecified</i> address must not be used as
 *         the destination address of an IP packet.
 *
 *         <p> The <i>Loopback</i> Addresses -- This is the address
 *         assigned to the loopback interface. Anything sent to this
 *         IP address loops around and becomes IP input on the local
 *         host. This address is often used when testing a
 *         client.</td></tr>
 *   <tr><th valign=top><i>multicast</i></th>
 *       <td>An identifier for a set of interfaces (typically belonging
 *         to different nodes). A packet sent to a multicast address is
 *         delivered to all interfaces identified by that address.</td></tr>
 * </table></blockquote>
 *
 * <h4> IP address scope </h4>
 *
 * <p> <i>Link-local</i> addresses are designed to be used for addressing
 * on a single link for purposes such as auto-address configuration,
 * neighbor discovery, or when no routers are present.
 *
 * <p> <i>Site-local</i> addresses are designed to be used for addressing
 * inside of a site without the need for a global prefix.
 *
 * <p> <i>Global</i> addresses are unique across the internet.
 *
 * <h4> Textual representation of IP addresses </h4>
 *
 * The textual representation of an IP address is address family specific.
 *
 * <p>
 *
 * For IPv4 address format, please refer to <A
 * HREF="Inet4Address.html#format">Inet4Address#format</A>; For IPv6
 * address format, please refer to <A
 * HREF="Inet6Address.html#format">Inet6Address#format</A>.
 *
 * <P>There is a <a href="doc-files/net-properties.html#Ipv4IPv6">couple of
 * System Properties</a> affecting how IPv4 and IPv6 addresses are used.</P>
 *
 * <h4> Host Name Resolution </h4>
 *
 * Host name-to-IP address <i>resolution</i> is accomplished through
 * the use of a combination of local machine configuration information
 * and network naming services such as the Domain Name System (DNS)
 * and Network Information Service(NIS). The particular naming
 * services(s) being used is by default the local machine configured
 * one. For any host name, its corresponding IP address is returned.
 *
 * <p> <i>Reverse name resolution</i> means that for any IP address,
 * the host associated with the IP address is returned.
 *
 * <p> The InetAddress class provides methods to resolve host names to
 * their IP addresses and vice versa.
 *
 * <h4> InetAddress Caching </h4>
 *
 * The InetAddress class has a cache to store successful as well as
 * unsuccessful host name resolutions.
 *
 * <p> By default, when a security manager is installed, in order to
 * protect against DNS spoofing attacks,
 * the result of positive host name resolutions are
 * cached forever. When a security manager is not installed, the default
 * behavior is to cache entries for a finite (implementation dependent)
 * period of time. The result of unsuccessful host
 * name resolution is cached for a very short period of time (10
 * seconds) to improve performance.
 *
 * <p> If the default behavior is not desired, then a Java security property
 * can be set to a different Time-to-live (TTL) value for positive
 * caching. Likewise, a system admin can configure a different
 * negative caching TTL value when needed.
 *
 * <p> Two Java security properties control the TTL values used for
 *  positive and negative host name resolution caching:
 *
 * <blockquote>
 * <dl>
 * <dt><b>networkaddress.cache.ttl</b></dt>
 * <dd>Indicates the caching policy for successful name lookups from
 * the name service. The value is specified as as integer to indicate
 * the number of seconds to cache the successful lookup. The default
 * setting is to cache for an implementation specific period of time.
 * <p>
 * A value of -1 indicates "cache forever".
 * </dd>
 * <dt><b>networkaddress.cache.negative.ttl</b> (default: 10)</dt>
 * <dd>Indicates the caching policy for un-successful name lookups
 * from the name service. The value is specified as as integer to
 * indicate the number of seconds to cache the failure for
 * un-successful lookups.
 * <p>
 * A value of 0 indicates "never cache".
 * A value of -1 indicates "cache forever".
 * </dd>
 * </dl>
 * </blockquote>
 *
 * @author  Chris Warth
 * @see     java.net.InetAddress#getByAddress(byte[])
 * @see     java.net.InetAddress#getByAddress(java.lang.String, byte[])
 * @see     java.net.InetAddress#getLocalHost()
 * @since JDK1.0
 */
public
class InetAddress implements java.io.Serializable {
    /**
     * Specify the address family: Internet Protocol, Version 4
     * @since 1.4
     */
    static final int IPv4 = 1;

    /**
     * Specify the address family: Internet Protocol, Version 6
     * @since 1.4
     */
    static final int IPv6 = 2;

    /* Specify address family preference */
    static transient boolean preferIPv6Address = false;

    static class InetAddressHolder {
        /**
         * Reserve the original application specified hostname.
         *
         * The original hostname is useful for domain-based endpoint
         * identification (see RFC 2818 and RFC 6125).  If an address
         * was created with a raw IP address, a reverse name lookup
         * may introduce endpoint identification security issue via
         * DNS forging.
         *
         * Oracle JSSE provider is using this original hostname, via
         * sun.misc.JavaNetAccess, for SSL/TLS endpoint identification.
         *
         * Note: May define a new public method in the future if necessary.
         */
        String originalHostName;

        InetAddressHolder() {}

        InetAddressHolder(String hostName, int address, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            this.address = address;
            this.family = family;
        }

        void init(String hostName, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            if (family != -1) {
                this.family = family;
            }
        }

        String hostName;

        String getHostName() {
            return hostName;
        }

        String getOriginalHostName() {
            return originalHostName;
        }

        /**
         * Holds a 32-bit IPv4 address.
         */
        int address;

        int getAddress() {
            return address;
        }

        /**
         * Specifies the address family type, for instance, '1' for IPv4
         * addresses, and '2' for IPv6 addresses.
         */
        int family;

        int getFamily() {
            return family;
        }
    }

    /* Used to store the serializable fields of InetAddress */
    final transient InetAddressHolder holder;

    InetAddressHolder holder() {
        return holder;
    }

    /* Used to store the best available hostname */
    private transient String canonicalHostName = null;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 3286316764910316507L;

    /*
     * Load net library into runtime, and perform initializations.
     */
    static {
        preferIPv6Address = java.security.AccessController.doPrivileged(
            new GetBooleanAction("java.net.preferIPv6Addresses")).booleanValue();
        AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("net");
                    return null;
                }
            });
        init();
    }

    /**
     * Constructor for the Socket.accept() method.
     * This creates an empty InetAddress, which is filled in by
     * the accept() method.  This InetAddress, however, is not
     * put in the address cache, since it is not created by name.
     */
    InetAddress() {
        holder = new InetAddressHolder();
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IP multicast address.
     * @return a {@code boolean} indicating if the InetAddress is
     * an IP multicast address
     * @since   JDK1.1
     */
    public boolean isMulticastAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress in a wildcard address.
     * @return a {@code boolean} indicating if the Inetaddress is
     *         a wildcard address.
     * @since 1.4
     */
    public boolean isAnyLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a loopback address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a loopback address; or false otherwise.
     * @since 1.4
     */
    public boolean isLoopbackAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is an link local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a link local address; or false if address is not a link local unicast address.
     * @since 1.4
     */
    public boolean isLinkLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a site local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a site local address; or false if address is not a site local unicast address.
     * @since 1.4
     */
    public boolean isSiteLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has global scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of global scope, false if it is not
     *         of global scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCGlobal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has node scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of node-local scope, false if it is not
     *         of node-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCNodeLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has link scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of link-local scope, false if it is not
     *         of link-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCLinkLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has site scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of site-local scope, false if it is not
     *         of site-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCSiteLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has organization scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of organization-local scope,
     *         false if it is not of organization-local scope
     *         or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCOrgLocal() {
        return false;
    }

    /**
     * Returns the raw IP address of this {@code InetAddress}
     * object. The result is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * @return  the raw IP address of this object.
     */
    public byte[] getAddress() {
        return null;
    }

    /**
     * Returns the IP address string in textual presentation.
     *
     * @return  the raw IP address in a string format.
     * @since   JDK1.0.2
     */
    public String getHostAddress() {
        return null;
     }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    public int hashCode() {
        return -1;
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if the argument is
     * not {@code null} and it represents the same IP address as
     * this object.
     * <p>
     * Two instances of {@code InetAddress} represent the same IP
     * address if the length of the byte arrays returned by
     * {@code getAddress} is the same for both, and each of the
     * array components is the same for the byte arrays.
     *
     * @param   obj   the object to compare against.
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     * @see     java.net.InetAddress#getAddress()
     */
    public boolean equals(Object obj) {
        return false;
    }

    /**
     * Converts this IP address to a {@code String}. The
     * string returned is of the form: hostname / literal IP
     * address.
     *
     * If the host name is unresolved, no reverse name service lookup
     * is performed. The hostname part will be represented by an empty string.
     *
     * @return  a string representation of this IP address.
     */
    public String toString() {
        String hostName = holder().getHostName();
        return ((hostName != null) ? hostName : "")
            + "/" + getHostAddress();
    }

    static InetAddress[]    unknown_array; // put THIS in cache

    static InetAddressImpl  impl;

    private static final HashMap<String, Void> lookupTable = new HashMap<>();

    /**
     * Represents a cache entry
     */
    static final class CacheEntry {

        CacheEntry(InetAddress[] addresses, long expiration) {
            this.addresses = addresses;
            this.expiration = expiration;
        }

        InetAddress[] addresses;
        long expiration;
    }

    /**
     * A cache that manages entries based on a policy specified
     * at creation time.
     */
    static final class Cache {
        private LinkedHashMap<String, CacheEntry> cache;
        private Type type;

        enum Type {Positive, Negative};

        /**
         * Create cache
         */
        public Cache(Type type) {
            this.type = type;
            cache = new LinkedHashMap<String, CacheEntry>();
        }

        private int getPolicy() {
            if (type == Type.Positive) {
                return InetAddressCachePolicy.get();
            } else {
                return InetAddressCachePolicy.getNegative();
            }
        }

        /**
         * Add an entry to the cache. If there's already an
         * entry then for this host then the entry will be
         * replaced.
         */
        public Cache put(String host, InetAddress[] addresses) {
            int policy = getPolicy();
            if (policy == InetAddressCachePolicy.NEVER) {
                return this;
            }

            // purge any expired entries

            if (policy != InetAddressCachePolicy.FOREVER) {

                // As we iterate in insertion order we can
                // terminate when a non-expired entry is found.
                LinkedList<String> expired = new LinkedList<>();
                long now = System.currentTimeMillis();
                for (String key : cache.keySet()) {
                    CacheEntry entry = cache.get(key);

                    if (entry.expiration >= 0 && entry.expiration < now) {
                        expired.add(key);
                    } else {
                        break;
                    }
                }

                for (String key : expired) {
                    cache.remove(key);
                }
            }

            // create new entry and add it to the cache
            // -- as a HashMap replaces existing entries we
            //    don't need to explicitly check if there is
            //    already an entry for this host.
            long expiration;
            if (policy == InetAddressCachePolicy.FOREVER) {
                expiration = -1;
            } else {
                expiration = System.currentTimeMillis() + (policy * 1000);
            }
            CacheEntry entry = new CacheEntry(addresses, expiration);
            cache.put(host, entry);
            return this;
        }

        /**
         * Query the cache for the specific host. If found then
         * return its CacheEntry, or null if not found.
         */
        public CacheEntry get(String host) {
            int policy = getPolicy();
            if (policy == InetAddressCachePolicy.NEVER) {
                return null;
            }
            CacheEntry entry = cache.get(host);

            // check if entry has expired
            if (entry != null && policy != InetAddressCachePolicy.FOREVER) {
                if (entry.expiration >= 0 &&
                    entry.expiration < System.currentTimeMillis()) {
                    cache.remove(host);
                    entry = null;
                }
            }

            return entry;
        }
    }

    static {
        // create the impl
        impl = InetAddressImplFactory.create();
    }

    /**
     * Creates an InetAddress based on the provided host name and IP address.
     * No name service is checked for the validity of the address.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code java.sun.com}", or a textual representation of its IP
     * address.
     * <p> No validity checking is done on the host name either.
     *
     * <p> If addr specifies an IPv4 address an instance of Inet4Address
     * will be returned; otherwise, an instance of Inet6Address
     * will be returned.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @exception  UnknownHostException  if IP address is of illegal length
     * @since 1.4
     *
    public static InetAddress getByAddress(String host, byte[] addr)
        throws UnknownHostException {
        if (host != null && host.length() > 0 && host.charAt(0) == '[') {
            if (host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
            }
        }
        if (addr != null) {
            if (addr.length == Inet4Address.INADDRSZ) {
                return new Inet4Address(host, addr);
            } else if (addr.length == Inet6Address.INADDRSZ) {
                byte[] newAddr
                    = IPAddressUtil.convertFromIPv4MappedAddress(addr);
                if (newAddr != null) {
                    return new Inet4Address(host, newAddr);
                } else {
                    return new Inet6Address(host, addr);
                }
            }
        }
        throw new UnknownHostException("addr is of illegal length");
    }
     */

    /**
     * Returns the loopback address.
     * <p>
     * The InetAddress returned will represent the IPv4
     * loopback address, 127.0.0.1, or the IPv6 loopback
     * address, ::1. The IPv4 loopback address returned
     * is only one of many in the form 127.*.*.*
     *
     * @return  the InetAddress loopback instance.
     * @since 1.7
     */
    public static InetAddress getLoopbackAddress() {
        return impl.loopbackAddress();
    }


    /**
     * check if the literal address string has %nn appended
     * returns -1 if not, or the numeric value otherwise.
     *
     * %nn may also be a string that represents the displayName of
     * a currently available NetworkInterface.
     */
    private static int checkNumericZone (String s) throws UnknownHostException {
        int percent = s.indexOf ('%');
        int slen = s.length();
        int digit, zone=0;
        if (percent == -1) {
            return -1;
        }
        for (int i=percent+1; i<slen; i++) {
            char c = s.charAt(i);
            if (c == ']') {
                if (i == percent+1) {
                    /* empty per-cent field */
                    return -1;
                }
                break;
            }
            if ((digit = Character.digit (c, 10)) < 0) {
                return -1;
            }
            zone = (zone * 10) + digit;
        }
        return zone;
    }

    /**
     * Returns an {@code InetAddress} object given the raw IP address .
     * The argument is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * <p> This method doesn't block, i.e. no reverse name service lookup
     * is performed.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @exception  UnknownHostException  if IP address is of illegal length
     * @since 1.4
     *
    public static InetAddress getByAddress(byte[] addr)
        throws UnknownHostException {
        return getByAddress(null, addr);
    }
     */

    private static InetAddress cachedLocalHost = null;
    private static long cacheTime = 0;
    private static final long maxCacheTime = 5000L;
    private static final Object cacheLock = new Object();

    /**
     * Perform class load-time initializations.
     */
    private static native void init();


    /*
     * Returns the InetAddress representing anyLocalAddress
     * (typically 0.0.0.0 or ::0)
     */
    static InetAddress anyLocalAddress() {
        return impl.anyLocalAddress();
    }

    /*
     * Load and instantiate an underlying impl class
     */
    static InetAddressImpl loadImpl(String implName) {
        Object impl = null;

        /*
         * Property "impl.prefix" will be prepended to the classname
         * of the implementation object we instantiate, to which we
         * delegate the real work (like native methods).  This
         * property can vary across implementations of the java.
         * classes.  The default is an empty String "".
         */
        String prefix = AccessController.doPrivileged(
                      new GetPropertyAction("impl.prefix", ""));
        try {
            impl = Class.forName("java.net." + prefix + implName).newInstance();
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        }

        if (impl == null) {
            try {
                impl = Class.forName(implName).newInstance();
            } catch (Exception e) {
                throw new Error("System property impl.prefix incorrect");
            }
        }

        return (InetAddressImpl) impl;
    }
}

/*
 * Simple factory to create the impl
 */
class InetAddressImplFactory {

    static InetAddressImpl create() {
        return InetAddress.loadImpl(isIPv6Supported() ?
                                    "Inet6AddressImpl" : "Inet4AddressImpl");
    }

    static native boolean isIPv6Supported();
}
