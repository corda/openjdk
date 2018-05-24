/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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

package javax.activation;

import java.io.*;

/**
 * The CommandInfo class is used by CommandMap implementations to
 * describe the results of command requests. It provides the requestor
 * with both the verb requested, as well as an instance of the
 * bean. There is also a method that will return the name of the
 * class that implements the command but <i>it is not guaranteed to
 * return a valid value</i>. The reason for this is to allow CommandMap
 * implmentations that subclass CommandInfo to provide special
 * behavior. For example a CommandMap could dynamically generate
 * JavaBeans. In this case, it might not be possible to create an
 * object with all the correct state information solely from the class
 * name.
 *
 * @since 1.6
 */

public class CommandInfo {
    private String verb;
    private String className;

    /**
     * The Constructor for CommandInfo.
     * @param verb The command verb this CommandInfo decribes.
     * @param className The command's fully qualified class name.
     */
    public CommandInfo(String verb, String className) {
        this.verb = verb;
        this.className = className;
    }

    /**
     * Return the command verb.
     *
     * @return the command verb.
     */
    public String getCommandName() {
        return verb;
    }

    /**
     * Return the command's class name. <i>This method MAY return null in
     * cases where a CommandMap subclassed CommandInfo for its
     * own purposes.</i> In other words, it might not be possible to
     * create the correct state in the command by merely knowing
     * its class name. <b>DO NOT DEPEND ON THIS METHOD RETURNING
     * A VALID VALUE!</b>
     *
     * @return The class name of the command, or <i>null</i>
     */
    public String getCommandClass() {
        return className;
    }
}
