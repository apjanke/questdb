/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.cairo;

import com.questdb.common.*;
import com.questdb.std.*;
import com.questdb.std.microtime.DateFormatUtils;
import com.questdb.std.microtime.Dates;
import com.questdb.std.str.LPSZ;
import com.questdb.std.str.Path;
import com.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TableReaderTest extends AbstractCairoTest {
    public static final int MUST_SWITCH = 1;
    public static final int MUST_NOT_SWITCH = 2;
    public static final int DONT_CARE = 0;
    private static final int blobLen = 64 * 1024;
    private static final RecordAssert BATCH1_ASSERTER = (r, exp, ts, blob) -> {
        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(2));
        } else {
            Assert.assertEquals(0, r.getByte(2));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(8));
        } else {
            Assert.assertFalse(r.getBool(8));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(1));
        } else {
            Assert.assertEquals(0, r.getShort(1));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextInt(), r.getInt(0));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(0));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(3), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(4), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(4)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(5));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(5));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(10));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(10));
        }

        assertBin(r, exp, blob, 9);

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 6);
        } else {
            assertNullStr(r, 6);
        }

        if (exp.nextBoolean()) {
            TestUtils.assertEquals(exp.nextChars(7), r.getSym(7));
        } else {
            Assert.assertNull(r.getSym(7));
        }
    };

    private static final RecordAssert BATCH1_ASSERTER_NULL_BIN = (r, exp, ts, blob) -> {
        // same as BATCH1_ASSERTER + special treatment of "bin" column

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(2));
        } else {
            Assert.assertEquals(0, r.getByte(2));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(8));
        } else {
            Assert.assertFalse(r.getBool(8));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(1));
        } else {
            Assert.assertEquals(0, r.getShort(1));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextInt(), r.getInt(0));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(0));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(3), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(4), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(4)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(5));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(5));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(10));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(10));
        }

        // generate random bin for random generator state consistency
        if (exp.nextBoolean()) {
            exp.nextChars(blob, blobLen / 2);
        }

        Assert.assertEquals(-1, r.getBinLen(9));
        Assert.assertNull(r.getBin2(9));

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 6);
        } else {
            assertNullStr(r, 6);
        }

        if (exp.nextBoolean()) {
            TestUtils.assertEquals(exp.nextChars(7), r.getSym(7));
        } else {
            Assert.assertNull(r.getSym(7));
        }
    };
    private static final RecordAssert BATCH1_ASSERTER_NULL_SYM = (r, exp, ts, blob) -> {
        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(2));
        } else {
            Assert.assertEquals(0, r.getByte(2));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(8));
        } else {
            Assert.assertFalse(r.getBool(8));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(1));
        } else {
            Assert.assertEquals(0, r.getShort(1));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextInt(), r.getInt(0));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(0));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(3), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(4), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(4)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(5));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(5));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(10));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(10));
        }

        assertBin(r, exp, blob, 9);

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 6);
        } else {
            assertNullStr(r, 6);
        }

        if (exp.nextBoolean()) {
            exp.nextChars(7);
        }
        Assert.assertNull(r.getSym(7));
    };
    private static final RecordAssert BATCH1_ASSERTER_NULL_INT = (r, exp, ts, blob) -> {
        // same as BATCH1_ASSERTER + special treatment of int field
        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(2));
        } else {
            Assert.assertEquals(0, r.getByte(2));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(8));
        } else {
            Assert.assertFalse(r.getBool(8));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(1));
        } else {
            Assert.assertEquals(0, r.getShort(1));
        }

        if (exp.nextBoolean()) {
            exp.nextInt();
        }

        Assert.assertEquals(Numbers.INT_NaN, r.getInt(0));

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(3), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(4), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(4)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(5));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(5));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(10));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(10));
        }

        assertBin(r, exp, blob, 9);

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 6);
        } else {
            assertNullStr(r, 6);
        }

        if (exp.nextBoolean()) {
            TestUtils.assertEquals(exp.nextChars(7), r.getSym(7));
        } else {
            Assert.assertNull(r.getSym(7));
        }
    };
    private static final RecordAssert BATCH2_BEFORE_ASSERTER = (r, rnd, ts, blob) -> assertNullStr(r, 11);
    private static final RecordAssert BATCH1_7_ASSERTER = (r, exp, ts, blob) -> {
        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(1));
        } else {
            Assert.assertEquals(0, r.getByte(1));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(7));
        } else {
            Assert.assertFalse(r.getBool(7));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(0));
        } else {
            Assert.assertEquals(0, r.getShort(0));
        }

        if (exp.nextBoolean()) {
            exp.nextInt();
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(2), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(2)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(3), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(4));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(4));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(9));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(9));
        }

        assertBin(r, exp, blob, 8);

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 5);
        } else {
            assertNullStr(r, 5);
        }

        if (exp.nextBoolean()) {
            TestUtils.assertEquals(exp.nextChars(7), r.getSym(6));
        } else {
            Assert.assertNull(r.getSym(6));
        }

        Assert.assertEquals(Numbers.INT_NaN, r.getInt(20));
    };
    private static final RecordAssert BATCH1_9_ASSERTER = (r, exp, ts, blob) -> {
        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextByte(), r.getByte(1));
        } else {
            Assert.assertEquals(0, r.getByte(1));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextBoolean(), r.getBool(6));
        } else {
            Assert.assertFalse(r.getBool(6));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextShort(), r.getShort(0));
        } else {
            Assert.assertEquals(0, r.getShort(0));
        }

        if (exp.nextBoolean()) {
            exp.nextInt();
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextDouble(), r.getDouble(2), 0.00000001);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(2)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextFloat(), r.getFloat(3), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(3)));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(exp.nextLong(), r.getLong(4));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(4));
        }

        if (exp.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(8));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(8));
        }

        assertBin(r, exp, blob, 7);

        if (exp.nextBoolean()) {
            assertStrColumn(exp.nextChars(10), r, 5);
        } else {
            assertNullStr(r, 5);
        }

        // exercise random generator for column we removed
        if (exp.nextBoolean()) {
            exp.nextChars(7);
        }
        Assert.assertEquals(Numbers.INT_NaN, r.getInt(19));
    };
    private static final RecordAssert BATCH_2_7_BEFORE_ASSERTER = (r, rnd, ts, blob) -> assertNullStr(r, 10);
    private static final RecordAssert BATCH_2_9_BEFORE_ASSERTER = (r, rnd, ts, blob) -> assertNullStr(r, 9);
    private static final RecordAssert BATCH_3_7_BEFORE_ASSERTER = (r, rnd, ts, blob) -> Assert.assertEquals(Numbers.INT_NaN, r.getInt(11));
    private static final RecordAssert BATCH_3_9_BEFORE_ASSERTER = (r, rnd, ts, blob) -> Assert.assertEquals(Numbers.INT_NaN, r.getInt(10));
    private static final RecordAssert BATCH_4_7_BEFORE_ASSERTER = (r, rnd, ts, blob) -> {
        Assert.assertEquals(0, r.getShort(12));
        Assert.assertFalse(r.getBool(13));
        Assert.assertEquals(0, r.getByte(14));
        Assert.assertTrue(Float.isNaN(r.getFloat(15)));
        Assert.assertTrue(Double.isNaN(r.getDouble(16)));
        Assert.assertNull(r.getSym(17));
        Assert.assertEquals(Numbers.LONG_NaN, r.getLong(18));
        Assert.assertEquals(Numbers.LONG_NaN, r.getDate(19));
    };
    private static final RecordAssert BATCH_4_9_BEFORE_ASSERTER = (r, rnd, ts, blob) -> {
        Assert.assertEquals(0, r.getShort(11));
        Assert.assertFalse(r.getBool(12));
        Assert.assertEquals(0, r.getByte(13));
        Assert.assertTrue(Float.isNaN(r.getFloat(14)));
        Assert.assertTrue(Double.isNaN(r.getDouble(15)));
        Assert.assertNull(r.getSym(16));
        Assert.assertEquals(Numbers.LONG_NaN, r.getLong(17));
        Assert.assertEquals(Numbers.LONG_NaN, r.getDate(18));
    };
    private static final RecordAssert BATCH2_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH1_ASSERTER.assertRecord(r, rnd, ts, blob);
        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 11);
        }
    };
    private static final RecordAssert BATCH2_7_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH1_7_ASSERTER.assertRecord(r, rnd, ts, blob);
        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 10);
        }
    };
    private static final RecordAssert BATCH2_9_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH1_9_ASSERTER.assertRecord(r, rnd, ts, blob);
        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 9);
        }
    };
    private static final RecordAssert BATCH3_BEFORE_ASSERTER = (r, rnd, ts, blob) -> Assert.assertEquals(Numbers.INT_NaN, r.getInt(12));
    private static final RecordAssert BATCH3_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH2_ASSERTER.assertRecord(r, rnd, ts, blob);

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(12));
        }
    };
    private static final RecordAssert BATCH3_7_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH2_7_ASSERTER.assertRecord(r, rnd, ts, blob);

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(11));
        }
    };
    private static final RecordAssert BATCH3_9_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH2_9_ASSERTER.assertRecord(r, rnd, ts, blob);

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(10));
        }
    };
    private static final RecordAssert BATCH4_BEFORE_ASSERTER = (r, rnd, ts, blob) -> {
        Assert.assertEquals(0, r.getShort(13));
        Assert.assertFalse(r.getBool(14));
        Assert.assertEquals(0, r.getByte(15));
        Assert.assertTrue(Float.isNaN(r.getFloat(16)));
        Assert.assertTrue(Double.isNaN(r.getDouble(17)));
        Assert.assertNull(r.getSym(18));
        Assert.assertEquals(Numbers.LONG_NaN, r.getLong(19));
        Assert.assertEquals(Numbers.LONG_NaN, r.getDate(20));
        Assert.assertNull(r.getBin2(21));
        Assert.assertEquals(-1L, r.getBinLen(21));
    };
    private static final RecordAssert BATCH5_BEFORE_ASSERTER = (r, rnd, ts, blob) -> {
        Assert.assertEquals(0, r.getShort(13));
        Assert.assertFalse(r.getBool(14));
        Assert.assertEquals(0, r.getByte(15));
        Assert.assertTrue(Float.isNaN(r.getFloat(16)));
        Assert.assertTrue(Double.isNaN(r.getDouble(17)));
        Assert.assertNull(r.getSym(18));
        Assert.assertEquals(Numbers.LONG_NaN, r.getLong(19));
        Assert.assertEquals(Numbers.LONG_NaN, r.getDate(20));
    };
    private static final RecordAssert BATCH4_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH3_ASSERTER.assertRecord(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(13));
        } else {
            Assert.assertEquals(0, r.getShort(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(14));
        } else {
            Assert.assertFalse(r.getBool(14));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(15));
        } else {
            Assert.assertEquals(0, r.getByte(15));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(16), 0.00000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(16)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(17), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(17)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(18));
        } else {
            Assert.assertNull(r.getSym(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(19));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(19));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(20));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(20));
        }

        assertBin(r, rnd, blob, 21);
    };
    private static final RecordAssert BATCH6_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH3_ASSERTER.assertRecord(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(13));
        } else {
            Assert.assertEquals(0, r.getShort(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(14));
        } else {
            Assert.assertFalse(r.getBool(14));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(15));
        } else {
            Assert.assertEquals(0, r.getByte(15));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(16), 0.00000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(16)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(17), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(17)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(18));
        } else {
            Assert.assertNull(r.getSym(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(19));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(19));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(20));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(20));
        }
    };
    private static final RecordAssert BATCH6_7_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH3_7_ASSERTER.assertRecord(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(12));
        } else {
            Assert.assertEquals(0, r.getShort(12));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(13));
        } else {
            Assert.assertFalse(r.getBool(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(14));
        } else {
            Assert.assertEquals(0, r.getByte(14));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(15), 0.00000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(15)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(16), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(16)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(17));
        } else {
            Assert.assertNull(r.getSym(17));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(18));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(19));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(19));
        }
    };
    private static final RecordAssert BATCH6_9_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH3_9_ASSERTER.assertRecord(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(11));
        } else {
            Assert.assertEquals(0, r.getShort(11));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(12));
        } else {
            Assert.assertFalse(r.getBool(12));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(13));
        } else {
            Assert.assertEquals(0, r.getByte(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(14), 0.00000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(14)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(15), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(15)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(16));
        } else {
            Assert.assertNull(r.getSym(16));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(17));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(17));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(18));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(18));
        }
    };
    private static final RecordAssert BATCH5_7_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH6_7_ASSERTER.assertRecord(r, rnd, ts, blob);

        // generate blob to roll forward random generator, don't assert blob value
        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
        }
    };
    private static final RecordAssert BATCH5_9_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH6_9_ASSERTER.assertRecord(r, rnd, ts, blob);

        // generate blob to roll forward random generator, don't assert blob value
        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
        }
    };
    private static final RecordAssert BATCH5_ASSERTER = (r, rnd, ts, blob) -> {
        BATCH6_ASSERTER.assertRecord(r, rnd, ts, blob);

        // generate blob to roll forward random generator, don't assert blob value
        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
        }
    };
    private static final FieldGenerator BATCH1_GENERATOR = (r1, rnd1, ts1, blob1) -> {
        if (rnd1.nextBoolean()) {
            r1.putByte(2, rnd1.nextByte());
        }

        if (rnd1.nextBoolean()) {
            r1.putBool(8, rnd1.nextBoolean());
        }

        if (rnd1.nextBoolean()) {
            r1.putShort(1, rnd1.nextShort());
        }

        if (rnd1.nextBoolean()) {
            r1.putInt(0, rnd1.nextInt());
        }

        if (rnd1.nextBoolean()) {
            r1.putDouble(3, rnd1.nextDouble());
        }

        if (rnd1.nextBoolean()) {
            r1.putFloat(4, rnd1.nextFloat());
        }

        if (rnd1.nextBoolean()) {
            r1.putLong(5, rnd1.nextLong());
        }

        if (rnd1.nextBoolean()) {
            r1.putDate(10, ts1);
        }

        if (rnd1.nextBoolean()) {
            rnd1.nextChars(blob1, blobLen / 2);
            r1.putBin(9, blob1, blobLen);
        }

        if (rnd1.nextBoolean()) {
            r1.putStr(6, rnd1.nextChars(10));
        }

        if (rnd1.nextBoolean()) {
            r1.putSym(7, rnd1.nextChars(7));
        }
    };
    private static final RecordAssert BATCH8_ASSERTER = (r, rnd, ts, blob) -> {
        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(1));
        } else {
            Assert.assertEquals(0, r.getByte(1));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(7));
        } else {
            Assert.assertFalse(r.getBool(7));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(0));
        } else {
            Assert.assertEquals(0, r.getShort(0));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(2), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(2)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(3), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(3)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(4));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(4));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(9));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(9));
        }

        assertBin(r, rnd, blob, 8);

        if (rnd.nextBoolean()) {
            assertStrColumn(rnd.nextChars(10), r, 5);
        } else {
            assertNullStr(r, 5);
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(7), r.getSym(6));
        } else {
            Assert.assertNull(r.getSym(6));
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 10);
        } else {
            assertNullStr(r, 10);
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(11));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(11));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(12));
        } else {
            Assert.assertEquals(0, r.getShort(12));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(13));
        } else {
            Assert.assertFalse(r.getBool(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(14));
        } else {
            Assert.assertEquals(0, r.getByte(14));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(15), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(15)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(16), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(16)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(17));
        } else {
            Assert.assertNull(r.getSym(17));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(18));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(19));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(19));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(20));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(20));
        }
    };
    private static final RecordAssert BATCH8_9_ASSERTER = (r, rnd, ts, blob) -> {
        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(1));
        } else {
            Assert.assertEquals(0, r.getByte(1));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(6));
        } else {
            Assert.assertFalse(r.getBool(6));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(0));
        } else {
            Assert.assertEquals(0, r.getShort(0));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(2), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(2)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(3), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(3)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(4));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(4));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(8));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(8));
        }

        assertBin(r, rnd, blob, 7);

        if (rnd.nextBoolean()) {
            assertStrColumn(rnd.nextChars(10), r, 5);
        } else {
            assertNullStr(r, 5);
        }

        if (rnd.nextBoolean()) {
            rnd.nextChars(7);
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 9);
        } else {
            assertNullStr(r, 9);
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(10));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(10));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(11));
        } else {
            Assert.assertEquals(0, r.getShort(11));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(12));
        } else {
            Assert.assertFalse(r.getBool(12));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(13));
        } else {
            Assert.assertEquals(0, r.getByte(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(14), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(14)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(15), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(15)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(16));
        } else {
            Assert.assertNull(r.getSym(16));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(17));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(17));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(18));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(19));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(19));
        }

        Assert.assertNull(r.getSym(20));
    };
    private static final RecordAssert BATCH9_ASSERTER = (r, rnd, ts, blob) -> {
        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(1));
        } else {
            Assert.assertEquals(0, r.getByte(1));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(6));
        } else {
            Assert.assertFalse(r.getBool(6));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(0));
        } else {
            Assert.assertEquals(0, r.getShort(0));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(2), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(2)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(3), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(3)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(4));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(4));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(ts, r.getDate(8));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(8));
        }

        assertBin(r, rnd, blob, 7);

        if (rnd.nextBoolean()) {
            assertStrColumn(rnd.nextChars(10), r, 5);
        } else {
            assertNullStr(r, 5);
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            assertStrColumn(rnd.nextChars(15), r, 9);
        } else {
            assertNullStr(r, 9);
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(10));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(10));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextShort(), r.getShort(11));
        } else {
            Assert.assertEquals(0, r.getShort(11));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextBoolean(), r.getBool(12));
        } else {
            Assert.assertFalse(r.getBool(12));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextByte(), r.getByte(13));
        } else {
            Assert.assertEquals(0, r.getByte(13));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextFloat(), r.getFloat(14), 0.000001f);
        } else {
            Assert.assertTrue(Float.isNaN(r.getFloat(14)));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextDouble(), r.getDouble(15), 0.0000001d);
        } else {
            Assert.assertTrue(Double.isNaN(r.getDouble(15)));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(10), r.getSym(16));
        } else {
            Assert.assertNull(r.getSym(16));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getLong(17));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getLong(17));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextLong(), r.getDate(18));
        } else {
            Assert.assertEquals(Numbers.LONG_NaN, r.getDate(18));
        }

        if (rnd.nextBoolean()) {
            Assert.assertEquals(rnd.nextInt(), r.getInt(19));
        } else {
            Assert.assertEquals(Numbers.INT_NaN, r.getInt(19));
        }

        if (rnd.nextBoolean()) {
            TestUtils.assertEquals(rnd.nextChars(8), r.getSym(20));
        } else {
            Assert.assertNull(r.getSym(20));
        }
    };
    private static final FieldGenerator BATCH2_GENERATOR = (r1, rnd1, ts1, blob1) -> {
        BATCH1_GENERATOR.generate(r1, rnd1, ts1, blob1);

        if ((rnd1.nextPositiveInt() & 3) == 0) {
            r1.putStr(11, rnd1.nextChars(15));
        }
    };
    private static final FieldGenerator BATCH3_GENERATOR = (r1, rnd1, ts1, blob1) -> {
        BATCH2_GENERATOR.generate(r1, rnd1, ts1, blob1);

        if ((rnd1.nextPositiveInt() & 3) == 0) {
            r1.putInt(12, rnd1.nextInt());
        }
    };
    private static final FieldGenerator BATCH4_GENERATOR = (r, rnd, ts, blob) -> {
        BATCH3_GENERATOR.generate(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            r.putShort(13, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putBool(14, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putByte(15, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(16, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(17, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putSym(18, rnd.nextChars(10));
        }

        if (rnd.nextBoolean()) {
            r.putLong(19, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(20, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
            r.putBin(21, blob, blobLen);
        }
    };
    private static final FieldGenerator BATCH6_GENERATOR = (r, rnd, ts, blob) -> {
        BATCH3_GENERATOR.generate(r, rnd, ts, blob);

        if (rnd.nextBoolean()) {
            r.putShort(13, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putBool(14, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putByte(15, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(16, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(17, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putSym(18, rnd.nextChars(10));
        }

        if (rnd.nextBoolean()) {
            r.putLong(19, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(20, rnd.nextLong());
        }
    };
    private static final FieldGenerator BATCH8_GENERATOR = (r, rnd, ts, blob) -> {
        if (rnd.nextBoolean()) {
            r.putByte(1, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putBool(7, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putShort(0, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(2, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(3, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putLong(4, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(9, ts);
        }

        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
            r.putBin(8, blob, blobLen);
        }

        if (rnd.nextBoolean()) {
            r.putStr(5, rnd.nextChars(10));
        }

        if (rnd.nextBoolean()) {
            r.putSym(6, rnd.nextChars(7));
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            r.putStr(10, rnd.nextChars(15));
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            r.putInt(11, rnd.nextInt());
        }

        if (rnd.nextBoolean()) {
            r.putShort(12, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putBool(13, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putByte(14, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(15, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(16, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putSym(17, rnd.nextChars(10));
        }

        if (rnd.nextBoolean()) {
            r.putLong(18, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(19, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putInt(20, rnd.nextInt());
        }
    };
    private static final FieldGenerator BATCH9_GENERATOR = (r, rnd, ts, blob) -> {
        if (rnd.nextBoolean()) {
            r.putByte(1, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putBool(6, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putShort(0, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(2, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(3, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putLong(4, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(8, ts);
        }

        if (rnd.nextBoolean()) {
            rnd.nextChars(blob, blobLen / 2);
            r.putBin(7, blob, blobLen);
        }

        if (rnd.nextBoolean()) {
            r.putStr(5, rnd.nextChars(10));
        }


        if ((rnd.nextPositiveInt() & 3) == 0) {
            r.putStr(9, rnd.nextChars(15));
        }

        if ((rnd.nextPositiveInt() & 3) == 0) {
            r.putInt(10, rnd.nextInt());
        }

        if (rnd.nextBoolean()) {
            r.putShort(11, rnd.nextShort());
        }

        if (rnd.nextBoolean()) {
            r.putBool(12, rnd.nextBoolean());
        }

        if (rnd.nextBoolean()) {
            r.putByte(13, rnd.nextByte());
        }

        if (rnd.nextBoolean()) {
            r.putFloat(14, rnd.nextFloat());
        }

        if (rnd.nextBoolean()) {
            r.putDouble(15, rnd.nextDouble());
        }

        if (rnd.nextBoolean()) {
            r.putSym(16, rnd.nextChars(10));
        }

        if (rnd.nextBoolean()) {
            r.putLong(17, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putDate(18, rnd.nextLong());
        }

        if (rnd.nextBoolean()) {
            r.putInt(19, rnd.nextInt());
        }

        if (rnd.nextBoolean()) {
            r.putSym(20, rnd.nextChars(8));
        }
    };

    @Test
    public void testCloseColumnNonPartitioned1() throws Exception {
        testCloseColumn(PartitionBy.NONE, 2000, 6000L, "bin", BATCH1_ASSERTER_NULL_BIN);
    }

    @Test
    public void testCloseColumnNonPartitioned2() throws Exception {
        testCloseColumn(PartitionBy.NONE, 2000, 6000L, "int", BATCH1_ASSERTER_NULL_INT);
    }

    @Test
    public void testCloseColumnNonPartitioned3() throws Exception {
        testCloseColumn(PartitionBy.NONE, 2000, 6000L, "sym", BATCH1_ASSERTER_NULL_SYM);
    }

    @Test
    public void testCloseColumnPartitioned1() throws Exception {
        testCloseColumn(PartitionBy.DAY, 1000, 60000L, "bin", BATCH1_ASSERTER_NULL_BIN);
    }

    @Test
    public void testCloseColumnPartitioned2() throws Exception {
        testCloseColumn(PartitionBy.DAY, 1000, 60000L, "int", BATCH1_ASSERTER_NULL_INT);
    }

    @Test
    public void testCloseColumnPartitioned3() throws Exception {
        testCloseColumn(PartitionBy.DAY, 1000, 60000L, "sym", BATCH1_ASSERTER_NULL_SYM);
    }

    @Test
    public void testConcurrentReloadByDay() throws Exception {
        testConcurrentReloadSinglePartition(PartitionBy.DAY);
    }

    @Test
    public void testConcurrentReloadMultipleByDay() throws Exception {
        testConcurrentReloadMultiplePartitions(PartitionBy.DAY, 100000);
    }

    @Test
    public void testConcurrentReloadMultipleByMonth() throws Exception {
        testConcurrentReloadMultiplePartitions(PartitionBy.MONTH, 3000000);
    }

    @Test
    public void testConcurrentReloadMultipleByYear() throws Exception {
        testConcurrentReloadMultiplePartitions(PartitionBy.MONTH, 12 * 3000000);
    }

    public void testConcurrentReloadMultiplePartitions(int partitionBy, long stride) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            // model data
            LongList list = new LongList();
            final int N = 1024;
            final int scale = 10000;
            for (int i = 0; i < N; i++) {
                list.add(i);
            }

            // model table
            try (TableModel model = new TableModel(configuration, "w", partitionBy).col("l", ColumnType.LONG).timestamp()) {
                CairoTestUtils.create(model);
            }

            final int threads = 2;
            final CyclicBarrier startBarrier = new CyclicBarrier(threads);
            final CountDownLatch stopLatch = new CountDownLatch(threads);
            final AtomicInteger errors = new AtomicInteger(0);

            // start writer
            new Thread(() -> {
                try {
                    startBarrier.await();
                    long timestampUs = DateFormatUtils.parseDateTime("2017-12-11T00:00:00.000Z");
                    try (TableWriter writer = new TableWriter(configuration, "w")) {
                        for (int i = 0; i < N * scale; i++) {
                            TableWriter.Row row = writer.newRow(timestampUs);
                            row.putLong(0, list.getQuick(i % N));
                            row.append();
                            writer.commit();
                            timestampUs += stride;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    stopLatch.countDown();
                }
            }).start();

            // start reader
            new Thread(() -> {
                try {
                    startBarrier.await();
                    try (TableReader reader = new TableReader(configuration, "w")) {
                        RecordCursor cursor = reader.getCursor();
                        do {
                            // we deliberately ignore result of reload()
                            // to create more race conditions
                            reader.reload();
                            cursor.toTop();
                            int count = 0;
                            while (cursor.hasNext()) {
                                Assert.assertEquals(list.get(count++ % N), cursor.next().getLong(0));
                            }

                            if (count == N * scale) {
                                break;
                            }
                        } while (true);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    stopLatch.countDown();
                }
            }).start();

            Assert.assertTrue(stopLatch.await(30, TimeUnit.SECONDS));
            Assert.assertEquals(0, errors.get());

            // check that we had multiple partitions created during the test
            try (TableReader reader = new TableReader(configuration, "w")) {
                Assert.assertTrue(reader.getPartitionCount() > 10);
            }
        });
    }

    @Test
    public void testConcurrentReloadNonPartitioned() throws Exception {
        testConcurrentReloadSinglePartition(PartitionBy.NONE);
    }

    public void testConcurrentReloadSinglePartition(int partitionBy) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            // model data
            LongList list = new LongList();
            final int N = 1024;
            final int scale = 10000;
            for (int i = 0; i < N; i++) {
                list.add(i);
            }

            // model table
            try (TableModel model = new TableModel(configuration, "w", partitionBy).col("l", ColumnType.LONG)) {
                CairoTestUtils.create(model);
            }

            final int threads = 2;
            final CyclicBarrier startBarrier = new CyclicBarrier(threads);
            final CountDownLatch stopLatch = new CountDownLatch(threads);
            final AtomicInteger errors = new AtomicInteger(0);

            // start writer
            new Thread(() -> {
                try {
                    startBarrier.await();
                    try (TableWriter writer = new TableWriter(configuration, "w")) {
                        for (int i = 0; i < N * scale; i++) {
                            TableWriter.Row row = writer.newRow(0);
                            row.putLong(0, list.getQuick(i % N));
                            row.append();
                            writer.commit();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    stopLatch.countDown();
                }
            }).start();

            // start reader
            new Thread(() -> {
                try {
                    startBarrier.await();
                    try (TableReader reader = new TableReader(configuration, "w")) {
                        RecordCursor cursor = reader.getCursor();
                        do {
                            // we deliberately ignore result of reload()
                            // to create more race conditions
                            reader.reload();
                            cursor.toTop();
                            int count = 0;
                            while (cursor.hasNext()) {
                                Assert.assertEquals(list.get(count++ % N), cursor.next().getLong(0));
                            }

                            if (count == N * scale) {
                                break;
                            }
                        } while (true);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    stopLatch.countDown();
                }
            }).start();

            Assert.assertTrue(stopLatch.await(30, TimeUnit.SECONDS));
            Assert.assertEquals(0, errors.get());
        });
    }

    @Test
    public void testDummyFacade() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            CairoTestUtils.createAllTable(configuration, PartitionBy.NONE);
            try (TableReader reader = new TableReader(configuration, "all")) {
                Assert.assertNull(reader.getCursor().getStorageFacade());
            }
        });
    }

    @Test
    public void testNullValueRecovery() throws Exception {
        final String expected = "int\tshort\tbyte\tdouble\tfloat\tlong\tstr\tsym\tbool\tbin\tdate\n" +
                "NaN\t0\t0\tNaN\tNaN\tNaN\t\tabc\ttrue\t\t\n";

        TestUtils.assertMemoryLeak(() -> {
            CairoTestUtils.createAllTable(configuration, PartitionBy.NONE);

            try (TableWriter w = new TableWriter(configuration, "all")) {
                TableWriter.Row r = w.newRow(1000000); // <-- higher timestamp
                r.putInt(0, 10);
                r.putByte(1, (byte) 56);
                r.putDouble(2, 4.3223);
                r.putStr(6, "xyz");
                r.cancel();

                r = w.newRow(100000); // <-- lower timestamp
                r.putSym(7, "abc");
                r.putBool(8, true);
                r.append();

                w.commit();
            }

            try (TableReader r = new TableReader(configuration, "all")) {
                sink.clear();
                printer.print(r.getCursor(), true, r.getMetadata());
                TestUtils.assertEquals(expected, sink);
            }
        });
    }

    @Test
    public void testOver2GFile() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE)
                    .col("a", ColumnType.INT)) {
                CairoTestUtils.create(model);
            }

            long N = 280000000;
            Rnd rnd = new Rnd();
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < N; i++) {
                    TableWriter.Row r = writer.newRow(0);
                    r.putLong(0, rnd.nextLong());
                    r.append();
                }
                writer.commit();
            }

            try (TableReader reader = new TableReader(configuration, "x")) {
                int count = 0;
                rnd.reset();
                RecordCursor cursor = reader.getCursor();
                while (cursor.hasNext()) {
                    Record record = cursor.next();
                    Assert.assertEquals(rnd.nextLong(), record.getLong(0));
                    count++;
                }
                Assert.assertEquals(N, count);
            }
        });
    }

    @Test
    public void testPartialString() {
        CairoTestUtils.createAllTable(configuration, PartitionBy.NONE);
        int N = 10000;
        Rnd rnd = new Rnd();
        try (TableWriter writer = new TableWriter(configuration, "all")) {
            int col = writer.getMetadata().getColumnIndex("str");
            for (int i = 0; i < N; i++) {
                TableWriter.Row r = writer.newRow(0);
                CharSequence chars = rnd.nextChars(15);
                r.putStr(col, chars, 2, 10);
                r.append();
            }
            writer.commit();

            // add more rows for good measure and rollback

            for (int i = 0; i < N; i++) {
                TableWriter.Row r = writer.newRow(0);
                CharSequence chars = rnd.nextChars(15);
                r.putStr(col, chars, 2, 10);
                r.append();
            }
            writer.rollback();

            rnd.reset();

            try (TableReader reader = new TableReader(configuration, "all")) {
                col = reader.getMetadata().getColumnIndex("str");
                int count = 0;
                RecordCursor cursor = reader.getCursor();
                while (cursor.hasNext()) {
                    Record record = cursor.next();
                    CharSequence expected = rnd.nextChars(15);
                    CharSequence actual = record.getFlyweightStr(col);
                    Assert.assertTrue(Chars.equals(expected, 2, 10, actual, 0, 8));
                    count++;
                }
                Assert.assertEquals(N, count);
            }
        }
    }

    @Test
    public void testPartitionArchiveDoesNotExist() throws Exception {
        RecoverableTestFilesFacade ff = new RecoverableTestFilesFacade() {

            boolean called = false;

            @Override
            public boolean wasCalled() {
                return called;
            }

            @Override
            public boolean exists(LPSZ path) {
                if (!recovered && Chars.endsWith(path, TableUtils.ARCHIVE_FILE_NAME)) {
                    called = true;
                    return false;
                }
                return super.exists(path);
            }
        };
        testSwitchPartitionFail(ff);
    }

    @Test
    public void testPartitionArchiveDoesNotOpen() throws Exception {
        RecoverableTestFilesFacade ff = new RecoverableTestFilesFacade() {

            boolean called = false;

            @Override
            public boolean wasCalled() {
                return called;
            }

            @Override
            public long openRO(LPSZ name) {
                if (!recovered && Chars.endsWith(name, TableUtils.ARCHIVE_FILE_NAME)) {
                    called = true;
                    return -1L;
                }
                return super.openRO(name);
            }
        };
        testSwitchPartitionFail(ff);
    }

    @Test
    public void testPartitionCannotReadArchive() throws Exception {
        RecoverableTestFilesFacade ff = new RecoverableTestFilesFacade() {

            boolean called = false;
            long fd = -1L;

            @Override
            public boolean wasCalled() {
                return called;
            }

            @Override
            public long openRO(LPSZ name) {
                if (!recovered && Chars.endsWith(name, TableUtils.ARCHIVE_FILE_NAME)) {
                    called = true;
                    fd = super.openRO(name);
                    return fd;
                }
                return super.openRO(name);
            }

            @Override
            public long read(long fd, long buf, int len, long offset) {
                if (this.fd == fd && !recovered) {
                    return 0;
                }
                return super.read(fd, buf, len, offset);
            }
        };
        testSwitchPartitionFail(ff);
    }

    @Test
    public void testReadByDay() throws Exception {
        CairoTestUtils.createAllTable(configuration, PartitionBy.DAY);
        TestUtils.assertMemoryLeak(this::testTableCursor);
    }

    @Test
    public void testReadByMonth() throws Exception {
        CairoTestUtils.createAllTable(configuration, PartitionBy.MONTH);
        TestUtils.assertMemoryLeak(() -> testTableCursor(60 * 60 * 60000));
    }

    @Test
    public void testReadByYear() throws Exception {
        CairoTestUtils.createAllTable(configuration, PartitionBy.YEAR);
        TestUtils.assertMemoryLeak(() -> testTableCursor(24 * 60 * 60 * 60000L));
    }

    @Test
    public void testReadEmptyTable() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            CairoTestUtils.createAllTable(configuration, PartitionBy.NONE);
            try (TableWriter ignored1 = new TableWriter(configuration, "all")) {

                // open another writer, which should fail
                try {
                    new TableWriter(configuration, "all");
                    Assert.fail();
                } catch (CairoException ignored) {

                }

                try (TableReader reader = new TableReader(configuration, "all")) {
                    Assert.assertFalse(reader.getCursor().hasNext());
                }
            }
        });
    }

    @Test
    public void testReadNonPartitioned() throws Exception {
        CairoTestUtils.createAllTable(configuration, PartitionBy.NONE);
        TestUtils.assertMemoryLeak(this::testTableCursor);
    }

    @Test
    public void testReaderAndWriterRace() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE)) {
                CairoTestUtils.create(model.timestamp());
            }

            CountDownLatch stopLatch = new CountDownLatch(2);
            CyclicBarrier barrier = new CyclicBarrier(2);
            int count = 1000000;
            AtomicInteger reloadCount = new AtomicInteger(0);

            try (TableWriter writer = new TableWriter(configuration, "x"); TableReader reader = new TableReader(configuration, "x")) {

                new Thread(() -> {
                    try {
                        barrier.await();
                        for (int i = 0; i < count; i++) {
                            TableWriter.Row row = writer.newRow(i);
                            row.append();
                            writer.commit();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        stopLatch.countDown();
                    }

                }).start();

                new Thread(() -> {
                    try {
                        barrier.await();
                        int max = 0;
                        RecordCursor cursor = reader.getCursor();
                        while (max < count) {
                            if (reader.reload()) {
                                reloadCount.incrementAndGet();
                                cursor.toTop();
                                int localCount = 0;
                                while (cursor.hasNext()) {
                                    cursor.next();
                                    localCount++;
                                }
                                if (localCount > max) {
                                    max = localCount;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        stopLatch.countDown();
                    }
                }).start();

                stopLatch.await();

                Assert.assertTrue(reloadCount.get() > 0);
            }
        });
    }

    @Test
    public void testReloadByDaySwitch() throws Exception {
        testReload(PartitionBy.DAY, 150, 6 * 60000L, MUST_SWITCH);
    }

    @Test
    public void testReloadByMonthSamePartition() throws Exception {
        testReload(PartitionBy.MONTH, 15, 60L * 60000, MUST_NOT_SWITCH);
    }

    @Test
    public void testReloadByMonthSwitch() throws Exception {
        testReload(PartitionBy.MONTH, 15, 24 * 60L * 60000, MUST_SWITCH);
    }

    @Test
    public void testReloadByYearSamePartition() throws Exception {
        testReload(PartitionBy.YEAR, 100, 60 * 60000 * 24L, MUST_NOT_SWITCH);
    }

    @Test
    public void testReloadByYearSwitch() throws Exception {
        testReload(PartitionBy.YEAR, 200, 60 * 60000 * 24L, MUST_SWITCH);
    }

    @Test
    public void testReloadDaySamePartition() throws Exception {
        testReload(PartitionBy.DAY, 10, 60L * 60000, MUST_NOT_SWITCH);
    }

    @Test
    public void testReloadNonPartitioned() throws Exception {
        testReload(PartitionBy.NONE, 10, 60L * 60000, DONT_CARE);
    }

    @Test
    public void testReloadWithoutData() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (TableModel model = new TableModel(configuration, "tab", PartitionBy.DAY).col("x", ColumnType.SYMBOL).col("y", ColumnType.LONG)) {
                CairoTestUtils.create(model);
            }

            try (TableWriter writer = new TableWriter(configuration, "tab")) {
                TableWriter.Row r = writer.newRow(0);
                r.putSym(0, "hello");
                r.append();

                writer.rollback();

                try (TableReader reader = new TableReader(configuration, "tab")) {
                    writer.addColumn("z", ColumnType.SYMBOL);
                    Assert.assertTrue(reader.reload());
                    writer.addColumn("w", ColumnType.INT);
                    Assert.assertTrue(reader.reload());
                }
            }
        });
    }

    @Test
    public void testRemoveFirstPartitionByDay() throws Exception {
        testRemovePartition(PartitionBy.DAY, "2017-12-11", 0, current -> Dates.addDays(Dates.floorDD(current), 1));
    }

    @Test
    public void testRemoveFirstPartitionByMonth() throws Exception {
        testRemovePartition(PartitionBy.MONTH, "2017-12", 0, current -> Dates.addMonths(Dates.floorMM(current), 1));
    }

    @Test
    public void testRemoveFirstPartitionByYear() throws Exception {
        testRemovePartition(PartitionBy.YEAR, "2017", 0, current -> Dates.addYear(Dates.floorYYYY(current), 1));
    }

    public void testRemovePartition(int partitionBy, CharSequence partitionNameToDelete, int affectedBand, NextPartitionTimestampProvider provider) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 100;
            int N_PARTITIONS = 5;
            long timestampUs = DateFormatUtils.parseDateTime("2017-12-11T00:00:00.000Z");
            long stride = 100;
            int bandStride = 1000;
            int totalCount = 0;

            // model table
            try (TableModel model = new TableModel(configuration, "w", partitionBy).col("l", ColumnType.LONG).timestamp()) {
                CairoTestUtils.create(model);
            }

            try (TableWriter writer = new TableWriter(configuration, "w")) {

                for (int k = 0; k < N_PARTITIONS; k++) {
                    long band = k * bandStride;
                    for (int i = 0; i < N; i++) {
                        TableWriter.Row row = writer.newRow(timestampUs);
                        row.putLong(0, band + i);
                        row.append();
                        writer.commit();
                        timestampUs += stride;
                    }
                    timestampUs = provider.getNext(timestampUs);
                }
            }

            rmDir(partitionNameToDelete);

            // now open table reader having partition gap
            try (TableReader reader = new TableReader(configuration, "w")) {
                int previousBand = -1;
                int bandCount = 0;
                RecordCursor cursor = reader.getCursor();
                while (cursor.hasNext()) {
                    long value = cursor.next().getLong(0);
                    int band = (int) ((value / bandStride) * bandStride);
                    if (band != previousBand) {
                        // make sure we don#t pick up deleted partition
                        Assert.assertNotEquals(affectedBand, band);
                        if (previousBand != -1) {
                            Assert.assertEquals(N, bandCount);
                        }
                        previousBand = band;
                        bandCount = 0;
                    }
                    bandCount++;
                    totalCount++;
                }
                Assert.assertEquals(N, bandCount);
            }

            Assert.assertEquals(N * (N_PARTITIONS - 1), totalCount);
        });
    }

    @Test
    public void testRemovePartitionByDay() throws Exception {
        testRemovePartition(PartitionBy.DAY, "2017-12-14", 3000, current -> Dates.addDays(Dates.floorDD(current), 1));
    }

    @Test
    public void testRemovePartitionByMonth() throws Exception {
        testRemovePartition(PartitionBy.MONTH, "2018-01", 1000, current -> Dates.addMonths(Dates.floorMM(current), 1));
    }

    @Test
    public void testRemovePartitionByYear() throws Exception {
        testRemovePartition(PartitionBy.YEAR, "2020", 3000, current -> Dates.addYear(Dates.floorYYYY(current), 1));
    }

    @Test
    public void testSymbolIndex() throws Exception {
        String expected = "{\"columnCount\":3,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"SYMBOL\",\"indexed\":true},{\"index\":1,\"name\":\"b\",\"type\":\"INT\"},{\"index\":2,\"name\":\"timestamp\",\"type\":\"TIMESTAMP\"}]}";

        TestUtils.assertMemoryLeak(() -> {
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.DAY)
                    .col("a", ColumnType.SYMBOL).indexed(true, 2)
                    .col("b", ColumnType.INT)
                    .timestamp()) {
                CairoTestUtils.create(model);
            }

            int N = 1000;
            long ts = DateFormatUtils.parseDateTime("2018-01-06T10:00:00.000Z");
            final Rnd rnd = new Rnd();
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                sink.clear();
                writer.getMetadata().toJson(sink);
                TestUtils.assertEquals(expected, sink);

                for (int i = 0; i < N; i++) {
                    TableWriter.Row row = writer.newRow(ts + ((long) i) * 2 * 360000000L);
                    row.putSym(0, rnd.nextChars(3));
                    row.putInt(1, rnd.nextInt());
                    row.append();
                    writer.commit();
                }
            }

            try (TableReader reader = new TableReader(configuration, "x")) {
                sink.clear();
                reader.getMetadata().toJson(sink);
                TestUtils.assertEquals(expected, sink);
            }
        });
    }

    @Test
    public void testUnsuccessfulFileRemove() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.STRING).col("b", ColumnType.STRING)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 0;

                @Override
                public boolean remove(LPSZ name) {
                    if (Chars.endsWith(name, "b.i") || Chars.endsWith(name, "b.d")) {
                        counter++;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter > 0;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < N; i++) {
                    TableWriter.Row row = writer.newRow(0);
                    row.putStr(0, rnd.nextChars(10));
                    row.putStr(1, rnd.nextChars(15));
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getFlyweightStr(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getFlyweightStr(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    // this must fail because we cannot delete foreign files
                    try {
                        writer.addColumn("b", ColumnType.STRING);
                        Assert.fail();
                    } catch (CairoException e) {
                        TestUtils.assertContains(e.getMessage(), "Cannot remove");
                    }

                    // now assert what reader sees
                    Assert.assertTrue(reader.reload());
                    Assert.assertEquals(N, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getFlyweightStr(0));
                        // roll random generator to make sure it returns same values
                        rnd.nextChars(15);
                        counter++;
                    }

                    Assert.assertEquals(N * 2, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    @Test
    public void testUnsuccessfulFileRemoveAndReloadStr() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.SYMBOL).col("b", ColumnType.STRING)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 2;

                @Override
                public boolean remove(LPSZ name) {
                    if (counter > 0 && (
                            (
                                    Chars.endsWith(name, "b.i") ||
                                            Chars.endsWith(name, "b.d") ||
                                            Chars.endsWith(name, "b.o") ||
                                            Chars.endsWith(name, "b.k") ||
                                            Chars.endsWith(name, "b.c") ||
                                            Chars.endsWith(name, "b.v")
                            )
                    )) {
                        counter--;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter < 1;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < N; i++) {
                    TableWriter.Row row = writer.newRow(0);
                    row.putSym(0, rnd.nextChars(10));
                    row.putStr(1, rnd.nextChars(15));
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getFlyweightStr(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    if (configuration.getFilesFacade().isRestrictedFileSystem()) {
                        reader.closeColumnForRemove("b");
                    }

                    // now when we add new column by same name it must not pick up files we failed to delete previously
                    writer.addColumn("b", ColumnType.STRING);

                    for (int i = 0; i < N; i++) {
                        TableWriter.Row row = writer.newRow(0);
                        row.putSym(0, rnd.nextChars(10));
                        row.putStr(1, rnd.nextChars(15));
                        row.append();
                    }
                    writer.commit();

                    // now assert what reader sees
                    Assert.assertTrue(reader.reload());
                    Assert.assertEquals(N * 2, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    counter = 0;
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        if (counter < N) {
                            // roll random generator to make sure it returns same values
                            rnd.nextChars(15);
                            Assert.assertNull(record.getSym(1));
                        } else {
                            Assert.assertEquals(rnd.nextChars(15), record.getFlyweightStr(1));
                        }
                        counter++;
                    }

                    Assert.assertEquals(N * 2, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    @Test
    public void testUnsuccessfulRemoveAndReloadSym() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.SYMBOL).col("b", ColumnType.SYMBOL)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 5;

                @Override
                public boolean remove(LPSZ name) {
                    if (counter > 0 && (
                            (
                                    Chars.endsWith(name, "b.i") ||
                                            Chars.endsWith(name, "b.d") ||
                                            Chars.endsWith(name, "b.o") ||
                                            Chars.endsWith(name, "b.k") ||
                                            Chars.endsWith(name, "b.c") ||
                                            Chars.endsWith(name, "b.v")
                            )
                    )) {
                        counter--;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter < 1;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                appendTwoSymbols(writer, N, rnd);
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    if (configuration.getFilesFacade().isRestrictedFileSystem()) {
                        reader.closeColumnForRemove("b");
                    }

//                    reader.reload();

                    // now when we add new column by same name it must not pick up files we failed to delete previously
                    writer.addColumn("b", ColumnType.SYMBOL);

                    // SymbolMap must be cleared when we try do add values to new column
                    appendTwoSymbols(writer, N, rnd);
                    writer.commit();

                    // now assert what reader sees
                    Assert.assertTrue(reader.reload());
                    Assert.assertEquals(N * 2, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    counter = 0;
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        if (counter < N) {
                            // roll random generator to make sure it returns same values
                            rnd.nextChars(15);
                            Assert.assertNull(record.getSym(1));
                        } else {
                            Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        }
                        counter++;
                    }

                    Assert.assertEquals(N * 2, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    @Test
    public void testUnsuccessfulRemoveAndReloadSymTwice() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.SYMBOL).col("b", ColumnType.SYMBOL)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 5;

                @Override
                public boolean remove(LPSZ name) {
                    if (counter > 0 && (
                            (
                                    Chars.endsWith(name, "b.i") ||
                                            Chars.endsWith(name, "b.d") ||
                                            Chars.endsWith(name, "b.o") ||
                                            Chars.endsWith(name, "b.k") ||
                                            Chars.endsWith(name, "b.c") ||
                                            Chars.endsWith(name, "b.v")
                            )
                    )) {
                        counter--;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter < 1;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                appendTwoSymbols(writer, N, rnd);
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    reader.reload();

                    // now when we add new column by same name it must not pick up files we failed to delete previously
                    writer.addColumn("b", ColumnType.SYMBOL);

                    // SymbolMap must be cleared when we try do add values to new column
                    appendTwoSymbols(writer, N, rnd);
                    writer.commit();

                    // now assert what reader sees
                    Assert.assertTrue(reader.reload());
                    Assert.assertEquals(N * 2, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    counter = 0;
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        if (counter < N) {
                            // roll random generator to make sure it returns same values
                            rnd.nextChars(15);
                            Assert.assertNull(record.getSym(1));
                        } else {
                            Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        }
                        counter++;
                    }

                    Assert.assertEquals(N * 2, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    @Test
    public void testUnsuccessfulRemoveExplicitColCloseAndReloadSym() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.SYMBOL).col("b", ColumnType.SYMBOL)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 5;

                @Override
                public boolean remove(LPSZ name) {
                    if (counter > 0 && (
                            (
                                    Chars.endsWith(name, "b.i") ||
                                            Chars.endsWith(name, "b.d") ||
                                            Chars.endsWith(name, "b.o") ||
                                            Chars.endsWith(name, "b.k") ||
                                            Chars.endsWith(name, "b.c") ||
                                            Chars.endsWith(name, "b.v")
                            )
                    )) {
                        counter--;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter < 1;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                appendTwoSymbols(writer, N, rnd);
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    reader.closeColumnForRemove("b");

                    // now when we add new column by same name it must not pick up files we failed to delete previously
                    writer.addColumn("b", ColumnType.SYMBOL);

                    // SymbolMap must be cleared when we try do add values to new column
                    appendTwoSymbols(writer, N, rnd);
                    writer.commit();

                    // now assert what reader sees
                    Assert.assertTrue(reader.reload());
                    Assert.assertEquals(N * 2, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    counter = 0;
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        if (counter < N) {
                            // roll random generator to make sure it returns same values
                            rnd.nextChars(15);
                            Assert.assertNull(record.getSym(1));
                        } else {
                            Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        }
                        counter++;
                    }

                    Assert.assertEquals(N * 2, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    @Test
    public void testUnsuccessfulRemoveLastSym() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            // create table with two string columns
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).col("a", ColumnType.SYMBOL).col("b", ColumnType.SYMBOL)) {
                CairoTestUtils.create(model);
            }

            Rnd rnd = new Rnd();
            final int N = 1000;
            // make sure we forbid deleting column "b" files
            TestFilesFacade ff = new TestFilesFacade() {
                int counter = 5;

                @Override
                public boolean remove(LPSZ name) {
                    if (counter > 0 && (
                            (
                                    Chars.endsWith(name, "b.i") ||
                                            Chars.endsWith(name, "b.d") ||
                                            Chars.endsWith(name, "b.o") ||
                                            Chars.endsWith(name, "b.k") ||
                                            Chars.endsWith(name, "b.c") ||
                                            Chars.endsWith(name, "b.v")
                            )
                    )) {
                        counter--;
                        return false;
                    }
                    return super.remove(name);
                }

                @Override
                public boolean wasCalled() {
                    return counter < 1;
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            // populate table and delete column
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                appendTwoSymbols(writer, N, rnd);
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    long counter = 0;

                    rnd.reset();
                    RecordCursor cursor = reader.getCursor();
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        Assert.assertEquals(rnd.nextChars(15), record.getSym(1));
                        counter++;
                    }

                    Assert.assertEquals(N, counter);

                    // this should write metadata without column "b" but will ignore
                    // file delete failures
                    writer.removeColumn("b");

                    Assert.assertTrue(reader.reload());

                    Assert.assertEquals(N, reader.size());

                    rnd.reset();
                    cursor.toTop();
                    counter = 0;
                    while (cursor.hasNext()) {
                        Record record = cursor.next();
                        Assert.assertEquals(rnd.nextChars(10), record.getSym(0));
                        // roll random generator to make sure it returns same values
                        rnd.nextChars(15);
                        counter++;
                    }

                    Assert.assertEquals(N, counter);
                }
            }

            Assert.assertTrue(ff.wasCalled());
        });
    }

    private static long allocBlob() {
        return Unsafe.malloc(blobLen);
    }

    private static void freeBlob(long blob) {
        Unsafe.free(blob, blobLen);
    }

    private static void assertBin(Record r, Rnd exp, long blob, int index) {
        if (exp.nextBoolean()) {
            exp.nextChars(blob, blobLen / 2);
            Assert.assertEquals(blobLen, r.getBinLen(index));
            BinarySequence sq = r.getBin2(index);
            for (int l = 0; l < blobLen; l++) {
                byte b = sq.byteAt(l);
                boolean result = Unsafe.getUnsafe().getByte(blob + l) != b;
                if (result) {
                    Assert.fail("Error at [" + l + "]: expected=" + Unsafe.getUnsafe().getByte(blob + l) + ", actual=" + b);
                }
            }
        } else {
            Assert.assertEquals(-1, r.getBinLen(index));
        }
    }

    private static void assertStrColumn(CharSequence expected, Record r, int index) {
        TestUtils.assertEquals(expected, r.getFlyweightStr(index));
        TestUtils.assertEquals(expected, r.getFlyweightStrB(index));
        Assert.assertFalse(r.getFlyweightStr(index) == r.getFlyweightStrB(index));
        Assert.assertEquals(expected.length(), r.getStrLen(index));
    }

    private static void assertNullStr(Record r, int index) {
        Assert.assertNull(r.getFlyweightStr(index));
        Assert.assertNull(r.getFlyweightStrB(index));
        Assert.assertEquals(-1, r.getStrLen(index));
    }

    private static void rmDir(CharSequence partitionName) {
        try (Path path = new Path()) {
            path.of(root).concat("w").concat(partitionName).$();
            Assert.assertTrue(configuration.getFilesFacade().exists(path));
            Assert.assertTrue(configuration.getFilesFacade().rmdir(path));
        }
    }

    private void appendTwoSymbols(TableWriter writer, int n, Rnd rnd) {
        for (int i = 0; i < n; i++) {
            TableWriter.Row row = writer.newRow(0);
            row.putSym(0, rnd.nextChars(10));
            row.putSym(1, rnd.nextChars(15));
            row.append();
        }
    }

    private void assertBatch2(int count, long increment, long ts, long blob, TableReader reader) {
        RecordCursor cursor = reader.getCursor();
        Rnd exp = new Rnd();
        long ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd13, ts13, blob13) -> {
            BATCH1_ASSERTER.assertRecord(r, rnd13, ts13, blob13);
            BATCH2_BEFORE_ASSERTER.assertRecord(r, rnd13, ts13, blob13);
        });
        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH2_ASSERTER);
    }

    private void assertBatch3(int count, long increment, long ts, long blob, TableReader reader) {
        Rnd exp = new Rnd();
        long ts2;
        RecordCursor cursor = reader.getCursor();
        ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH2_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd12, ts12, blob12) -> {
            BATCH2_ASSERTER.assertRecord(r, rnd12, ts12, blob12);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd12, ts12, blob12);
        });

        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH3_ASSERTER);
    }

    private void assertBatch4(int count, long increment, long ts, long blob, TableReader reader) {
        Rnd exp;
        long ts2;
        exp = new Rnd();
        RecordCursor cursor = reader.getCursor();
        ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH2_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH4_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd12, ts12, blob12) -> {
            BATCH2_ASSERTER.assertRecord(r, rnd12, ts12, blob12);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd12, ts12, blob12);
            BATCH4_BEFORE_ASSERTER.assertRecord(r, rnd12, ts12, blob12);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd14, ts14, blob14) -> {
            BATCH4_BEFORE_ASSERTER.assertRecord(r, rnd14, ts14, blob14);
            BATCH3_ASSERTER.assertRecord(r, rnd14, ts14, blob14);
        });

        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH4_ASSERTER);
    }

    private long assertBatch5(int count, long increment, long ts, long blob, RecordCursor cursor, Rnd exp) {
        long ts2;

        cursor.toTop();
        ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH2_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH5_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH2_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH3_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH5_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH5_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH3_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        return assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH5_ASSERTER);
    }

    private void assertBatch6(int count, long increment, long ts, long blob, RecordCursor cursor) {
        Rnd exp;
        long ts2;
        exp = new Rnd();
        cursor.toTop();
        ts2 = assertBatch5(count, increment, ts, blob, cursor, exp);
        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH6_ASSERTER);
    }

    private void assertBatch7(int count, long increment, long ts, long blob, RecordCursor cursor) {
        cursor.toTop();
        Rnd exp = new Rnd();
        long ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_2_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH2_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH3_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH5_7_ASSERTER);
        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH6_7_ASSERTER);
    }

    private void assertBatch8(int count, long increment, long ts, long blob, RecordCursor cursor) {
        cursor.toTop();
        Rnd exp = new Rnd();
        long ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_2_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH2_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH3_7_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_7_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH5_7_ASSERTER);
        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH6_7_ASSERTER);
        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH8_ASSERTER);
    }

    private void assertBatch9(int count, long increment, long ts, long blob, RecordCursor cursor) {
        cursor.toTop();
        Rnd exp = new Rnd();
        long ts2 = assertPartialCursor(cursor, exp, ts, increment, blob, 3 * count, (r, rnd1, ts1, blob1) -> {
            BATCH1_9_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_2_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH2_9_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_3_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, (r, rnd1, ts1, blob1) -> {
            BATCH3_9_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
            BATCH_4_9_BEFORE_ASSERTER.assertRecord(r, rnd1, ts1, blob1);
        });

        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH5_9_ASSERTER);
        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH6_9_ASSERTER);
        ts2 = assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH8_9_ASSERTER);
        assertPartialCursor(cursor, exp, ts2, increment, blob, count, BATCH9_ASSERTER);
    }

    private void assertCursor(RecordCursor cursor, long ts, long increment, long blob, long expectedSize, RecordAssert asserter) {
        Rnd rnd = new Rnd();
//        Assert.assertEquals(expectedSize, cursor.size());
        cursor.toTop();
        int count = 0;
        long timestamp = ts;
        LongList rows = new LongList((int) expectedSize);

        while (cursor.hasNext() && count < expectedSize) {
            count++;
            Record rec = cursor.next();
            asserter.assertRecord(rec, rnd, timestamp += increment, blob);
            rows.add(rec.getRowId());
        }
        // did our loop run?
        Assert.assertEquals(expectedSize, count);

        // assert rowid access, method 1
        rnd.reset();
        timestamp = ts;
        for (int i = 0; i < count; i++) {
            Record rec = cursor.recordAt(rows.getQuick(i));
            asserter.assertRecord(rec, rnd, timestamp += increment, blob);
        }

        // assert rowid access, method 2
        rnd.reset();
        timestamp = ts;
        Record rec = cursor.getRecord();
        for (int i = 0; i < count; i++) {
            cursor.recordAt(rec, rows.getQuick(i));
            asserter.assertRecord(rec, rnd, timestamp += increment, blob);
        }

        // assert rowid access, method 3
        rnd.reset();
        timestamp = ts;
        rec = cursor.newRecord();
        for (int i = 0; i < count; i++) {
            cursor.recordAt(rec, rows.getQuick(i));
            asserter.assertRecord(rec, rnd, timestamp += increment, blob);
        }

        // courtesy call to no-op method
        cursor.releaseCursor();
    }

    private long assertPartialCursor(RecordCursor cursor, Rnd rnd, long ts, long increment, long blob, long expectedSize, RecordAssert asserter) {
        int count = 0;
        while (cursor.hasNext() && count < expectedSize) {
            count++;
            asserter.assertRecord(cursor.next(), rnd, ts += increment, blob);
        }
        // did our loop run?
        Assert.assertEquals(expectedSize, count);
        return ts;
    }

    private long testAppend(TableWriter writer, Rnd rnd, long ts, int count, long inc, long blob, int testPartitionSwitch, FieldGenerator generator) {
        long size = writer.size();

        long timestamp = writer.getMaxTimestamp();

        for (int i = 0; i < count; i++) {
            TableWriter.Row r = writer.newRow(ts += inc);
            generator.generate(r, rnd, ts, blob);
            r.append();
        }
        writer.commit();

        if (testPartitionSwitch == MUST_SWITCH) {
            Assert.assertFalse(CairoTestUtils.isSamePartition(timestamp, writer.getMaxTimestamp(), writer.getPartitionBy()));
        } else if (testPartitionSwitch == MUST_NOT_SWITCH) {
            Assert.assertTrue(CairoTestUtils.isSamePartition(timestamp, writer.getMaxTimestamp(), writer.getPartitionBy()));
        }

        Assert.assertEquals(size + count, writer.size());
        return ts;
    }

    private long testAppend(Rnd rnd, CairoConfiguration configuration, long ts, int count, long inc, long blob, int testPartitionSwitch, FieldGenerator generator) {
        try (TableWriter writer = new TableWriter(configuration, "all")) {
            return testAppend(writer, rnd, ts, count, inc, blob, testPartitionSwitch, generator);
        }
    }

    private void testCloseColumn(int partitionBy, int count, long increment, String column, RecordAssert assertAfter) throws Exception {
        final Rnd rnd = new Rnd();
        final LongList fds = new LongList();
        String dcol = column + ".d";
        String icol = column + ".i";

        TestFilesFacade ff = new TestFilesFacade() {

            boolean called = false;

            @Override
            public boolean close(long fd) {
                fds.remove(fd);
                return super.close(fd);
            }

            @Override
            public boolean wasCalled() {
                return called;
            }

            @Override
            public long openRO(LPSZ name) {
                long fd = super.openRO(name);
                if (Chars.endsWith(name, dcol) || Chars.endsWith(name, icol)) {
                    fds.add(fd);
                    called = true;
                }
                return fd;
            }


        };

        long blob = allocBlob();
        try {
            TestUtils.assertMemoryLeak(() -> {
                CairoTestUtils.createAllTable(configuration, partitionBy);
                long ts = DateFormatUtils.parseDateTime("2013-03-04T00:00:00.000Z");

                CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                    @Override
                    public FilesFacade getFilesFacade() {
                        return ff;
                    }
                };
                testAppend(rnd, configuration, ts, count, increment, blob, 0, BATCH1_GENERATOR);

                try (TableReader reader = new TableReader(configuration, "all")) {
                    RecordCursor cursor = reader.getCursor();
                    assertCursor(cursor, ts, increment, blob, count, BATCH1_ASSERTER);
                    reader.closeColumnForRemove(column);
                    assertCursor(cursor, ts, increment, blob, count, assertAfter);
                }

                Assert.assertTrue(ff.wasCalled());
                Assert.assertEquals(0, fds.size());
            });
        } finally {
            freeBlob(blob);
        }
    }

    private void testReload(int partitionBy, int count, long inct, final int testPartitionSwitch) throws Exception {
        final long increment = inct * 1000;

        CairoTestUtils.createAllTable(configuration, partitionBy);

        TestUtils.assertMemoryLeak(() -> {
            Rnd rnd = new Rnd();

            long ts = DateFormatUtils.parseDateTime("2013-03-04T00:00:00.000Z");

            long blob = allocBlob();
            try {

                // test if reader behaves correctly when table is empty

                try (TableReader reader = new TableReader(configuration, "all")) {
                    // can we reload empty table?
                    Assert.assertFalse(reader.reload());
                    // reader can see all the rows ? Meaning none?
                    assertCursor(reader.getCursor(), ts, increment, blob, 0, null);

                }

                try (TableReader reader = new TableReader(configuration, "all")) {

                    RecordCursor cursor = reader.getCursor();
                    // this combination of reload/iterate/reload is deliberate
                    // we make sure that reload() behavior is not affected by
                    // iterating empty result set
                    Assert.assertFalse(reader.reload());
                    assertCursor(cursor, ts, increment, blob, 0, null);
                    Assert.assertFalse(reader.reload());

                    // create table with first batch populating all columns (there could be null values too)
                    long nextTs = testAppend(rnd, configuration, ts, count, increment, blob, 0, BATCH1_GENERATOR);

                    // can we reload from empty to first batch?
                    Assert.assertTrue(reader.reload());

                    // make sure we can see first batch right after table is open
                    assertCursor(cursor, ts, increment, blob, count, BATCH1_ASSERTER);

                    // create another reader to make sure it can load data from constructor
                    try (TableReader reader2 = new TableReader(configuration, "all")) {
                        // make sure we can see first batch right after table is open
                        assertCursor(reader2.getCursor(), ts, increment, blob, count, BATCH1_ASSERTER);
                    }

                    // try reload when table hasn't changed
                    Assert.assertFalse(reader.reload());

                    // add second batch to test if reload of open table will pick it up
                    nextTs = testAppend(rnd, configuration, nextTs, count, increment, blob, testPartitionSwitch, BATCH1_GENERATOR);

                    // if we don't reload reader it should still see first batch
                    // reader can see all the rows ?
                    cursor.toTop();
                    assertPartialCursor(cursor, new Rnd(), ts, increment, blob, count / 4, BATCH1_ASSERTER);

                    // reload should be successful because we have new data in the table
                    Assert.assertTrue(reader.reload());

                    // check if we can see second batch after reader was reloaded
                    assertCursor(cursor, ts, increment, blob, 2 * count, BATCH1_ASSERTER);

                    // writer will inflate last partition in order to optimise appends
                    // reader must be able to cope with that
                    try (TableWriter writer = new TableWriter(configuration, "all")) {

                        // this is a bit of paranoid check, but make sure our reader doesn't flinch when new writer is open
                        assertCursor(cursor, ts, increment, blob, 2 * count, BATCH1_ASSERTER);

                        // also make sure that there is nothing to reload, we've not done anything to data after all
                        Assert.assertFalse(reader.reload());

                        // check that we can still see two batches after no-op reload
                        // we rule out possibility of reload() corrupting table state
                        assertCursor(cursor, ts, increment, blob, 2 * count, BATCH1_ASSERTER);

                        // just for no reason add third batch
                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH1_GENERATOR);

                        // table must be able to reload now
                        Assert.assertTrue(reader.reload());

                        // and we should see three batches of data
                        assertCursor(cursor, ts, increment, blob, 3 * count, BATCH1_ASSERTER);

                        // this is where things get interesting
                        // add single column
                        writer.addColumn("str2", ColumnType.STRING);

                        // populate table with fourth batch, this time we also populate new column
                        // we expect that values of new column will be NULL for first three batches and non-NULL for fourth
                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH2_GENERATOR);

                        // reload table, check if it was positive effort
                        Assert.assertTrue(reader.reload());

                        // two-step assert checks 3/4 rows checking that new column is NUL
                        // the last 1/3 is checked including new column
                        // this is why we need to use same random state and timestamp
                        assertBatch2(count, increment, ts, blob, reader);

                        // good job we got as far as this
                        // now add another column and populate fifth batch, including new column
                        // reading this table will ensure tops are preserved

                        writer.addColumn("int2", ColumnType.INT);

                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH3_GENERATOR);

                        Assert.assertTrue(reader.reload());

                        assertBatch3(count, increment, ts, blob, reader);

                        // now append more columns that would overflow column buffer and force table to use different
                        // algo when retaining resources

                        writer.addColumn("short2", ColumnType.SHORT);
                        writer.addColumn("bool2", ColumnType.BOOLEAN);
                        writer.addColumn("byte2", ColumnType.BYTE);
                        writer.addColumn("float2", ColumnType.FLOAT);
                        writer.addColumn("double2", ColumnType.DOUBLE);
                        writer.addColumn("sym2", ColumnType.SYMBOL);
                        writer.addColumn("long2", ColumnType.LONG);
                        writer.addColumn("date2", ColumnType.DATE);
                        writer.addColumn("bin2", ColumnType.BINARY);

                        // populate new columns and start asserting batches, which would assert that new columns are
                        // retrospectively "null" in existing records
                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH4_GENERATOR);

                        Assert.assertTrue(reader.reload());

                        assertBatch4(count, increment, ts, blob, reader);

                        // now delete last column

                        if (configuration.getFilesFacade().isRestrictedFileSystem()) {
                            reader.closeColumnForRemove("bin2");
                        }

                        writer.removeColumn("bin2");

                        Assert.assertTrue(reader.reload());

                        // and assert that all columns that have not been deleted contain correct values

                        assertBatch5(count, increment, ts, blob, cursor, new Rnd());

                        // append all columns excluding the one we just deleted
                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH6_GENERATOR);

                        Assert.assertTrue(reader.reload());

                        // and assert that all columns that have not been deleted contain correct values
                        assertBatch6(count, increment, ts, blob, cursor);

                        if (configuration.getFilesFacade().isRestrictedFileSystem()) {
                            reader.closeColumnForRemove("int");
                        }

//                        writer.getMetadata().toJson(sink);
//                        System.out.println("before:");
//                        System.out.println(sink);

                        // remove first column and add new column by same name
                        writer.removeColumn("int");
                        writer.addColumn("int", ColumnType.INT);

                        Assert.assertTrue(reader.reload());

                        assertBatch7(count, increment, ts, blob, cursor);

                        Assert.assertFalse(reader.reload());

                        nextTs = testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH8_GENERATOR);

                        Assert.assertTrue(reader.reload());

                        assertBatch8(count, increment, ts, blob, cursor);

                        if (configuration.getFilesFacade().isRestrictedFileSystem()) {
                            reader.closeColumnForRemove("sym");
                        }

                        writer.removeColumn("sym");
                        writer.addColumn("sym", ColumnType.SYMBOL);
                        Assert.assertTrue(reader.reload());

                        testAppend(writer, rnd, nextTs, count, increment, blob, 0, BATCH9_GENERATOR);

                        Assert.assertTrue(reader.reload());

                        assertBatch9(count, increment, ts, blob, cursor);

                    }
                }
            } finally {
                freeBlob(blob);
            }
        });
    }

    private void testSwitchPartitionFail(RecoverableTestFilesFacade ff) throws Exception {
        final Rnd rnd = new Rnd();

        int count = 1000;
        long increment = 60 * 60000L * 1000L;
        long blob = allocBlob();
        try {
            TestUtils.assertMemoryLeak(() -> {
                CairoTestUtils.createAllTable(configuration, PartitionBy.DAY);
                long ts = DateFormatUtils.parseDateTime("2013-03-04T00:00:00.000Z");
                CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                    @Override
                    public FilesFacade getFilesFacade() {
                        return ff;
                    }
                };
                testAppend(rnd, configuration, ts, count, increment, blob, 0, BATCH1_GENERATOR);

                try (TableReader reader = new TableReader(configuration, "all")) {
                    RecordCursor cursor = reader.getCursor();
                    try {
                        assertCursor(cursor, ts, increment, blob, count, BATCH1_ASSERTER);
                        Assert.fail();
                    } catch (CairoException ignored) {
                    }
                    ff.setRecovered(true);
                    assertCursor(cursor, ts, increment, blob, count, BATCH1_ASSERTER);
                }
                Assert.assertTrue(ff.wasCalled());
            });
        } finally {
            freeBlob(blob);
        }
    }

    private void testTableCursor(long inc) throws NumericException {
        Rnd rnd = new Rnd();
        int N = 100;
        long ts = DateFormatUtils.parseDateTime("2013-03-04T00:00:00.000Z") / 1000;
        long blob = allocBlob();
        try {
            testAppend(rnd, configuration, ts, N, inc, blob, 0, BATCH1_GENERATOR);
            final LongList rows = new LongList();
            try (TableReader reader = new TableReader(configuration, "all")) {
                Assert.assertEquals(N, reader.size());

                RecordCursor cursor = reader.getCursor();
                assertCursor(cursor, ts, inc, blob, N, BATCH1_ASSERTER);

                cursor.toTop();
                while (cursor.hasNext()) {
                    rows.add(cursor.next().getRowId());
                }

                Rnd exp = new Rnd();
                for (int i = 0, n = rows.size(); i < n; i++) {
                    BATCH1_ASSERTER.assertRecord(cursor.recordAt(rows.getQuick(i)), exp, ts += inc, blob);
                }
            }
        } finally {
            freeBlob(blob);
        }
    }

    private void testTableCursor() throws NumericException {
        testTableCursor(60 * 60000);
    }

    @FunctionalInterface
    private interface NextPartitionTimestampProvider {
        long getNext(long current);
    }

    private interface RecordAssert {
        void assertRecord(Record r, Rnd rnd, long ts, long blob);
    }

    @FunctionalInterface
    private interface FieldGenerator {
        void generate(TableWriter.Row r, Rnd rnd, long ts, long blob);
    }

    private abstract class RecoverableTestFilesFacade extends TestFilesFacade {
        protected boolean recovered = false;

        public void setRecovered(boolean recovered) {
            this.recovered = recovered;
        }
    }
}