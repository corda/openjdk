/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.util.*;
import java.io.IOException;

/**
 * A UnresolvedPermissionCollection stores a collection
 * of UnresolvedPermission permissions.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.UnresolvedPermission
 *
 *
 * @author Roland Schemers
 *
 * @serial include
 */

final class UnresolvedPermissionCollection
extends PermissionCollection
implements java.io.Serializable
{
    /**
     * Key is permission type, value is a list of the UnresolvedPermissions
     * of the same type.
     * Not serialized; see serialization section at end of class.
     */
    private transient Map<String, List<UnresolvedPermission>> perms;

    /**
     * Create an empty UnresolvedPermissionCollection object.
     *
     */
    public UnresolvedPermissionCollection() {
        perms = new HashMap<String, List<UnresolvedPermission>>(11);
    }

    /**
     * Adds a permission to this UnresolvedPermissionCollection.
     * The key for the hash is the unresolved permission's type (class) name.
     *
     * @param permission the Permission object to add.
     */

    public void add(Permission permission)
    {
        if (! (permission instanceof UnresolvedPermission))
            throw new IllegalArgumentException("invalid permission: "+
                                               permission);
        UnresolvedPermission up = (UnresolvedPermission) permission;

        List<UnresolvedPermission> v;
        synchronized (this) {
            v = perms.get(up.getName());
            if (v == null) {
                v = new ArrayList<UnresolvedPermission>();
                perms.put(up.getName(), v);
            }
        }
        synchronized (v) {
            v.add(up);
        }
    }

    /**
     * get any unresolved permissions of the same type as p,
     * and return the List containing them.
     */
    List<UnresolvedPermission> getUnresolvedPermissions(Permission p) {
        synchronized (this) {
            return perms.get(p.getClass().getName());
        }
    }

    /**
     * always returns false for unresolved permissions
     *
     */
    public boolean implies(Permission permission)
    {
        return false;
    }

    /**
     * Returns an enumeration of all the UnresolvedPermission lists in the
     * container.
     *
     * @return an enumeration of all the UnresolvedPermission objects.
     */

    public Enumeration<Permission> elements() {
        List<Permission> results =
            new ArrayList<>(); // where results are stored

        // Get iterator of Map values (which are lists of permissions)
        synchronized (this) {
            for (List<UnresolvedPermission> l : perms.values()) {
                synchronized (l) {
                    results.addAll(l);
                }
            }
        }

        return Collections.enumeration(results);
    }

    private static final long serialVersionUID = -7176153071733132400L;

    // Need to maintain serialization interoperability with earlier releases,
    // which had the serializable field:
    // private Hashtable permissions; // keyed on type
}
