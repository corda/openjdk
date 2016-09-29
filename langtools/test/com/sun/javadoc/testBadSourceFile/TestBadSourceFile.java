/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug      4835749
 * @summary  Make sure exception is not thrown if there is a bad source
 *           file in the same directory as the file being documented.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestBadSourceFile
 * @run main TestBadSourceFile
 */

public class TestBadSourceFile extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4835749";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, SRC_DIR + FS + "C2.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = NO_TEST;
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestBadSourceFile tester = new TestBadSourceFile();
        int exitCode = run(tester, ARGS, TEST, NEGATED_TEST);
        tester.checkExitCode(0, exitCode);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
