/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.datatransfer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.FlavorTable;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import java.io.FilePermission;


/**
 * Provides a set of functions to be shared among the DataFlavor class and
 * platform-specific data transfer implementations.
 *
 * The concept of "flavors" and "natives" is extended to include "formats",
 * which are the numeric values Win32 and X11 use to express particular data
 * types. Like FlavorMap, which provides getNativesForFlavors(DataFlavor[]) and
 * getFlavorsForNatives(String[]) functions, DataTransferer provides a set
 * of getFormatsFor(Flavor|Flavors) and
 * getFlavorsFor(Format|Formats) functions.
 *
 * Also provided are functions for translating
 * a byte array or InputStream into an Object, given a source format and
 * a target DataFlavor.
 *
 * @author David Mendenhall
 * @author Danila Sinopalnikov
 *
 * @since 1.3.1
 */
public abstract class DataTransferer {

    /**
     * The <code>DataFlavor</code> representing plain text with Unicode
     * encoding, where:
     * <pre>
     *     representationClass = java.lang.String
     *     mimeType            = "text/plain; charset=Unicode"
     * </pre>
     */
    public static final DataFlavor plainTextStringFlavor;

    /**
     * The <code>DataFlavor</code> representing a Java text encoding String
     * encoded in UTF-8, where
     * <pre>
     *     representationClass = [B
     *     mimeType            = "application/x-java-text-encoding"
     * </pre>
     */
    public static final DataFlavor javaTextEncodingFlavor;

    /**
     * Lazy initialization of Standard Encodings.
     */
    private static class StandardEncodingsHolder {
        private static final SortedSet<String> standardEncodings = load();

        private static SortedSet<String> load() {
            final Comparator comparator =
                    new CharsetComparator(IndexedComparator.SELECT_WORST);
            final SortedSet<String> tempSet = new TreeSet<String>(comparator);
            tempSet.add("US-ASCII");
            tempSet.add("ISO-8859-1");
            tempSet.add("UTF-8");
            tempSet.add("UTF-16BE");
            tempSet.add("UTF-16LE");
            tempSet.add("UTF-16");
            tempSet.add(getDefaultTextCharset());
            return Collections.unmodifiableSortedSet(tempSet);
        }
    }

    /**
     * Tracks whether a particular text/* MIME type supports the charset
     * parameter. The Map is initialized with all of the standard MIME types
     * listed in the DataFlavor.selectBestTextFlavor method comment. Additional
     * entries may be added during the life of the JRE for text/<other> types.
     */
    private static final Map textMIMESubtypeCharsetSupport;

    /**
     * Cache of the platform default encoding as specified in the
     * "file.encoding" system property.
     */
    private static String defaultEncoding;

    /**
     * A collection of all natives listed in flavormap.properties with
     * a primary MIME type of "text".
     */
    private static final Set textNatives =
        Collections.synchronizedSet(new HashSet());

    /**
     * The native encodings/charsets for the Set of textNatives.
     */
    private static final Map nativeCharsets =
        Collections.synchronizedMap(new HashMap());

    /**
     * The end-of-line markers for the Set of textNatives.
     */
    private static final Map nativeEOLNs =
        Collections.synchronizedMap(new HashMap());

    /**
     * The number of terminating NUL bytes for the Set of textNatives.
     */
    private static final Map nativeTerminators =
        Collections.synchronizedMap(new HashMap());

    /**
     * The key used to store pending data conversion requests for an AppContext.
     */
    private static final String DATA_CONVERTER_KEY = "DATA_CONVERTER_KEY";

    /**
     * The singleton DataTransferer instance. It is created during MToolkit
     * or WToolkit initialization.
     */
    private static DataTransferer transferer;

    static {
        DataFlavor tPlainTextStringFlavor = null;
        try {
            tPlainTextStringFlavor = new DataFlavor
                ("text/plain;charset=Unicode;class=java.lang.String");
        } catch (ClassNotFoundException cannotHappen) {
        }
        plainTextStringFlavor = tPlainTextStringFlavor;

        DataFlavor tJavaTextEncodingFlavor = null;
        try {
            tJavaTextEncodingFlavor = new DataFlavor
                ("application/x-java-text-encoding;class=\"[B\"");
        } catch (ClassNotFoundException cannotHappen) {
        }
        javaTextEncodingFlavor = tJavaTextEncodingFlavor;

        Map tempMap = new HashMap(17);
        tempMap.put("sgml", Boolean.TRUE);
        tempMap.put("xml", Boolean.TRUE);
        tempMap.put("html", Boolean.TRUE);
        tempMap.put("enriched", Boolean.TRUE);
        tempMap.put("richtext", Boolean.TRUE);
        tempMap.put("uri-list", Boolean.TRUE);
        tempMap.put("directory", Boolean.TRUE);
        tempMap.put("css", Boolean.TRUE);
        tempMap.put("calendar", Boolean.TRUE);
        tempMap.put("plain", Boolean.TRUE);
        tempMap.put("rtf", Boolean.FALSE);
        tempMap.put("tab-separated-values", Boolean.FALSE);
        tempMap.put("t140", Boolean.FALSE);
        tempMap.put("rfc822-headers", Boolean.FALSE);
        tempMap.put("parityfec", Boolean.FALSE);
        textMIMESubtypeCharsetSupport = Collections.synchronizedMap(tempMap);
    }

    /**
     * The accessor method for the singleton DataTransferer instance. Note
     * that in a headless environment, there may be no DataTransferer instance;
     * instead, null will be returned.
     */
    public static synchronized DataTransferer getInstance() {
        return null;
    }

    /**
     * Converts an arbitrary text encoding to its canonical name.
     */
    public static String canonicalName(String encoding) {
        if (encoding == null) {
            return null;
        }
        try {
            return Charset.forName(encoding).name();
        } catch (IllegalCharsetNameException icne) {
            return encoding;
        } catch (UnsupportedCharsetException uce) {
            return encoding;
        }
    }

    /**
     * If the specified flavor is a text flavor which supports the "charset"
     * parameter, then this method returns that parameter, or the default
     * charset if no such parameter was specified at construction. For non-
     * text DataFlavors, and for non-charset text flavors, this method returns
     * null.
     */
    public static String getTextCharset(DataFlavor flavor) {
        if (!isFlavorCharsetTextType(flavor)) {
            return null;
        }

        String encoding = flavor.getParameter("charset");

        return (encoding != null) ? encoding : getDefaultTextCharset();
    }

    /**
     * Returns the platform's default character encoding.
     */
    public static String getDefaultTextCharset() {
        if (defaultEncoding != null) {
            return defaultEncoding;
        }
        return defaultEncoding = Charset.defaultCharset().name();
    }

    /**
     * Tests only whether the flavor's MIME type supports the charset
     * parameter. Must only be called for flavors with a primary type of
     * "text".
     */
    public static boolean doesSubtypeSupportCharset(DataFlavor flavor) {
        String subType = flavor.getSubType();
        if (subType == null) {
            return false;
        }

        Object support = textMIMESubtypeCharsetSupport.get(subType);

        if (support != null) {
            return (support == Boolean.TRUE);
        }

        boolean ret_val = (flavor.getParameter("charset") != null);
        textMIMESubtypeCharsetSupport.put
            (subType, (ret_val) ? Boolean.TRUE : Boolean.FALSE);
        return ret_val;
    }
    public static boolean doesSubtypeSupportCharset(String subType,
                                                    String charset)
    {
        Object support = textMIMESubtypeCharsetSupport.get(subType);

        if (support != null) {
            return (support == Boolean.TRUE);
        }

        boolean ret_val = (charset != null);
        textMIMESubtypeCharsetSupport.put
            (subType, (ret_val) ? Boolean.TRUE : Boolean.FALSE);
        return ret_val;
    }

    /**
     * Returns whether this flavor is a text type which supports the
     * 'charset' parameter.
     */
    public static boolean isFlavorCharsetTextType(DataFlavor flavor) {
        // Although stringFlavor doesn't actually support the charset
        // parameter (because its primary MIME type is not "text"), it should
        // be treated as though it does. stringFlavor is semantically
        // equivalent to "text/plain" data.
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return true;
        }

        if (!"text".equals(flavor.getPrimaryType()) ||
            !doesSubtypeSupportCharset(flavor))
        {
            return false;
        }

        Class rep_class = flavor.getRepresentationClass();

        if (flavor.isRepresentationClassReader() ||
            String.class.equals(rep_class) ||
            flavor.isRepresentationClassCharBuffer() ||
            char[].class.equals(rep_class))
        {
            return true;
        }

        if (!(flavor.isRepresentationClassInputStream() ||
              flavor.isRepresentationClassByteBuffer() ||
              byte[].class.equals(rep_class))) {
            return false;
        }

        String charset = flavor.getParameter("charset");

        return (charset != null)
            ? DataTransferer.isEncodingSupported(charset)
            : true; // null equals default encoding which is always supported
    }

    /**
     * Returns whether this flavor is a text type which does not support the
     * 'charset' parameter.
     */
    public static boolean isFlavorNoncharsetTextType(DataFlavor flavor) {
        if (!"text".equals(flavor.getPrimaryType()) ||
            doesSubtypeSupportCharset(flavor))
        {
            return false;
        }

        return (flavor.isRepresentationClassInputStream() ||
                flavor.isRepresentationClassByteBuffer() ||
                byte[].class.equals(flavor.getRepresentationClass()));
    }

    /**
     * Determines whether this JRE can both encode and decode text in the
     * specified encoding.
     */
    public static boolean isEncodingSupported(String encoding) {
        if (encoding == null) {
            return false;
        }
        try {
            return Charset.isSupported(encoding);
        } catch (IllegalCharsetNameException icne) {
            return false;
        }
    }

    /**
     * Returns {@code true} if the given type is a java.rmi.Remote.
     */
    public static boolean isRemote(Class<?> type) {
        return RMI.isRemote(type);
    }

    /**
     * Returns an Iterator which traverses a SortedSet of Strings which are
     * a total order of the standard character sets supported by the JRE. The
     * ordering follows the same principles as DataFlavor.selectBestTextFlavor.
     * So as to avoid loading all available character converters, optional,
     * non-standard, character sets are not included.
     */
    public static Set <String> standardEncodings() {
        return StandardEncodingsHolder.standardEncodings;
    }

    /**
     * Converts a FlavorMap to a FlavorTable.
     */
    public static FlavorTable adaptFlavorMap(final FlavorMap map) {
        if (map instanceof FlavorTable) {
            return (FlavorTable)map;
        }

        return new FlavorTable() {
                public Map getNativesForFlavors(DataFlavor[] flavors) {
                    return map.getNativesForFlavors(flavors);
                }
                public Map getFlavorsForNatives(String[] natives) {
                    return map.getFlavorsForNatives(natives);
                }
                public List getNativesForFlavor(DataFlavor flav) {
                    Map natives =
                        getNativesForFlavors(new DataFlavor[] { flav } );
                    String nat = (String)natives.get(flav);
                    if (nat != null) {
                        List list = new ArrayList(1);
                        list.add(nat);
                        return list;
                    } else {
                        return Collections.EMPTY_LIST;
                    }
                }
                public List getFlavorsForNative(String nat) {
                    Map flavors =
                        getFlavorsForNatives(new String[] { nat } );
                    DataFlavor flavor = (DataFlavor)flavors.get(nat);
                    if (flavor != null) {
                        List list = new ArrayList(1);
                        list.add(flavor);
                        return list;
                    } else {
                        return Collections.EMPTY_LIST;
                    }
                }
            };
    }

    /**
     * Returns the default Unicode encoding for the platform. The encoding
     * need not be canonical. This method is only used by the archaic function
     * DataFlavor.getTextPlainUnicodeFlavor().
     */
    public abstract String getDefaultUnicodeEncoding();

    /**
     * This method is called for text flavor mappings established while parsing
     * the flavormap.properties file. It stores the "eoln" and "terminators"
     * parameters which are not officially part of the MIME type. They are
     * MIME parameters specific to the flavormap.properties file format.
     */
    public void registerTextFlavorProperties(String nat, String charset,
                                             String eoln, String terminators) {
        Long format = getFormatForNativeAsLong(nat);

        textNatives.add(format);
        nativeCharsets.put(format, (charset != null && charset.length() != 0)
            ? charset : getDefaultTextCharset());
        if (eoln != null && eoln.length() != 0 && !eoln.equals("\n")) {
            nativeEOLNs.put(format, eoln);
        }
        if (terminators != null && terminators.length() != 0) {
            Integer iTerminators = Integer.valueOf(terminators);
            if (iTerminators.intValue() > 0) {
                nativeTerminators.put(format, iTerminators);
            }
        }
    }

    /**
     * Determines whether the native corresponding to the specified long format
     * was listed in the flavormap.properties file.
     */
    protected boolean isTextFormat(long format) {
        return textNatives.contains(Long.valueOf(format));
    }

    protected String getCharsetForTextFormat(Long lFormat) {
        return (String)nativeCharsets.get(lFormat);
    }

    /**
     * Specifies whether text imported from the native system in the specified
     * format is locale-dependent. If so, when decoding such text,
     * 'nativeCharsets' should be ignored, and instead, the Transferable should
     * be queried for its javaTextEncodingFlavor data for the correct encoding.
     */
    public abstract boolean isLocaleDependentTextFormat(long format);

    /**
     * Determines whether the DataFlavor corresponding to the specified long
     * format is DataFlavor.javaFileListFlavor.
     */
    public abstract boolean isFileFormat(long format);

    /**
     * Determines whether the DataFlavor corresponding to the specified long
     * format is DataFlavor.imageFlavor.
     */
    public abstract boolean isImageFormat(long format);

    /**
     * Determines whether the format is a URI list we can convert to
     * a DataFlavor.javaFileListFlavor.
     */
    protected boolean isURIListFormat(long format) {
        return false;
    }

    /**
     * Returns a Map whose keys are all of the possible formats into which data
     * in the specified DataFlavor can be translated. The value of each key
     * is the DataFlavor in which a Transferable's data should be requested
     * when converting to the format.
     * <p>
     * The map keys are sorted according to the native formats preference
     * order.
     */
    public SortedMap getFormatsForFlavor(DataFlavor flavor, FlavorTable map) {
        return getFormatsForFlavors(new DataFlavor[] { flavor },
                                    map);
    }

    /**
     * Returns a Map whose keys are all of the possible formats into which data
     * in the specified DataFlavors can be translated. The value of each key
     * is the DataFlavor in which the Transferable's data should be requested
     * when converting to the format.
     * <p>
     * The map keys are sorted according to the native formats preference
     * order.
     *
     * @param flavors the data flavors
     * @param map the FlavorTable which contains mappings between
     *            DataFlavors and data formats
     * @throws NullPointerException if flavors or map is <code>null</code>
     */
    public SortedMap <Long, DataFlavor> getFormatsForFlavors(
        DataFlavor[] flavors, FlavorTable map)
    {
        Map <Long,DataFlavor> formatMap =
            new HashMap <> (flavors.length);
        Map <Long,DataFlavor> textPlainMap =
            new HashMap <> (flavors.length);
        // Maps formats to indices that will be used to sort the formats
        // according to the preference order.
        // Larger index value corresponds to the more preferable format.
        Map indexMap = new HashMap(flavors.length);
        Map textPlainIndexMap = new HashMap(flavors.length);

        int currentIndex = 0;

        // Iterate backwards so that preferred DataFlavors are used over
        // other DataFlavors. (See javadoc for
        // Transferable.getTransferDataFlavors.)
        for (int i = flavors.length - 1; i >= 0; i--) {
            DataFlavor flavor = flavors[i];
            if (flavor == null) continue;

            // Don't explicitly test for String, since it is just a special
            // case of Serializable
            if (flavor.isFlavorTextType() ||
                flavor.isFlavorJavaFileListType() ||
                DataFlavor.imageFlavor.equals(flavor) ||
                flavor.isRepresentationClassSerializable() ||
                flavor.isRepresentationClassInputStream() ||
                flavor.isRepresentationClassRemote())
            {
                List natives = map.getNativesForFlavor(flavor);

                currentIndex += natives.size();

                for (Iterator iter = natives.iterator(); iter.hasNext(); ) {
                    Long lFormat =
                        getFormatForNativeAsLong((String)iter.next());
                    Integer index = Integer.valueOf(currentIndex--);

                    formatMap.put(lFormat, flavor);
                    indexMap.put(lFormat, index);

                    // SystemFlavorMap.getNativesForFlavor will return
                    // text/plain natives for all text/*. While this is good
                    // for a single text/* flavor, we would prefer that
                    // text/plain native data come from a text/plain flavor.
                    if (("text".equals(flavor.getPrimaryType()) &&
                         "plain".equals(flavor.getSubType())) ||
                        flavor.equals(DataFlavor.stringFlavor))
                    {
                        textPlainMap.put(lFormat, flavor);
                        textPlainIndexMap.put(lFormat, index);
                    }
                }

                currentIndex += natives.size();
            }
        }

        formatMap.putAll(textPlainMap);
        indexMap.putAll(textPlainIndexMap);

        // Sort the map keys according to the formats preference order.
        Comparator comparator =
            new IndexOrderComparator(indexMap, IndexedComparator.SELECT_WORST);
        SortedMap sortedMap = new TreeMap(comparator);
        sortedMap.putAll(formatMap);

        return sortedMap;
    }

    /**
     * Reduces the Map output for the root function to an array of the
     * Map's keys.
     */
    public long[] getFormatsForFlavorAsArray(DataFlavor flavor,
                                             FlavorTable map) {
        return keysToLongArray(getFormatsForFlavor(flavor, map));
    }
    public long[] getFormatsForFlavorsAsArray(DataFlavor[] flavors,
                                              FlavorTable map) {
        return keysToLongArray(getFormatsForFlavors(flavors, map));
    }

    /**
     * Returns a Map whose keys are all of the possible DataFlavors into which
     * data in the specified format can be translated. The value of each key
     * is the format in which the Clipboard or dropped data should be requested
     * when converting to the DataFlavor.
     */
    public Map getFlavorsForFormat(long format, FlavorTable map) {
        return getFlavorsForFormats(new long[] { format }, map);
    }

    /**
     * Returns a Map whose keys are all of the possible DataFlavors into which
     * data in the specified formats can be translated. The value of each key
     * is the format in which the Clipboard or dropped data should be requested
     * when converting to the DataFlavor.
     */
    public Map getFlavorsForFormats(long[] formats, FlavorTable map) {
        Map flavorMap = new HashMap(formats.length);
        Set mappingSet = new HashSet(formats.length);
        Set flavorSet = new HashSet(formats.length);

        // First step: build flavorSet, mappingSet and initial flavorMap
        // flavorSet  - the set of all the DataFlavors into which
        //              data in the specified formats can be translated;
        // mappingSet - the set of all the mappings from the specified formats
        //              into any DataFlavor;
        // flavorMap  - after this step, this map maps each of the DataFlavors
        //              from flavorSet to any of the specified formats.
        for (int i = 0; i < formats.length; i++) {
            long format = formats[i];
            String nat = getNativeForFormat(format);
            List flavors = map.getFlavorsForNative(nat);

            for (Iterator iter = flavors.iterator(); iter.hasNext(); ) {
                DataFlavor flavor = (DataFlavor)iter.next();

                // Don't explicitly test for String, since it is just a special
                // case of Serializable
                if (flavor.isFlavorTextType() ||
                    flavor.isFlavorJavaFileListType() ||
                    DataFlavor.imageFlavor.equals(flavor) ||
                    flavor.isRepresentationClassSerializable() ||
                    flavor.isRepresentationClassInputStream() ||
                    flavor.isRepresentationClassRemote())
                {
                    Long lFormat = Long.valueOf(format);
                    Object mapping =
                        DataTransferer.createMapping(lFormat, flavor);
                    flavorMap.put(flavor, lFormat);
                    mappingSet.add(mapping);
                    flavorSet.add(flavor);
                }
            }
        }

        // Second step: for each DataFlavor try to figure out which of the
        // specified formats is the best to translate to this flavor.
        // Then map each flavor to the best format.
        // For the given flavor, FlavorTable indicates which native will
        // best reflect data in the specified flavor to the underlying native
        // platform. We assume that this native is the best to translate
        // to this flavor.
        // Note: FlavorTable allows one-way mappings, so we can occasionally
        // map a flavor to the format for which the corresponding
        // format-to-flavor mapping doesn't exist. For this reason we have built
        // a mappingSet of all format-to-flavor mappings for the specified formats
        // and check if the format-to-flavor mapping exists for the
        // (flavor,format) pair being added.
        for (Iterator flavorIter = flavorSet.iterator();
             flavorIter.hasNext(); ) {
            DataFlavor flavor = (DataFlavor)flavorIter.next();

            List natives = map.getNativesForFlavor(flavor);

            for (Iterator nativeIter = natives.iterator();
                 nativeIter.hasNext(); ) {
                Long lFormat =
                    getFormatForNativeAsLong((String)nativeIter.next());
                Object mapping = DataTransferer.createMapping(lFormat, flavor);

                if (mappingSet.contains(mapping)) {
                    flavorMap.put(flavor, lFormat);
                    break;
                }
            }
        }

        return flavorMap;
    }

    /**
     * Returns a Set of all DataFlavors for which
     * 1) a mapping from at least one of the specified formats exists in the
     * specified map and
     * 2) the data translation for this mapping can be performed by the data
     * transfer subsystem.
     *
     * @param formats the data formats
     * @param map the FlavorTable which contains mappings between
     *            DataFlavors and data formats
     * @throws NullPointerException if formats or map is <code>null</code>
     */
    public Set getFlavorsForFormatsAsSet(long[] formats, FlavorTable map) {
        Set flavorSet = new HashSet(formats.length);

        for (int i = 0; i < formats.length; i++) {
            String nat = getNativeForFormat(formats[i]);
            List flavors = map.getFlavorsForNative(nat);

            for (Iterator iter = flavors.iterator(); iter.hasNext(); ) {
                DataFlavor flavor = (DataFlavor)iter.next();

                // Don't explicitly test for String, since it is just a special
                // case of Serializable
                if (flavor.isFlavorTextType() ||
                    flavor.isFlavorJavaFileListType() ||
                    DataFlavor.imageFlavor.equals(flavor) ||
                    flavor.isRepresentationClassSerializable() ||
                    flavor.isRepresentationClassInputStream() ||
                    flavor.isRepresentationClassRemote())
                {
                    flavorSet.add(flavor);
                }
            }
        }

        return flavorSet;
    }

    /**
     * Returns an array of all DataFlavors for which
     * 1) a mapping from the specified format exists in the specified map and
     * 2) the data translation for this mapping can be performed by the data
     * transfer subsystem.
     * The array will be sorted according to a
     * <code>DataFlavorComparator</code> created with the specified
     * map as an argument.
     *
     * @param format the data format
     * @param map the FlavorTable which contains mappings between
     *            DataFlavors and data formats
     * @throws NullPointerException if map is <code>null</code>
     */
    public DataFlavor[] getFlavorsForFormatAsArray(long format,
                                                   FlavorTable map) {
        return getFlavorsForFormatsAsArray(new long[] { format }, map);
    }

    /**
     * Returns an array of all DataFlavors for which
     * 1) a mapping from at least one of the specified formats exists in the
     * specified map and
     * 2) the data translation for this mapping can be performed by the data
     * transfer subsystem.
     * The array will be sorted according to a
     * <code>DataFlavorComparator</code> created with the specified
     * map as an argument.
     *
     * @param formats the data formats
     * @param map the FlavorTable which contains mappings between
     *            DataFlavors and data formats
     * @throws NullPointerException if formats or map is <code>null</code>
     */
    public DataFlavor[] getFlavorsForFormatsAsArray(long[] formats,
                                                    FlavorTable map) {
        // getFlavorsForFormatsAsSet() is less expensive than
        // getFlavorsForFormats().
        return setToSortedDataFlavorArray(getFlavorsForFormatsAsSet(formats, map));
    }

    /**
     * Returns an object that represents a mapping between the specified
     * key and value. <tt>null</tt> values and the <tt>null</tt> keys are
     * permitted. The internal representation of the mapping object is
     * irrelevant. The only requrement is that the two mapping objects are equal
     * if and only if their keys are equal and their values are equal.
     * More formally, the two mapping objects are equal if and only if
     * <tt>(value1 == null ? value2 == null : value1.equals(value2))
     * && (key1 == null ? key2 == null : key1.equals(key2))</tt>.
     */
    private static Object createMapping(Object key, Object value) {
        // NOTE: Should be updated to use AbstractMap.SimpleEntry as
        // soon as it is made public.
        return Arrays.asList(new Object[] { key, value });
    }

    /**
     * Looks-up or registers the String native with the native data transfer
     * system and returns a long format corresponding to that native.
     */
    protected abstract Long getFormatForNativeAsLong(String str);

    /**
     * Looks-up the String native corresponding to the specified long format in
     * the native data transfer system.
     */
    protected abstract String getNativeForFormat(long format);

    /**
     * Helper function to reduce a Map with Long keys to a long array.
     * <p>
     * The map keys are sorted according to the native formats preference
     * order.
     */
    public static long[] keysToLongArray(SortedMap map) {
        Set keySet = map.keySet();
        long[] retval = new long[keySet.size()];
        int i = 0;
        for (Iterator iter = keySet.iterator(); iter.hasNext(); i++) {
            retval[i] = ((Long)iter.next()).longValue();
        }
        return retval;
    }

    /**
     * Helper function to convert a Set of DataFlavors to a sorted array.
     * The array will be sorted according to <code>DataFlavorComparator</code>.
     */
    public static DataFlavor[] setToSortedDataFlavorArray(Set flavorsSet) {
        DataFlavor[] flavors = new DataFlavor[flavorsSet.size()];
        flavorsSet.toArray(flavors);
        final Comparator comparator =
                new DataFlavorComparator(IndexedComparator.SELECT_WORST);
        Arrays.sort(flavors, comparator);
        return flavors;
    }

    /**
     * Returns platform-specific mappings for the specified native.
     * If there are no platform-specific mappings for this native, the method
     * returns an empty <code>List</code>.
     */
    public LinkedHashSet<DataFlavor> getPlatformMappingsForNative(String nat) {
       return new LinkedHashSet<>();
    }

    /**
     * Returns platform-specific mappings for the specified flavor.
     * If there are no platform-specific mappings for this flavor, the method
     * returns an empty <code>List</code>.
     */
    public LinkedHashSet<String> getPlatformMappingsForFlavor(DataFlavor df) {
        return new LinkedHashSet<>();
    }

    /**
     * A Comparator which includes a helper function for comparing two Objects
     * which are likely to be keys in the specified Map.
     */
    public abstract static class IndexedComparator implements Comparator {

        /**
         * The best Object (e.g., DataFlavor) will be the last in sequence.
         */
        public static final boolean SELECT_BEST = true;

        /**
         * The best Object (e.g., DataFlavor) will be the first in sequence.
         */
        public static final boolean SELECT_WORST = false;

        protected final boolean order;

        public IndexedComparator() {
            this(SELECT_BEST);
        }

        public IndexedComparator(boolean order) {
            this.order = order;
        }

        /**
         * Helper method to compare two objects by their Integer indices in the
         * given map. If the map doesn't contain an entry for either of the
         * objects, the fallback index will be used for the object instead.
         *
         * @param indexMap the map which maps objects into Integer indexes.
         * @param obj1 the first object to be compared.
         * @param obj2 the second object to be compared.
         * @param fallbackIndex the Integer to be used as a fallback index.
         * @return a negative integer, zero, or a positive integer as the
         *             first object is mapped to a less, equal to, or greater
         *             index than the second.
         */
        protected static int compareIndices(Map indexMap,
                                            Object obj1, Object obj2,
                                            Integer fallbackIndex) {
            Integer index1 = (Integer)indexMap.get(obj1);
            Integer index2 = (Integer)indexMap.get(obj2);

            if (index1 == null) {
                index1 = fallbackIndex;
            }
            if (index2 == null) {
                index2 = fallbackIndex;
            }

            return index1.compareTo(index2);
        }

        /**
         * Helper method to compare two objects by their Long indices in the
         * given map. If the map doesn't contain an entry for either of the
         * objects, the fallback index will be used for the object instead.
         *
         * @param indexMap the map which maps objects into Long indexes.
         * @param obj1 the first object to be compared.
         * @param obj2 the second object to be compared.
         * @param fallbackIndex the Long to be used as a fallback index.
         * @return a negative integer, zero, or a positive integer as the
         *             first object is mapped to a less, equal to, or greater
         *             index than the second.
         */
        protected static int compareLongs(Map indexMap,
                                          Object obj1, Object obj2,
                                          Long fallbackIndex) {
            Long index1 = (Long)indexMap.get(obj1);
            Long index2 = (Long)indexMap.get(obj2);

            if (index1 == null) {
                index1 = fallbackIndex;
            }
            if (index2 == null) {
                index2 = fallbackIndex;
            }

            return index1.compareTo(index2);
        }
    }

    /**
     * An IndexedComparator which compares two String charsets. The comparison
     * follows the rules outlined in DataFlavor.selectBestTextFlavor. In order
     * to ensure that non-Unicode, non-ASCII, non-default charsets are sorted
     * in alphabetical order, charsets are not automatically converted to their
     * canonical forms.
     */
    public static class CharsetComparator extends IndexedComparator {
        private static final Map charsets;
        private static String defaultEncoding;

        private static final Integer DEFAULT_CHARSET_INDEX = Integer.valueOf(2);
        private static final Integer OTHER_CHARSET_INDEX = Integer.valueOf(1);
        private static final Integer WORST_CHARSET_INDEX = Integer.valueOf(0);
        private static final Integer UNSUPPORTED_CHARSET_INDEX =
            Integer.valueOf(Integer.MIN_VALUE);

        private static final String UNSUPPORTED_CHARSET = "UNSUPPORTED";

        static {
            HashMap charsetsMap = new HashMap(8, 1.0f);

            // we prefer Unicode charsets
            charsetsMap.put(canonicalName("UTF-16LE"), Integer.valueOf(4));
            charsetsMap.put(canonicalName("UTF-16BE"), Integer.valueOf(5));
            charsetsMap.put(canonicalName("UTF-8"), Integer.valueOf(6));
            charsetsMap.put(canonicalName("UTF-16"), Integer.valueOf(7));

            // US-ASCII is the worst charset supported
            charsetsMap.put(canonicalName("US-ASCII"), WORST_CHARSET_INDEX);

            String defEncoding = DataTransferer.canonicalName
                (DataTransferer.getDefaultTextCharset());

            if (charsetsMap.get(defaultEncoding) == null) {
                charsetsMap.put(defaultEncoding, DEFAULT_CHARSET_INDEX);
            }
            charsetsMap.put(UNSUPPORTED_CHARSET, UNSUPPORTED_CHARSET_INDEX);

            charsets = Collections.unmodifiableMap(charsetsMap);
        }

        public CharsetComparator() {
            this(SELECT_BEST);
        }

        public CharsetComparator(boolean order) {
            super(order);
        }

        /**
         * Compares two String objects. Returns a negative integer, zero,
         * or a positive integer as the first charset is worse than, equal to,
         * or better than the second.
         *
         * @param obj1 the first charset to be compared
         * @param obj2 the second charset to be compared
         * @return a negative integer, zero, or a positive integer as the
         *         first argument is worse, equal to, or better than the
         *         second.
         * @throws ClassCastException if either of the arguments is not
         *         instance of String
         * @throws NullPointerException if either of the arguments is
         *         <code>null</code>.
         */
        public int compare(Object obj1, Object obj2) {
            String charset1 = null;
            String charset2 = null;
            if (order == SELECT_BEST) {
                charset1 = (String)obj1;
                charset2 = (String)obj2;
            } else {
                charset1 = (String)obj2;
                charset2 = (String)obj1;
            }

            return compareCharsets(charset1, charset2);
        }

        /**
         * Compares charsets. Returns a negative integer, zero, or a positive
         * integer as the first charset is worse than, equal to, or better than
         * the second.
         * <p>
         * Charsets are ordered according to the following rules:
         * <ul>
         * <li>All unsupported charsets are equal.
         * <li>Any unsupported charset is worse than any supported charset.
         * <li>Unicode charsets, such as "UTF-16", "UTF-8", "UTF-16BE" and
         *     "UTF-16LE", are considered best.
         * <li>After them, platform default charset is selected.
         * <li>"US-ASCII" is the worst of supported charsets.
         * <li>For all other supported charsets, the lexicographically less
         *     one is considered the better.
         * </ul>
         *
         * @param charset1 the first charset to be compared
         * @param charset2 the second charset to be compared.
         * @return a negative integer, zero, or a positive integer as the
         *             first argument is worse, equal to, or better than the
         *             second.
         */
        protected int compareCharsets(String charset1, String charset2) {
            charset1 = getEncoding(charset1);
            charset2 = getEncoding(charset2);

            int comp = compareIndices(charsets, charset1, charset2,
                                      OTHER_CHARSET_INDEX);

            if (comp == 0) {
                return charset2.compareTo(charset1);
            }

            return comp;
        }

        /**
         * Returns encoding for the specified charset according to the
         * following rules:
         * <ul>
         * <li>If the charset is <code>null</code>, then <code>null</code> will
         *     be returned.
         * <li>Iff the charset specifies an encoding unsupported by this JRE,
         *     <code>UNSUPPORTED_CHARSET</code> will be returned.
         * <li>If the charset specifies an alias name, the corresponding
         *     canonical name will be returned iff the charset is a known
         *     Unicode, ASCII, or default charset.
         * </ul>
         *
         * @param charset the charset.
         * @return an encoding for this charset.
         */
        protected static String getEncoding(String charset) {
            if (charset == null) {
                return null;
            } else if (!DataTransferer.isEncodingSupported(charset)) {
                return UNSUPPORTED_CHARSET;
            } else {
                // Only convert to canonical form if the charset is one
                // of the charsets explicitly listed in the known charsets
                // map. This will happen only for Unicode, ASCII, or default
                // charsets.
                String canonicalName = DataTransferer.canonicalName(charset);
                return (charsets.containsKey(canonicalName))
                    ? canonicalName
                    : charset;
            }
        }
    }

    /**
     * An IndexedComparator which compares two DataFlavors. For text flavors,
     * the comparison follows the rules outlined in
     * DataFlavor.selectBestTextFlavor. For non-text flavors, unknown
     * application MIME types are preferred, followed by known
     * application/x-java-* MIME types. Unknown application types are preferred
     * because if the user provides his own data flavor, it will likely be the
     * most descriptive one. For flavors which are otherwise equal, the
     * flavors' string representation are compared in the alphabetical order.
     */
    public static class DataFlavorComparator extends IndexedComparator {

        private final CharsetComparator charsetComparator;

        private static final Map exactTypes;
        private static final Map primaryTypes;
        private static final Map nonTextRepresentations;
        private static final Map textTypes;
        private static final Map decodedTextRepresentations;
        private static final Map encodedTextRepresentations;

        private static final Integer UNKNOWN_OBJECT_LOSES =
            Integer.valueOf(Integer.MIN_VALUE);
        private static final Integer UNKNOWN_OBJECT_WINS =
            Integer.valueOf(Integer.MAX_VALUE);

        private static final Long UNKNOWN_OBJECT_LOSES_L =
            Long.valueOf(Long.MIN_VALUE);
        private static final Long UNKNOWN_OBJECT_WINS_L =
            Long.valueOf(Long.MAX_VALUE);

        static {
            {
                HashMap exactTypesMap = new HashMap(4, 1.0f);

                // application/x-java-* MIME types
                exactTypesMap.put("application/x-java-file-list",
                                  Integer.valueOf(0));
                exactTypesMap.put("application/x-java-serialized-object",
                                  Integer.valueOf(1));
                exactTypesMap.put("application/x-java-jvm-local-objectref",
                                  Integer.valueOf(2));
                exactTypesMap.put("application/x-java-remote-object",
                                  Integer.valueOf(3));

                exactTypes = Collections.unmodifiableMap(exactTypesMap);
            }

            {
                HashMap primaryTypesMap = new HashMap(1, 1.0f);

                primaryTypesMap.put("application", Integer.valueOf(0));

                primaryTypes = Collections.unmodifiableMap(primaryTypesMap);
            }

            {
                HashMap nonTextRepresentationsMap = new HashMap(3, 1.0f);

                nonTextRepresentationsMap.put(java.io.InputStream.class,
                                              Integer.valueOf(0));
                nonTextRepresentationsMap.put(java.io.Serializable.class,
                                              Integer.valueOf(1));

                Class<?> remoteClass = RMI.remoteClass();
                if (remoteClass != null) {
                    nonTextRepresentationsMap.put(remoteClass,
                                                  Integer.valueOf(2));
                }

                nonTextRepresentations =
                    Collections.unmodifiableMap(nonTextRepresentationsMap);
            }

            {
                HashMap textTypesMap = new HashMap(16, 1.0f);

                // plain text
                textTypesMap.put("text/plain", Integer.valueOf(0));

                // stringFlavor
                textTypesMap.put("application/x-java-serialized-object",
                                Integer.valueOf(1));

                // misc
                textTypesMap.put("text/calendar", Integer.valueOf(2));
                textTypesMap.put("text/css", Integer.valueOf(3));
                textTypesMap.put("text/directory", Integer.valueOf(4));
                textTypesMap.put("text/parityfec", Integer.valueOf(5));
                textTypesMap.put("text/rfc822-headers", Integer.valueOf(6));
                textTypesMap.put("text/t140", Integer.valueOf(7));
                textTypesMap.put("text/tab-separated-values", Integer.valueOf(8));
                textTypesMap.put("text/uri-list", Integer.valueOf(9));

                // enriched
                textTypesMap.put("text/richtext", Integer.valueOf(10));
                textTypesMap.put("text/enriched", Integer.valueOf(11));
                textTypesMap.put("text/rtf", Integer.valueOf(12));

                // markup
                textTypesMap.put("text/html", Integer.valueOf(13));
                textTypesMap.put("text/xml", Integer.valueOf(14));
                textTypesMap.put("text/sgml", Integer.valueOf(15));

                textTypes = Collections.unmodifiableMap(textTypesMap);
            }

            {
                HashMap decodedTextRepresentationsMap = new HashMap(4, 1.0f);

                decodedTextRepresentationsMap.put
                    (char[].class, Integer.valueOf(0));
                decodedTextRepresentationsMap.put
                    (java.nio.CharBuffer.class, Integer.valueOf(1));
                decodedTextRepresentationsMap.put
                    (java.lang.String.class, Integer.valueOf(2));
                decodedTextRepresentationsMap.put
                    (java.io.Reader.class, Integer.valueOf(3));

                decodedTextRepresentations =
                    Collections.unmodifiableMap(decodedTextRepresentationsMap);
            }

            {
                HashMap encodedTextRepresentationsMap = new HashMap(3, 1.0f);

                encodedTextRepresentationsMap.put
                    (byte[].class, Integer.valueOf(0));
                encodedTextRepresentationsMap.put
                    (java.nio.ByteBuffer.class, Integer.valueOf(1));
                encodedTextRepresentationsMap.put
                    (java.io.InputStream.class, Integer.valueOf(2));

                encodedTextRepresentations =
                    Collections.unmodifiableMap(encodedTextRepresentationsMap);
            }
        }

        public DataFlavorComparator() {
            this(SELECT_BEST);
        }

        public DataFlavorComparator(boolean order) {
            super(order);

            charsetComparator = new CharsetComparator(order);
        }

        public int compare(Object obj1, Object obj2) {
            DataFlavor flavor1 = null;
            DataFlavor flavor2 = null;
            if (order == SELECT_BEST) {
                flavor1 = (DataFlavor)obj1;
                flavor2 = (DataFlavor)obj2;
            } else {
                flavor1 = (DataFlavor)obj2;
                flavor2 = (DataFlavor)obj1;
            }

            if (flavor1.equals(flavor2)) {
                return 0;
            }

            int comp = 0;

            String primaryType1 = flavor1.getPrimaryType();
            String subType1 = flavor1.getSubType();
            String mimeType1 = primaryType1 + "/" + subType1;
            Class class1 = flavor1.getRepresentationClass();

            String primaryType2 = flavor2.getPrimaryType();
            String subType2 = flavor2.getSubType();
            String mimeType2 = primaryType2 + "/" + subType2;
            Class class2 = flavor2.getRepresentationClass();

            if (flavor1.isFlavorTextType() && flavor2.isFlavorTextType()) {
                // First, compare MIME types
                comp = compareIndices(textTypes, mimeType1, mimeType2,
                                      UNKNOWN_OBJECT_LOSES);
                if (comp != 0) {
                    return comp;
                }

                // Only need to test one flavor because they both have the
                // same MIME type. Also don't need to worry about accidentally
                // passing stringFlavor because either
                //   1. Both flavors are stringFlavor, in which case the
                //      equality test at the top of the function succeeded.
                //   2. Only one flavor is stringFlavor, in which case the MIME
                //      type comparison returned a non-zero value.
                if (doesSubtypeSupportCharset(flavor1)) {
                    // Next, prefer the decoded text representations of Reader,
                    // String, CharBuffer, and [C, in that order.
                    comp = compareIndices(decodedTextRepresentations, class1,
                                          class2, UNKNOWN_OBJECT_LOSES);
                    if (comp != 0) {
                        return comp;
                    }

                    // Next, compare charsets
                    comp = charsetComparator.compareCharsets
                        (DataTransferer.getTextCharset(flavor1),
                         DataTransferer.getTextCharset(flavor2));
                    if (comp != 0) {
                        return comp;
                    }
                }

                // Finally, prefer the encoded text representations of
                // InputStream, ByteBuffer, and [B, in that order.
                comp = compareIndices(encodedTextRepresentations, class1,
                                      class2, UNKNOWN_OBJECT_LOSES);
                if (comp != 0) {
                    return comp;
                }
            } else {
                // First, prefer text types
                if (flavor1.isFlavorTextType()) {
                    return 1;
                }

                if (flavor2.isFlavorTextType()) {
                    return -1;
                }

                // Next, prefer application types.
                comp = compareIndices(primaryTypes, primaryType1, primaryType2,
                        UNKNOWN_OBJECT_LOSES);
                if (comp != 0) {
                    return comp;
                }

                // Next, look for application/x-java-* types. Prefer unknown
                // MIME types because if the user provides his own data flavor,
                // it will likely be the most descriptive one.
                comp = compareIndices(exactTypes, mimeType1, mimeType2,
                                      UNKNOWN_OBJECT_WINS);
                if (comp != 0) {
                    return comp;
                }

                // Finally, prefer the representation classes of Remote,
                // Serializable, and InputStream, in that order.
                comp = compareIndices(nonTextRepresentations, class1, class2,
                                      UNKNOWN_OBJECT_LOSES);
                if (comp != 0) {
                    return comp;
                }
            }

            // The flavours are not equal but still not distinguishable.
            // Compare String representations in alphabetical order
            return flavor1.getMimeType().compareTo(flavor2.getMimeType());
        }
    }

    /*
     * Given the Map that maps objects to Integer indices and a boolean value,
     * this Comparator imposes a direct or reverse order on set of objects.
     * <p>
     * If the specified boolean value is SELECT_BEST, the Comparator imposes the
     * direct index-based order: an object A is greater than an object B if and
     * only if the index of A is greater than the index of B. An object that
     * doesn't have an associated index is less or equal than any other object.
     * <p>
     * If the specified boolean value is SELECT_WORST, the Comparator imposes the
     * reverse index-based order: an object A is greater than an object B if and
     * only if A is less than B with the direct index-based order.
     */
    public static class IndexOrderComparator extends IndexedComparator {
        private final Map indexMap;
        private static final Integer FALLBACK_INDEX =
            Integer.valueOf(Integer.MIN_VALUE);

        public IndexOrderComparator(Map indexMap) {
            super(SELECT_BEST);
            this.indexMap = indexMap;
        }

        public IndexOrderComparator(Map indexMap, boolean order) {
            super(order);
            this.indexMap = indexMap;
        }

        public int compare(Object obj1, Object obj2) {
            if (order == SELECT_WORST) {
                return -compareIndices(indexMap, obj1, obj2, FALLBACK_INDEX);
            } else {
                return compareIndices(indexMap, obj1, obj2, FALLBACK_INDEX);
            }
        }
    }

    /**
     * A class that provides access to java.rmi.Remote and java.rmi.MarshalledObject
     * without creating a static dependency.
     */
    private static class RMI {
        private static final Class<?> remoteClass = getClass("java.rmi.Remote");
        private static final Class<?> marshallObjectClass =
            getClass("java.rmi.MarshalledObject");
        private static final Constructor<?> marshallCtor =
            getConstructor(marshallObjectClass, Object.class);
        private static final Method marshallGet =
            getMethod(marshallObjectClass, "get");

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name, true, null);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Constructor<?> getConstructor(Class<?> c, Class<?>... types) {
            try {
                return (c == null) ? null : c.getDeclaredConstructor(types);
            } catch (NoSuchMethodException x) {
                throw new AssertionError(x);
            }
        }

        private static Method getMethod(Class<?> c, String name, Class<?>... types) {
            try {
                return (c == null) ? null : c.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Returns {@code true} if the given class is java.rmi.Remote.
         */
        static boolean isRemote(Class<?> c) {
            return (remoteClass == null) ? null : remoteClass.isAssignableFrom(c);
        }

        /**
         * Returns java.rmi.Remote.class if RMI is present; otherwise {@code null}.
         */
        static Class<?> remoteClass() {
            return remoteClass;
        }

        /**
         * Returns a new MarshalledObject containing the serialized representation
         * of the given object.
         */
        static Object newMarshalledObject(Object obj) throws IOException {
            try {
                return marshallCtor.newInstance(obj);
            } catch (InstantiationException x) {
                throw new AssertionError(x);
            } catch (IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException  x) {
                Throwable cause = x.getCause();
                if (cause instanceof IOException)
                    throw (IOException)cause;
                throw new AssertionError(x);
            }
        }

        /**
         * Returns a new copy of the contained marshalled object.
         */
        static Object getMarshalledObject(Object obj)
            throws IOException, ClassNotFoundException
        {
            try {
                return marshallGet.invoke(obj);
            } catch (IllegalAccessException x) {
                throw new AssertionError(x);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause instanceof IOException)
                    throw (IOException)cause;
                if (cause instanceof ClassNotFoundException)
                    throw (ClassNotFoundException)cause;
                throw new AssertionError(x);
            }
        }
    }
}
