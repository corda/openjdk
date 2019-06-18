/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

/**
 * Serialization's descriptor for classes.  It contains the name and
 * serialVersionUID of the class.  The ObjectStreamClass for a specific class
 * loaded in this Java VM can be found/created using the lookup method.
 *
 * <p>The algorithm to compute the SerialVersionUID is described in
 * <a href="../../../platform/serialization/spec/class.html#4100">Object
 * Serialization Specification, Section 4.6, Stream Unique Identifiers</a>.
 *
 * @author      Mike Warres
 * @author      Roger Riggs
 * @see ObjectStreamField
 * @see <a href="../../../platform/serialization/spec/class.html">Object Serialization Specification, Section 4, Class Descriptors</a>
 * @since   JDK1.1
 */
public class ObjectStreamClass implements Serializable {
    private static final String NOT_IMPLEMENTED = "Serialisation not supported";

    /** serialPersistentFields value indicating no serializable fields */
    public static final ObjectStreamField[] NO_FIELDS =
        new ObjectStreamField[0];

    /**
     * Find the descriptor for a class that can be serialized.  Creates an
     * ObjectStreamClass instance if one does not exist yet for class. Null is
     * returned if the specified class does not implement java.io.Serializable
     * or java.io.Externalizable.
     *
     * @param   cl class for which to get the descriptor
     * @return  the class descriptor for the specified class
     */
    public static ObjectStreamClass lookup(Class<?> cl) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Returns the descriptor for any class, regardless of whether it
     * implements {@link Serializable}.
     *
     * @param        cl class for which to get the descriptor
     * @return       the class descriptor for the specified class
     * @since 1.6
     */
    public static ObjectStreamClass lookupAny(Class<?> cl) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Returns the name of the class described by this descriptor.
     * This method returns the name of the class in the format that
     * is used by the {@link Class#getName} method.
     *
     * @return a string representing the name of the class
     */
    public String getName() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Return the serialVersionUID for this class.  The serialVersionUID
     * defines a set of classes all with the same name that have evolved from a
     * common root class and agree to be serialized and deserialized using a
     * common format.  NonSerializable classes have a serialVersionUID of 0L.
     *
     * @return  the SUID of the class described by this descriptor
     */
    public long getSerialVersionUID() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Return the class in the local VM that this version is mapped to.  Null
     * is returned if there is no corresponding local class.
     *
     * @return  the <code>Class</code> instance that this descriptor represents
     */
    public Class<?> forClass() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Return an array of the fields of this serializable class.
     *
     * @return  an array containing an element for each persistent field of
     *          this class. Returns an array of length zero if there are no
     *          fields.
     * @since 1.2
     */
    public ObjectStreamField[] getFields() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Get the field of this class by name.
     *
     * @param   name the name of the data field to look for
     * @return  The ObjectStreamField object of the named field or null if
     *          there is no such named field.
     */
    public ObjectStreamField getField(String name) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    /**
     * Return a string describing this ObjectStreamClass.
     */
    public String toString() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    private ObjectStreamClass(final Class<?> cl) {
    }

    /**
     * Creates blank class descriptor which should be initialized via a
     * subsequent call to initProxy(), initNonProxy() or readNonProxy().
     */
    ObjectStreamClass() {
    }
}
