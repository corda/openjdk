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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time;

import static java.time.LocalTime.NANOS_PER_MINUTE;
import static java.time.LocalTime.NANOS_PER_SECOND;

import java.io.Serializable;
import java.util.Objects;
import java.util.TimeZone;

/**
 * A clock providing access to the current instant, date and time using a time-zone.
 * <p>
 * Instances of this class are used to find the current instant, which can be
 * interpreted using the stored time-zone to find the current date and time.
 * <p>
 * Use of a {@code Clock} is optional. All key date-time classes also have a
 * {@code now()} factory method that uses the system clock in the default time zone.
 * The primary purpose of this abstraction is to allow alternate clocks to be
 * plugged in as and when required. Applications use an object to obtain the
 * current time rather than a static method. This can simplify testing.
 * <p>
 * Best practice for applications is to pass a {@code Clock} into any method
 * that requires the current instant. A dependency injection framework is one
 * way to achieve this:
 * <pre>
 *  public class MyBean {
 *    private Clock clock;  // dependency inject
 *    ...
 *    public void process(LocalDate eventDate) {
 *      if (eventDate.isBefore(LocalDate.now(clock)) {
 *        ...
 *      }
 *    }
 *  }
 * </pre>
 * This approach allows an alternate clock, such as {@link #fixed(Instant, ZoneId) fixed}
 * or {@link #offset(Clock, Duration) offset} to be used during testing.
 * <p>
 * The {@code system} factory methods provide clocks based on the best available
 * system clock.
 *
 * @implSpec
 * This abstract class must be implemented with care to ensure other classes operate correctly.
 * All implementations that can be instantiated must be final, immutable and thread-safe.
 * <p>
 * The principal methods are defined to allow the throwing of an exception.
 * In normal use, no exceptions will be thrown, however one possible implementation would be to
 * obtain the time from a central time server across the network. Obviously, in this case the
 * lookup could fail, and so the method is permitted to throw an exception.
 * <p>
 * The returned instants from {@code Clock} work on a time-scale that ignores leap seconds,
 * as described in {@link Instant}. If the implementation wraps a source that provides leap
 * second information, then a mechanism should be used to "smooth" the leap second.
 * The Java Time-Scale mandates the use of UTC-SLS, however clock implementations may choose
 * how accurate they are with the time-scale so long as they document how they work.
 * Implementations are therefore not required to actually perform the UTC-SLS slew or to
 * otherwise be aware of leap seconds.
 * <p>
 * Implementations should implement {@code Serializable} wherever possible and must
 * document whether or not they do support serialization.
 *
 * @since 1.8
 */
public abstract class Clock {

    //-------------------------------------------------------------------------

    /**
     * Obtains a clock that returns instants from the specified clock truncated
     * to the nearest occurrence of the specified duration.
     * <p>
     * This clock will only tick as per the specified duration. Thus, if the duration
     * is half a second, the clock will return instants truncated to the half second.
     * <p>
     * The tick duration must be positive. If it has a part smaller than a whole
     * millisecond, then the whole duration must divide into one second without
     * leaving a remainder. All normal tick durations will match these criteria,
     * including any multiple of hours, minutes, seconds and milliseconds, and
     * sensible nanosecond durations, such as 20ns, 250,000ns and 500,000ns.
     * <p>
     * A duration of zero or one nanosecond would have no truncation effect.
     * Passing one of these will return the underlying clock.
     * <p>
     * Implementations may use a caching strategy for performance reasons.
     * As such, it is possible that the start of the requested duration observed
     * via this clock will be later than that observed directly via the underlying clock.
     * <p>
     * The returned implementation is immutable, thread-safe and {@code Serializable}
     * providing that the base clock is.
     *
     * @param baseClock  the base clock to base the ticking clock on, not null
     * @param tickDuration  the duration of each visible tick, not negative, not null
     * @return a clock that ticks in whole units of the duration, not null
     * @throws IllegalArgumentException if the duration is negative, or has a
     *  part smaller than a whole millisecond such that the whole duration is not
     *  divisible into one second
     * @throws ArithmeticException if the duration is too large to be represented as nanos
     */
    public static Clock tick(Clock baseClock, Duration tickDuration) {
        Objects.requireNonNull(baseClock, "baseClock");
        Objects.requireNonNull(tickDuration, "tickDuration");
        if (tickDuration.isNegative()) {
            throw new IllegalArgumentException("Tick duration must not be negative");
        }
        long tickNanos = tickDuration.toNanos();
        if (tickNanos % 1000_000 == 0) {
            // ok, no fraction of millisecond
        } else if (1000_000_000 % tickNanos == 0) {
            // ok, divides into one second without remainder
        } else {
            throw new IllegalArgumentException("Invalid tick duration");
        }
        if (tickNanos <= 1) {
            return baseClock;
        }
        return new TickClock(baseClock, tickNanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a clock that always returns the same instant.
     * <p>
     * This clock simply returns the specified instant.
     * As such, it is not a clock in the conventional sense.
     * The main use case for this is in testing, where the fixed clock ensures
     * tests are not dependent on the current clock.
     * <p>
     * The returned implementation is immutable, thread-safe and {@code Serializable}.
     *
     * @param fixedInstant  the instant to use as the clock, not null
     * @param zone  the time-zone to use to convert the instant to date-time, not null
     * @return a clock that always returns the same instant, not null
     */
    public static Clock fixed(Instant fixedInstant, ZoneId zone) {
        Objects.requireNonNull(fixedInstant, "fixedInstant");
        Objects.requireNonNull(zone, "zone");
        return new FixedClock(fixedInstant, zone);
    }

    //-------------------------------------------------------------------------
    /**
     * Obtains a clock that returns instants from the specified clock with the
     * specified duration added
     * <p>
     * This clock wraps another clock, returning instants that are later by the
     * specified duration. If the duration is negative, the instants will be
     * earlier than the current date and time.
     * The main use case for this is to simulate running in the future or in the past.
     * <p>
     * A duration of zero would have no offsetting effect.
     * Passing zero will return the underlying clock.
     * <p>
     * The returned implementation is immutable, thread-safe and {@code Serializable}
     * providing that the base clock is.
     *
     * @param baseClock  the base clock to add the duration to, not null
     * @param offsetDuration  the duration to add, not null
     * @return a clock based on the base clock with the duration added, not null
     */
    public static Clock offset(Clock baseClock, Duration offsetDuration) {
        Objects.requireNonNull(baseClock, "baseClock");
        Objects.requireNonNull(offsetDuration, "offsetDuration");
        if (offsetDuration.equals(Duration.ZERO)) {
            return baseClock;
        }
        return new OffsetClock(baseClock, offsetDuration);
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor accessible by subclasses.
     */
    protected Clock() {
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the time-zone being used to create dates and times.
     * <p>
     * A clock will typically obtain the current instant and then convert that
     * to a date or time using a time-zone. This method returns the time-zone used.
     *
     * @return the time-zone being used to interpret instants, not null
     */
    public abstract ZoneId getZone();

    /**
     * Returns a copy of this clock with a different time-zone.
     * <p>
     * A clock will typically obtain the current instant and then convert that
     * to a date or time using a time-zone. This method returns a clock with
     * similar properties but using a different time-zone.
     *
     * @param zone  the time-zone to change to, not null
     * @return a clock based on this clock with the specified time-zone, not null
     */
    public abstract Clock withZone(ZoneId zone);

    //-------------------------------------------------------------------------
    /**
     * Checks if this clock is equal to another clock.
     * <p>
     * Clocks should override this method to compare equals based on
     * their state and to meet the contract of {@link Object#equals}.
     * If not overridden, the behavior is defined by {@link Object#equals}
     *
     * @param obj  the object to check, null returns false
     * @return true if this is equal to the other clock
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * A hash code for this clock.
     * <p>
     * Clocks should override this method based on
     * their state and to meet the contract of {@link Object#hashCode}.
     * If not overridden, the behavior is defined by {@link Object#hashCode}
     *
     * @return a suitable hash code
     */
    @Override
    public  int hashCode() {
        return super.hashCode();
    }

    //-----------------------------------------------------------------------
    /**
     * Implementation of a clock that always returns the same instant.
     * This is typically used for testing.
     */
    static final class FixedClock extends Clock implements Serializable {
       private static final long serialVersionUID = 7430389292664866958L;
        private final Instant instant;
        private final ZoneId zone;

        FixedClock(Instant fixedInstant, ZoneId zone) {
            this.instant = fixedInstant;
            this.zone = zone;
        }
        @Override
        public ZoneId getZone() {
            return zone;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            if (zone.equals(this.zone)) {  // intentional NPE
                return this;
            }
            return new FixedClock(instant, zone);
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FixedClock) {
                FixedClock other = (FixedClock) obj;
                return instant.equals(other.instant) && zone.equals(other.zone);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return instant.hashCode() ^ zone.hashCode();
        }
        @Override
        public String toString() {
            return "FixedClock[" + instant + "," + zone + "]";
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Implementation of a clock that adds an offset to an underlying clock.
     */
    static final class OffsetClock extends Clock implements Serializable {
       private static final long serialVersionUID = 2007484719125426256L;
        private final Clock baseClock;
        private final Duration offset;

        OffsetClock(Clock baseClock, Duration offset) {
            this.baseClock = baseClock;
            this.offset = offset;
        }
        @Override
        public ZoneId getZone() {
            return baseClock.getZone();
        }
        @Override
        public Clock withZone(ZoneId zone) {
            if (zone.equals(baseClock.getZone())) {  // intentional NPE
                return this;
            }
            return new OffsetClock(baseClock.withZone(zone), offset);
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof OffsetClock) {
                OffsetClock other = (OffsetClock) obj;
                return baseClock.equals(other.baseClock) && offset.equals(other.offset);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return baseClock.hashCode() ^ offset.hashCode();
        }
        @Override
        public String toString() {
            return "OffsetClock[" + baseClock + "," + offset + "]";
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Implementation of a clock that adds an offset to an underlying clock.
     */
    static final class TickClock extends Clock implements Serializable {
        private static final long serialVersionUID = 6504659149906368850L;
        private final Clock baseClock;
        private final long tickNanos;

        TickClock(Clock baseClock, long tickNanos) {
            this.baseClock = baseClock;
            this.tickNanos = tickNanos;
        }
        @Override
        public ZoneId getZone() {
            return baseClock.getZone();
        }
        @Override
        public Clock withZone(ZoneId zone) {
            if (zone.equals(baseClock.getZone())) {  // intentional NPE
                return this;
            }
            return new TickClock(baseClock.withZone(zone), tickNanos);
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TickClock) {
                TickClock other = (TickClock) obj;
                return baseClock.equals(other.baseClock) && tickNanos == other.tickNanos;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return baseClock.hashCode() ^ ((int) (tickNanos ^ (tickNanos >>> 32)));
        }
        @Override
        public String toString() {
            return "TickClock[" + baseClock + "," + Duration.ofNanos(tickNanos) + "]";
        }
    }

}
