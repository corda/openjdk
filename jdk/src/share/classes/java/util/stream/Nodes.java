/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * Factory methods for constructing implementations of {@link Node} and
 * {@link Node.Builder} and their primitive specializations.  Fork/Join tasks
 * for collecting output from a {@link PipelineHelper} to a {@link Node} and
 * flattening {@link Node}s.
 *
 * @since 1.8
 */
final class Nodes {

    private Nodes() {
        throw new Error("no instances");
    }

    /**
     * The maximum size of an array that can be allocated.
     */
    static final long MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    // IllegalArgumentException messages
    static final String BAD_SIZE = "Stream size exceeds max array size";

    @SuppressWarnings("rawtypes")
    private static final Node EMPTY_NODE = new EmptyNode.OfRef();
    private static final Node.OfInt EMPTY_INT_NODE = new EmptyNode.OfInt();
    private static final Node.OfLong EMPTY_LONG_NODE = new EmptyNode.OfLong();
    private static final Node.OfDouble EMPTY_DOUBLE_NODE = new EmptyNode.OfDouble();

    // General shape-based node creation methods

    /**
     * Produces an empty node whose count is zero, has no children and no content.
     *
     * @param <T> the type of elements of the created node
     * @param shape the shape of the node to be created
     * @return an empty node.
     */
    @SuppressWarnings("unchecked")
    static <T> Node<T> emptyNode(StreamShape shape) {
        switch (shape) {
            case REFERENCE:    return (Node<T>) EMPTY_NODE;
            case INT_VALUE:    return (Node<T>) EMPTY_INT_NODE;
            case LONG_VALUE:   return (Node<T>) EMPTY_LONG_NODE;
            case DOUBLE_VALUE: return (Node<T>) EMPTY_DOUBLE_NODE;
            default:
                throw new IllegalStateException("Unknown shape " + shape);
        }
    }

    /**
     * Produces a concatenated {@link Node} that has two or more children.
     * <p>The count of the concatenated node is equal to the sum of the count
     * of each child. Traversal of the concatenated node traverses the content
     * of each child in encounter order of the list of children. Splitting a
     * spliterator obtained from the concatenated node preserves the encounter
     * order of the list of children.
     *
     * <p>The result may be a concatenated node, the input sole node if the size
     * of the list is 1, or an empty node.
     *
     * @param <T> the type of elements of the concatenated node
     * @param shape the shape of the concatenated node to be created
     * @param left the left input node
     * @param right the right input node
     * @return a {@code Node} covering the elements of the input nodes
     * @throws IllegalStateException if all {@link Node} elements of the list
     * are an not instance of type supported by this factory.
     */
    @SuppressWarnings("unchecked")
    static <T> Node<T> conc(StreamShape shape, Node<T> left, Node<T> right) {
        switch (shape) {
            case REFERENCE:
                return new ConcNode<>(left, right);
            case INT_VALUE:
                return (Node<T>) new ConcNode.OfInt((Node.OfInt) left, (Node.OfInt) right);
            case LONG_VALUE:
                return (Node<T>) new ConcNode.OfLong((Node.OfLong) left, (Node.OfLong) right);
            case DOUBLE_VALUE:
                return (Node<T>) new ConcNode.OfDouble((Node.OfDouble) left, (Node.OfDouble) right);
            default:
                throw new IllegalStateException("Unknown shape " + shape);
        }
    }

    // Reference-based node methods

    /**
     * Produces a {@link Node} describing an array.
     *
     * <p>The node will hold a reference to the array and will not make a copy.
     *
     * @param <T> the type of elements held by the node
     * @param array the array
     * @return a node holding an array
     */
    static <T> Node<T> node(T[] array) {
        return new ArrayNode<>(array);
    }

    /**
     * Produces a {@link Node} describing a {@link Collection}.
     * <p>
     * The node will hold a reference to the collection and will not make a copy.
     *
     * @param <T> the type of elements held by the node
     * @param c the collection
     * @return a node holding a collection
     */
    static <T> Node<T> node(Collection<T> c) {
        return new CollectionNode<>(c);
    }

    /**
     * Produces a {@link Node.Builder}.
     *
     * @param exactSizeIfKnown -1 if a variable size builder is requested,
     * otherwise the exact capacity desired.  A fixed capacity builder will
     * fail if the wrong number of elements are added to the builder.
     * @param generator the array factory
     * @param <T> the type of elements of the node builder
     * @return a {@code Node.Builder}
     */
    static <T> Node.Builder<T> builder(long exactSizeIfKnown, IntFunction<T[]> generator) {
        return (exactSizeIfKnown >= 0 && exactSizeIfKnown < MAX_ARRAY_SIZE)
               ? new FixedNodeBuilder<>(exactSizeIfKnown, generator)
               : builder();
    }

    /**
     * Produces a variable size @{link Node.Builder}.
     *
     * @param <T> the type of elements of the node builder
     * @return a {@code Node.Builder}
     */
    static <T> Node.Builder<T> builder() {
        return new SpinedNodeBuilder<>();
    }

    // Int nodes

    /**
     * Produces a {@link Node.OfInt} describing an int[] array.
     *
     * <p>The node will hold a reference to the array and will not make a copy.
     *
     * @param array the array
     * @return a node holding an array
     */
    static Node.OfInt node(int[] array) {
        return new IntArrayNode(array);
    }

    /**
     * Produces a {@link Node.Builder.OfInt}.
     *
     * @param exactSizeIfKnown -1 if a variable size builder is requested,
     * otherwise the exact capacity desired.  A fixed capacity builder will
     * fail if the wrong number of elements are added to the builder.
     * @return a {@code Node.Builder.OfInt}
     */
    static Node.Builder.OfInt intBuilder(long exactSizeIfKnown) {
        return (exactSizeIfKnown >= 0 && exactSizeIfKnown < MAX_ARRAY_SIZE)
               ? new IntFixedNodeBuilder(exactSizeIfKnown)
               : intBuilder();
    }

    /**
     * Produces a variable size @{link Node.Builder.OfInt}.
     *
     * @return a {@code Node.Builder.OfInt}
     */
    static Node.Builder.OfInt intBuilder() {
        return new IntSpinedNodeBuilder();
    }

    // Long nodes

    /**
     * Produces a {@link Node.OfLong} describing a long[] array.
     * <p>
     * The node will hold a reference to the array and will not make a copy.
     *
     * @param array the array
     * @return a node holding an array
     */
    static Node.OfLong node(final long[] array) {
        return new LongArrayNode(array);
    }

    /**
     * Produces a {@link Node.Builder.OfLong}.
     *
     * @param exactSizeIfKnown -1 if a variable size builder is requested,
     * otherwise the exact capacity desired.  A fixed capacity builder will
     * fail if the wrong number of elements are added to the builder.
     * @return a {@code Node.Builder.OfLong}
     */
    static Node.Builder.OfLong longBuilder(long exactSizeIfKnown) {
        return (exactSizeIfKnown >= 0 && exactSizeIfKnown < MAX_ARRAY_SIZE)
               ? new LongFixedNodeBuilder(exactSizeIfKnown)
               : longBuilder();
    }

    /**
     * Produces a variable size @{link Node.Builder.OfLong}.
     *
     * @return a {@code Node.Builder.OfLong}
     */
    static Node.Builder.OfLong longBuilder() {
        return new LongSpinedNodeBuilder();
    }

    // Double nodes

    /**
     * Produces a {@link Node.OfDouble} describing a double[] array.
     *
     * <p>The node will hold a reference to the array and will not make a copy.
     *
     * @param array the array
     * @return a node holding an array
     */
    static Node.OfDouble node(final double[] array) {
        return new DoubleArrayNode(array);
    }

    /**
     * Produces a {@link Node.Builder.OfDouble}.
     *
     * @param exactSizeIfKnown -1 if a variable size builder is requested,
     * otherwise the exact capacity desired.  A fixed capacity builder will
     * fail if the wrong number of elements are added to the builder.
     * @return a {@code Node.Builder.OfDouble}
     */
    static Node.Builder.OfDouble doubleBuilder(long exactSizeIfKnown) {
        return (exactSizeIfKnown >= 0 && exactSizeIfKnown < MAX_ARRAY_SIZE)
               ? new DoubleFixedNodeBuilder(exactSizeIfKnown)
               : doubleBuilder();
    }

    /**
     * Produces a variable size @{link Node.Builder.OfDouble}.
     *
     * @return a {@code Node.Builder.OfDouble}
     */
    static Node.Builder.OfDouble doubleBuilder() {
        return new DoubleSpinedNodeBuilder();
    }

    // Implementations

    private static abstract class EmptyNode<T, T_ARR, T_CONS> implements Node<T> {
        EmptyNode() { }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            return generator.apply(0);
        }

        public void copyInto(T_ARR array, int offset) { }

        @Override
        public long count() {
            return 0;
        }

        public void forEach(T_CONS consumer) { }

        private static class OfRef<T> extends EmptyNode<T, T[], Consumer<? super T>> {
            private OfRef() {
                super();
            }

            @Override
            public Spliterator<T> spliterator() {
                return Spliterators.emptySpliterator();
            }
        }

        private static final class OfInt
                extends EmptyNode<Integer, int[], IntConsumer>
                implements Node.OfInt {

            OfInt() { } // Avoid creation of special accessor

            @Override
            public Spliterator.OfInt spliterator() {
                return Spliterators.emptyIntSpliterator();
            }

            @Override
            public int[] asPrimitiveArray() {
                return EMPTY_INT_ARRAY;
            }
        }

        private static final class OfLong
                extends EmptyNode<Long, long[], LongConsumer>
                implements Node.OfLong {

            OfLong() { } // Avoid creation of special accessor

            @Override
            public Spliterator.OfLong spliterator() {
                return Spliterators.emptyLongSpliterator();
            }

            @Override
            public long[] asPrimitiveArray() {
                return EMPTY_LONG_ARRAY;
            }
        }

        private static final class OfDouble
                extends EmptyNode<Double, double[], DoubleConsumer>
                implements Node.OfDouble {

            OfDouble() { } // Avoid creation of special accessor

            @Override
            public Spliterator.OfDouble spliterator() {
                return Spliterators.emptyDoubleSpliterator();
            }

            @Override
            public double[] asPrimitiveArray() {
                return EMPTY_DOUBLE_ARRAY;
            }
        }
    }

    /** Node class for a reference array */
    private static class ArrayNode<T> implements Node<T> {
        final T[] array;
        int curSize;

        @SuppressWarnings("unchecked")
        ArrayNode(long size, IntFunction<T[]> generator) {
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(BAD_SIZE);
            this.array = generator.apply((int) size);
            this.curSize = 0;
        }

        ArrayNode(T[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator<T> spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public void copyInto(T[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            if (array.length == curSize) {
                return array;
            } else {
                throw new IllegalStateException();
            }
        }

        @Override
        public long count() {
            return curSize;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        //

        @Override
        public String toString() {
            return String.format("ArrayNode[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    /** Node class for a Collection */
    private static final class CollectionNode<T> implements Node<T> {
        private final Collection<T> c;

        CollectionNode(Collection<T> c) {
            this.c = c;
        }

        // Node

        @Override
        public Spliterator<T> spliterator() {
            return c.stream().spliterator();
        }

        @Override
        public void copyInto(T[] array, int offset) {
            for (T t : c)
                array[offset++] = t;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] asArray(IntFunction<T[]> generator) {
            return c.toArray(generator.apply(c.size()));
        }

        @Override
        public long count() {
            return c.size();
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            c.forEach(consumer);
        }

        //

        @Override
        public String toString() {
            return String.format("CollectionNode[%d][%s]", c.size(), c);
        }
    }

    /**
     * Node class for an internal node with two or more children
     */
    private static abstract class AbstractConcNode<T, T_NODE extends Node<T>> implements Node<T> {
        protected final T_NODE left;
        protected final T_NODE right;
        private final long size;

        AbstractConcNode(T_NODE left, T_NODE right) {
            this.left = left;
            this.right = right;
            // The Node count will be required when the Node spliterator is
            // obtained and it is cheaper to aggressively calculate bottom up
            // as the tree is built rather than later on from the top down
            // traversing the tree
            this.size = left.count() + right.count();
        }

        @Override
        public int getChildCount() {
            return 2;
        }

        @Override
        public T_NODE getChild(int i) {
            if (i == 0) return left;
            if (i == 1) return right;
            throw new IndexOutOfBoundsException();
        }

        @Override
        public long count() {
            return size;
        }
    }

    static final class ConcNode<T>
            extends AbstractConcNode<T, Node<T>>
            implements Node<T> {

        ConcNode(Node<T> left, Node<T> right) {
            super(left, right);
        }

        @Override
        public Spliterator<T> spliterator() {
            return new Nodes.InternalNodeSpliterator.OfRef<>(this);
        }

        @Override
        public void copyInto(T[] array, int offset) {
            Objects.requireNonNull(array);
            left.copyInto(array, offset);
            // Cast to int is safe since it is the callers responsibility to
            // ensure that there is sufficient room in the array
            right.copyInto(array, offset + (int) left.count());
        }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            long size = count();
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(BAD_SIZE);
            T[] array = generator.apply((int) size);
            copyInto(array, 0);
            return array;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            left.forEach(consumer);
            right.forEach(consumer);
        }

        @Override
        public Node<T> truncate(long from, long to, IntFunction<T[]> generator) {
            if (from == 0 && to == count())
                return this;
            long leftCount = left.count();
            if (from >= leftCount)
                return right.truncate(from - leftCount, to - leftCount, generator);
            else if (to <= leftCount)
                return left.truncate(from, to, generator);
            else {
                return Nodes.conc(getShape(), left.truncate(from, leftCount, generator),
                                  right.truncate(0, to - leftCount, generator));
            }
        }

        @Override
        public String toString() {
            if (count() < 32) {
                return String.format("ConcNode[%s.%s]", left, right);
            } else {
                return String.format("ConcNode[size=%d]", count());
            }
        }

        private abstract static class OfPrimitive<E, T_CONS, T_ARR,
                                                  T_SPLITR extends Spliterator.OfPrimitive<E, T_CONS, T_SPLITR>,
                                                  T_NODE extends Node.OfPrimitive<E, T_CONS, T_ARR, T_SPLITR, T_NODE>>
                extends AbstractConcNode<E, T_NODE>
                implements Node.OfPrimitive<E, T_CONS, T_ARR, T_SPLITR, T_NODE> {

            OfPrimitive(T_NODE left, T_NODE right) {
                super(left, right);
            }

            @Override
            public void forEach(T_CONS consumer) {
                left.forEach(consumer);
                right.forEach(consumer);
            }

            @Override
            public void copyInto(T_ARR array, int offset) {
                left.copyInto(array, offset);
                // Cast to int is safe since it is the callers responsibility to
                // ensure that there is sufficient room in the array
                right.copyInto(array, offset + (int) left.count());
            }

            @Override
            public T_ARR asPrimitiveArray() {
                long size = count();
                if (size >= MAX_ARRAY_SIZE)
                    throw new IllegalArgumentException(BAD_SIZE);
                T_ARR array = newArray((int) size);
                copyInto(array, 0);
                return array;
            }

            @Override
            public String toString() {
                if (count() < 32)
                    return String.format("%s[%s.%s]", this.getClass().getName(), left, right);
                else
                    return String.format("%s[size=%d]", this.getClass().getName(), count());
            }
        }

        static final class OfInt
                extends ConcNode.OfPrimitive<Integer, IntConsumer, int[], Spliterator.OfInt, Node.OfInt>
                implements Node.OfInt {

            OfInt(Node.OfInt left, Node.OfInt right) {
                super(left, right);
            }

            @Override
            public Spliterator.OfInt spliterator() {
                return new InternalNodeSpliterator.OfInt(this);
            }
        }

        static final class OfLong
                extends ConcNode.OfPrimitive<Long, LongConsumer, long[], Spliterator.OfLong, Node.OfLong>
                implements Node.OfLong {

            OfLong(Node.OfLong left, Node.OfLong right) {
                super(left, right);
            }

            @Override
            public Spliterator.OfLong spliterator() {
                return new InternalNodeSpliterator.OfLong(this);
            }
        }

        static final class OfDouble
                extends ConcNode.OfPrimitive<Double, DoubleConsumer, double[], Spliterator.OfDouble, Node.OfDouble>
                implements Node.OfDouble {

            OfDouble(Node.OfDouble left, Node.OfDouble right) {
                super(left, right);
            }

            @Override
            public Spliterator.OfDouble spliterator() {
                return new InternalNodeSpliterator.OfDouble(this);
            }
        }
    }

    /** Abstract class for spliterator for all internal node classes */
    private static abstract class InternalNodeSpliterator<T,
                                                          S extends Spliterator<T>,
                                                          N extends Node<T>>
            implements Spliterator<T> {
        // Node we are pointing to
        // null if full traversal has occurred
        N curNode;

        // next child of curNode to consume
        int curChildIndex;

        // The spliterator of the curNode if that node is last and has no children.
        // This spliterator will be delegated to for splitting and traversing.
        // null if curNode has children
        S lastNodeSpliterator;

        // spliterator used while traversing with tryAdvance
        // null if no partial traversal has occurred
        S tryAdvanceSpliterator;

        // node stack used when traversing to search and find leaf nodes
        // null if no partial traversal has occurred
        Deque<N> tryAdvanceStack;

        InternalNodeSpliterator(N curNode) {
            this.curNode = curNode;
        }

        /**
         * Initiate a stack containing, in left-to-right order, the child nodes
         * covered by this spliterator
         */
        @SuppressWarnings("unchecked")
        protected final Deque<N> initStack() {
            // Bias size to the case where leaf nodes are close to this node
            // 8 is the minimum initial capacity for the ArrayDeque implementation
            Deque<N> stack = new ArrayDeque<>(8);
            for (int i = curNode.getChildCount() - 1; i >= curChildIndex; i--)
                stack.addFirst((N) curNode.getChild(i));
            return stack;
        }

        /**
         * Depth first search, in left-to-right order, of the node tree, using
         * an explicit stack, to find the next non-empty leaf node.
         */
        @SuppressWarnings("unchecked")
        protected final N findNextLeafNode(Deque<N> stack) {
            N n = null;
            while ((n = stack.pollFirst()) != null) {
                if (n.getChildCount() == 0) {
                    if (n.count() > 0)
                        return n;
                } else {
                    for (int i = n.getChildCount() - 1; i >= 0; i--)
                        stack.addFirst((N) n.getChild(i));
                }
            }

            return null;
        }

        @SuppressWarnings("unchecked")
        protected final boolean initTryAdvance() {
            if (curNode == null)
                return false;

            if (tryAdvanceSpliterator == null) {
                if (lastNodeSpliterator == null) {
                    // Initiate the node stack
                    tryAdvanceStack = initStack();
                    N leaf = findNextLeafNode(tryAdvanceStack);
                    if (leaf != null)
                        tryAdvanceSpliterator = (S) leaf.spliterator();
                    else {
                        // A non-empty leaf node was not found
                        // No elements to traverse
                        curNode = null;
                        return false;
                    }
                }
                else
                    tryAdvanceSpliterator = lastNodeSpliterator;
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public final S trySplit() {
            if (curNode == null || tryAdvanceSpliterator != null)
                return null; // Cannot split if fully or partially traversed
            else if (lastNodeSpliterator != null)
                return (S) lastNodeSpliterator.trySplit();
            else if (curChildIndex < curNode.getChildCount() - 1)
                return (S) curNode.getChild(curChildIndex++).spliterator();
            else {
                curNode = (N) curNode.getChild(curChildIndex);
                if (curNode.getChildCount() == 0) {
                    lastNodeSpliterator = (S) curNode.spliterator();
                    return (S) lastNodeSpliterator.trySplit();
                }
                else {
                    curChildIndex = 0;
                    return (S) curNode.getChild(curChildIndex++).spliterator();
                }
            }
        }

        @Override
        public final long estimateSize() {
            if (curNode == null)
                return 0;

            // Will not reflect the effects of partial traversal.
            // This is compliant with the specification
            if (lastNodeSpliterator != null)
                return lastNodeSpliterator.estimateSize();
            else {
                long size = 0;
                for (int i = curChildIndex; i < curNode.getChildCount(); i++)
                    size += curNode.getChild(i).count();
                return size;
            }
        }

        @Override
        public final int characteristics() {
            return Spliterator.SIZED;
        }

        private static final class OfRef<T>
                extends InternalNodeSpliterator<T, Spliterator<T>, Node<T>> {

            OfRef(Node<T> curNode) {
                super(curNode);
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> consumer) {
                if (!initTryAdvance())
                    return false;

                boolean hasNext = tryAdvanceSpliterator.tryAdvance(consumer);
                if (!hasNext) {
                    if (lastNodeSpliterator == null) {
                        // Advance to the spliterator of the next non-empty leaf node
                        Node<T> leaf = findNextLeafNode(tryAdvanceStack);
                        if (leaf != null) {
                            tryAdvanceSpliterator = leaf.spliterator();
                            // Since the node is not-empty the spliterator can be advanced
                            return tryAdvanceSpliterator.tryAdvance(consumer);
                        }
                    }
                    // No more elements to traverse
                    curNode = null;
                }
                return hasNext;
            }

            @Override
            public void forEachRemaining(Consumer<? super T> consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator == null) {
                        Deque<Node<T>> stack = initStack();
                        Node<T> leaf;
                        while ((leaf = findNextLeafNode(stack)) != null) {
                            leaf.forEach(consumer);
                        }
                        curNode = null;
                    }
                    else
                        lastNodeSpliterator.forEachRemaining(consumer);
                }
                else
                    while(tryAdvance(consumer)) { }
            }
        }

        private static abstract class OfPrimitive<T, T_CONS, T_ARR,
                                                  T_SPLITR extends Spliterator.OfPrimitive<T, T_CONS, T_SPLITR>,
                                                  N extends Node.OfPrimitive<T, T_CONS, T_ARR, T_SPLITR, N>>
                extends InternalNodeSpliterator<T, T_SPLITR, N>
                implements Spliterator.OfPrimitive<T, T_CONS, T_SPLITR> {

            OfPrimitive(N cur) {
                super(cur);
            }

            @Override
            public boolean tryAdvance(T_CONS consumer) {
                if (!initTryAdvance())
                    return false;

                boolean hasNext = tryAdvanceSpliterator.tryAdvance(consumer);
                if (!hasNext) {
                    if (lastNodeSpliterator == null) {
                        // Advance to the spliterator of the next non-empty leaf node
                        N leaf = findNextLeafNode(tryAdvanceStack);
                        if (leaf != null) {
                            tryAdvanceSpliterator = leaf.spliterator();
                            // Since the node is not-empty the spliterator can be advanced
                            return tryAdvanceSpliterator.tryAdvance(consumer);
                        }
                    }
                    // No more elements to traverse
                    curNode = null;
                }
                return hasNext;
            }

            @Override
            public void forEachRemaining(T_CONS consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator == null) {
                        Deque<N> stack = initStack();
                        N leaf;
                        while ((leaf = findNextLeafNode(stack)) != null) {
                            leaf.forEach(consumer);
                        }
                        curNode = null;
                    }
                    else
                        lastNodeSpliterator.forEachRemaining(consumer);
                }
                else
                    while(tryAdvance(consumer)) { }
            }
        }

        private static final class OfInt
                extends OfPrimitive<Integer, IntConsumer, int[], Spliterator.OfInt, Node.OfInt>
                implements Spliterator.OfInt {

            OfInt(Node.OfInt cur) {
                super(cur);
            }
        }

        private static final class OfLong
                extends OfPrimitive<Long, LongConsumer, long[], Spliterator.OfLong, Node.OfLong>
                implements Spliterator.OfLong {

            OfLong(Node.OfLong cur) {
                super(cur);
            }
        }

        private static final class OfDouble
                extends OfPrimitive<Double, DoubleConsumer, double[], Spliterator.OfDouble, Node.OfDouble>
                implements Spliterator.OfDouble {

            OfDouble(Node.OfDouble cur) {
                super(cur);
            }
        }
    }

    /**
     * Fixed-sized builder class for reference nodes
     */
    private static final class FixedNodeBuilder<T>
            extends ArrayNode<T>
            implements Node.Builder<T> {

        FixedNodeBuilder(long size, IntFunction<T[]> generator) {
            super(size, generator);
            assert size < MAX_ARRAY_SIZE;
        }

        @Override
        public Node<T> build() {
            if (curSize < array.length)
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d",
                                                              curSize, array.length));
            return this;
        }

        @Override
        public void begin(long size) {
            if (size != array.length)
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d",
                                                              size, array.length));
            curSize = 0;
        }

        @Override
        public void accept(T t) {
            if (curSize < array.length) {
                array[curSize++] = t;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d",
                                                              array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length)
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d",
                                                              curSize, array.length));
        }

        @Override
        public String toString() {
            return String.format("FixedNodeBuilder[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    /**
     * Variable-sized builder class for reference nodes
     */
    private static final class SpinedNodeBuilder<T>
            extends SpinedBuffer<T>
            implements Node<T>, Node.Builder<T> {
        private boolean building = false;

        SpinedNodeBuilder() {} // Avoid creation of special accessor

        @Override
        public Spliterator<T> spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(T t) {
            assert building : "not building";
            super.accept(t);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(T[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public T[] asArray(IntFunction<T[]> arrayFactory) {
            assert !building : "during building";
            return super.asArray(arrayFactory);
        }

        @Override
        public Node<T> build() {
            assert !building : "during building";
            return this;
        }
    }

    //

    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final long[] EMPTY_LONG_ARRAY = new long[0];
    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    private static class IntArrayNode implements Node.OfInt {
        final int[] array;
        int curSize;

        IntArrayNode(long size) {
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(BAD_SIZE);
            this.array = new int[(int) size];
            this.curSize = 0;
        }

        IntArrayNode(int[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator.OfInt spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public int[] asPrimitiveArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(int[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        @Override
        public void forEach(IntConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        @Override
        public String toString() {
            return String.format("IntArrayNode[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static class LongArrayNode implements Node.OfLong {
        final long[] array;
        int curSize;

        LongArrayNode(long size) {
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(BAD_SIZE);
            this.array = new long[(int) size];
            this.curSize = 0;
        }

        LongArrayNode(long[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        @Override
        public Spliterator.OfLong spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public long[] asPrimitiveArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(long[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        @Override
        public void forEach(LongConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        @Override
        public String toString() {
            return String.format("LongArrayNode[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static class DoubleArrayNode implements Node.OfDouble {
        final double[] array;
        int curSize;

        DoubleArrayNode(long size) {
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException(BAD_SIZE);
            this.array = new double[(int) size];
            this.curSize = 0;
        }

        DoubleArrayNode(double[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        @Override
        public Spliterator.OfDouble spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public double[] asPrimitiveArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(double[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        @Override
        public String toString() {
            return String.format("DoubleArrayNode[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static final class IntFixedNodeBuilder
            extends IntArrayNode
            implements Node.Builder.OfInt {

        IntFixedNodeBuilder(long size) {
            super(size);
            assert size < MAX_ARRAY_SIZE;
        }

        @Override
        public Node.OfInt build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d",
                                                              curSize, array.length));
            }

            return this;
        }

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d",
                                                              size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(int i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d",
                                                              array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d",
                                                              curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("IntFixedNodeBuilder[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static final class LongFixedNodeBuilder
            extends LongArrayNode
            implements Node.Builder.OfLong {

        LongFixedNodeBuilder(long size) {
            super(size);
            assert size < MAX_ARRAY_SIZE;
        }

        @Override
        public Node.OfLong build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d",
                                                              curSize, array.length));
            }

            return this;
        }

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d",
                                                              size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(long i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d",
                                                              array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d",
                                                              curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("LongFixedNodeBuilder[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static final class DoubleFixedNodeBuilder
            extends DoubleArrayNode
            implements Node.Builder.OfDouble {

        DoubleFixedNodeBuilder(long size) {
            super(size);
            assert size < MAX_ARRAY_SIZE;
        }

        @Override
        public Node.OfDouble build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d",
                                                              curSize, array.length));
            }

            return this;
        }

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d",
                                                              size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(double i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d",
                                                              array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d",
                                                              curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("DoubleFixedNodeBuilder[%d][%s]",
                                 array.length - curSize, Arrays.toString(array));
        }
    }

    private static final class IntSpinedNodeBuilder
            extends SpinedBuffer.OfInt
            implements Node.OfInt, Node.Builder.OfInt {
        private boolean building = false;

        IntSpinedNodeBuilder() {} // Avoid creation of special accessor

        @Override
        public Spliterator.OfInt spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(IntConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(int i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(int[] array, int offset) throws IndexOutOfBoundsException {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public int[] asPrimitiveArray() {
            assert !building : "during building";
            return super.asPrimitiveArray();
        }

        @Override
        public Node.OfInt build() {
            assert !building : "during building";
            return this;
        }
    }

    private static final class LongSpinedNodeBuilder
            extends SpinedBuffer.OfLong
            implements Node.OfLong, Node.Builder.OfLong {
        private boolean building = false;

        LongSpinedNodeBuilder() {} // Avoid creation of special accessor

        @Override
        public Spliterator.OfLong spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(LongConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(long i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(long[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public long[] asPrimitiveArray() {
            assert !building : "during building";
            return super.asPrimitiveArray();
        }

        @Override
        public Node.OfLong build() {
            assert !building : "during building";
            return this;
        }
    }

    private static final class DoubleSpinedNodeBuilder
            extends SpinedBuffer.OfDouble
            implements Node.OfDouble, Node.Builder.OfDouble {
        private boolean building = false;

        DoubleSpinedNodeBuilder() {} // Avoid creation of special accessor

        @Override
        public Spliterator.OfDouble spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(double i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(double[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public double[] asPrimitiveArray() {
            assert !building : "during building";
            return super.asPrimitiveArray();
        }

        @Override
        public Node.OfDouble build() {
            assert !building : "during building";
            return this;
        }
    }
}
