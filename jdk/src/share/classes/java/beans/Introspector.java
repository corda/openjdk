/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

/**
 * The Introspector class provides a standard way for tools to learn about
 * the properties, events, and methods supported by a target Java Bean.
 * <p>
 * For each of those three kinds of information, the Introspector will
 * separately analyze the bean's class and superclasses looking for
 * either explicit or implicit information and use that information to
 * build a BeanInfo object that comprehensively describes the target bean.
 * <p>
 * For each class "Foo", explicit information may be available if there exists
 * a corresponding "FooBeanInfo" class that provides a non-null value when
 * queried for the information.   We first look for the BeanInfo class by
 * taking the full package-qualified name of the target bean class and
 * appending "BeanInfo" to form a new class name.  If this fails, then
 * we take the final classname component of this name, and look for that
 * class in each of the packages specified in the BeanInfo package search
 * path.
 * <p>
 * Thus for a class such as "sun.xyz.OurButton" we would first look for a
 * BeanInfo class called "sun.xyz.OurButtonBeanInfo" and if that failed we'd
 * look in each package in the BeanInfo search path for an OurButtonBeanInfo
 * class.  With the default search path, this would mean looking for
 * "sun.beans.infos.OurButtonBeanInfo".
 * <p>
 * If a class provides explicit BeanInfo about itself then we add that to
 * the BeanInfo information we obtained from analyzing any derived classes,
 * but we regard the explicit information as being definitive for the current
 * class and its base classes, and do not proceed any further up the superclass
 * chain.
 * <p>
 * If we don't find explicit BeanInfo on a class, we use low-level
 * reflection to study the methods of the class and apply standard design
 * patterns to identify property accessors, event sources, or public
 * methods.  We then proceed to analyze the class's superclass and add
 * in the information from it (and possibly on up the superclass chain).
 * <p>
 * For more information about introspection and design patterns, please
 * consult the
 *  <a href="http://www.oracle.com/technetwork/java/javase/documentation/spec-136004.html">JavaBeans&trade; specification</a>.
 */

public class Introspector {

    /**
     * Utility method to take a string and convert it to normal Java variable
     * name capitalization.  This normally means converting the first
     * character from upper case to lower case, but in the (unusual) special
     * case when there is more than one character and both the first and
     * second characters are upper case, we leave it alone.
     * <p>
     * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays
     * as "URL".
     *
     * @param  name The string to be decapitalized.
     * @return  The decapitalized version of the string.
     */
    public static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                        Character.isUpperCase(name.charAt(0))){
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

} // end class Introspector
