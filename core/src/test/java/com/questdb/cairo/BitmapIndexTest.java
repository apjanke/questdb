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

import com.questdb.std.*;
import com.questdb.std.str.Path;
import com.questdb.test.tools.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BitmapIndexTest extends AbstractCairoTest {

    private Path path;
    private int plen;

    public static void create(CairoConfiguration configuration, Path path, CharSequence name, int valueBlockCapacity) {
        int plen = path.length();
        try {
            FilesFacade ff = configuration.getFilesFacade();
            try (AppendMemory mem = new AppendMemory(ff, BitmapIndexUtils.keyFileName(path, name), ff.getPageSize())) {
                BitmapIndexWriter.initKeyMemory(mem, valueBlockCapacity);
            }
            ff.touch(BitmapIndexUtils.valueFileName(path.trimTo(plen), name));
        } finally {
            path.trimTo(plen);
        }
    }

    @Before
    public void setUp0() {
        path = new Path().of(configuration.getRoot());
        plen = path.length();
        super.setUp0();
    }

    @Override
    @After
    public void tearDown0() {
        Misc.free(path);
        super.tearDown0();
    }

    @Test
    public void testAdd() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            LongList list = new LongList();
            create(configuration, path.trimTo(plen), "x", 4);
            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path, "x")) {
                writer.add(0, 1000);
                writer.add(256, 1234);
                writer.add(64, 10);
                writer.add(64, 987);
                writer.add(256, 5567);
                writer.add(64, 91);
                writer.add(64, 92);
                writer.add(64, 93);

                assertThat("[5567,1234]", writer.getCursor(256), list);
                assertThat("[93,92,91,987,10]", writer.getCursor(64), list);
                assertThat("[1000]", writer.getCursor(0), list);
                assertThat("[]", writer.getCursor(1000), list);
            }

            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                assertThat("[5567,1234]", reader.getCursor(256, Long.MAX_VALUE), list);
                assertThat("[93,92,91,987,10]", reader.getCursor(64, Long.MAX_VALUE), list);
                assertThat("[1000]", reader.getCursor(0, Long.MAX_VALUE), list);
            }

        });
    }

    @Test
    public void testAdd1MValues() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            Rnd rnd = new Rnd();
            int maxKeys = 1024;
            int N = 1000000;

            IntList keys = new IntList();
            IntObjHashMap<LongList> lists = new IntObjHashMap<>();

            // we need to have a conventional structure, which will unfortunately be memory-inefficient, to
            // assert that index is populated correctly
            create(configuration, path.trimTo(plen), "x", 128);
            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path, "x")) {
                for (int i = 0; i < N; i++) {
                    int key = i % maxKeys;
                    long value = rnd.nextLong();
                    writer.add(key, value);

                    LongList list = lists.get(key);
                    if (list == null) {
                        lists.put(key, list = new LongList());
                        keys.add(key);
                    }
                    list.add(value);
                }
            }

            // read values and compare to the structure index is expected to be holding
            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                for (int i = 0, n = keys.size(); i < n; i++) {
                    LongList list = lists.get(keys.getQuick(i));
                    Assert.assertNotNull(list);

                    BitmapIndexCursor cursor = reader.getCursor(keys.getQuick(i), Long.MAX_VALUE);
                    int z = list.size();
                    while (cursor.hasNext()) {
                        Assert.assertTrue(z > -1);
                        Assert.assertEquals(list.getQuick(z - 1), cursor.next());
                        z--;
                    }

                    // makes sure entire list is processed
                    Assert.assertEquals(0, z);
                }
            }
        });
    }

    @Test
    public void testBackwardReaderConstructorBadSequence() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            setupIndexHeader();
            assertBackwardReaderConstructorFail("failed to read index header");
        });
    }

    @Test
    public void testBackwardReaderConstructorBadSig() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (AppendMemory mem = openKey()) {
                mem.skip(BitmapIndexUtils.KEY_FILE_RESERVED);
            }
            assertBackwardReaderConstructorFail("Unknown format");
        });
    }

    @Test
    public void testBackwardReaderConstructorFileTooSmall() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            openKey().close();
            assertBackwardReaderConstructorFail("Index file too short");
        });
    }

    @Test
    public void testBackwardReaderKeyUpdateFail() {
        create(configuration, path.trimTo(plen), "x", 1024);

        try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path, "x")) {
            writer.add(0, 1000);
        }

        try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {

            // should have single value in cursor
            BitmapIndexCursor cursor = reader.getCursor(0, Long.MAX_VALUE);
            Assert.assertTrue(cursor.hasNext());
            Assert.assertEquals(1000, cursor.next());
            Assert.assertFalse(cursor.hasNext());

            try (Path path = new Path();
                 ReadWriteMemory mem = new ReadWriteMemory(
                         configuration.getFilesFacade(),
                         path.of(root).concat("x").put(".k").$(),
                         configuration.getFilesFacade().getPageSize())
            ) {
                // change sequence but not sequence check
                long seq = mem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE);
                mem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE);
                mem.putLong(22);

                try {
                    reader.getCursor(10, Long.MAX_VALUE);
                } catch (CairoException e) {
                    Assert.assertTrue(Chars.contains(e.getMessage(), "failed to consistently"));
                }

                // make sure index fails until sequence is not up to date

                try {
                    reader.getCursor(0, Long.MAX_VALUE);
                } catch (CairoException e) {
                    Assert.assertTrue(Chars.contains(e.getMessage(), "failed to consistently"));
                }

                mem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE);
                mem.putLong(seq);

                // test that index recovers
                cursor = reader.getCursor(0, Long.MAX_VALUE);
                Assert.assertTrue(cursor.hasNext());
                Assert.assertEquals(1000, cursor.next());
                Assert.assertFalse(cursor.hasNext());
            }
        }
    }

    @Test
    public void testConcurrentWriterAndReadBreadth() throws Exception {
        testConcurrentRW(10000000, 1024);
    }

    @Test
    public void testConcurrentWriterAndReadHeight() throws Exception {
        testConcurrentRW(1000000, 100000);
    }

    @Test
    public void testCursorTimeout() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            create(configuration, path.trimTo(plen), "x", 1024);

            try (BitmapIndexWriter w = new BitmapIndexWriter(configuration, path.trimTo(plen), "x")) {
                w.add(0, 10);
            }

            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {

                try (ReadWriteMemory mem = new ReadWriteMemory()) {
                    try (Path path = new Path()) {
                        path.of(configuration.getRoot()).concat("x").put(".k").$();
                        mem.of(configuration.getFilesFacade(), path, configuration.getFilesFacade().getPageSize());
                    }

                    long offset = BitmapIndexUtils.getKeyEntryOffset(0);
                    mem.jumpTo(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_VALUE_COUNT);
                    mem.putLong(10);

                    try {
                        reader.getCursor(0, Long.MAX_VALUE);
                        Assert.fail();
                    } catch (CairoException e) {
                        Assert.assertTrue(Chars.contains(e.getMessage(), "cursor failed"));
                    }
                }
            }
        });
    }

    @Test
    public void testEmptyCursor() {
        BitmapIndexCursor cursor = new BitmapIndexEmptyCursor();
        Assert.assertFalse(cursor.hasNext());
        Assert.assertEquals(0, cursor.next());
    }

    @Test
    public void testIndexDoesNotExist() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try {
                new BitmapIndexWriter(configuration, path, "x");
                Assert.fail();
            } catch (CairoException e) {
                Assert.assertTrue(Chars.contains(e.getMessage(), "does not exist"));
            }
        });
    }

    @Test
    public void testIntIndex() throws Exception {
        final int plen = path.length();
        final Rnd rnd = new Rnd();
        int N = 100000000;
        final int MOD = 1024;
        TestUtils.assertMemoryLeak(() -> {
            try (AppendMemory mem = new AppendMemory()) {

                mem.of(configuration.getFilesFacade(), path.concat("x.dat").$(), configuration.getFilesFacade().getMapPageSize());

                for (int i = 0; i < N; i++) {
                    mem.putInt(rnd.nextPositiveInt() & (MOD - 1));
                }

                try (SlidingWindowMemory rwin = new SlidingWindowMemory()) {
                    rwin.of(mem);

                    create(configuration, path.trimTo(plen), "x", N / MOD / 128);
                    try (BitmapIndexWriter writer = new BitmapIndexWriter()) {
                        writer.of(configuration, path.trimTo(plen), "x");
                        indexInts(rwin, writer, 0, N);
                    }
                }
            }
        });
    }

    @Test
    public void testLimitBackwardCursor() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            create(configuration, path.trimTo(plen), "x", 128);

            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path.trimTo(plen), "x")) {
                for (int i = 0; i < 265; i++) {
                    if (i % 3 == 0) {
                        continue;
                    }
                    writer.add(0, i);
                    writer.add(0, i);
                    writer.add(0, i);
                }
            }

            LongList tmp = new LongList();
            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                assertCursorLimit(reader, 260L, tmp);
                assertCursorLimit(reader, 16L, tmp);
                assertCursorLimit(reader, 9L, tmp);
                Assert.assertFalse(reader.getCursor(0, -1L).hasNext());
            }
        });
    }

    @Test
    public void testReaderDoesNotUnmapPages() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            Rnd rnd = new Rnd();

            create(configuration, path.trimTo(plen), "x", 1024);

            class CountingFacade extends FilesFacadeImpl {
                private int count = 0;

                @Override
                public long getMapPageSize() {
                    return 1024 * 1024;
                }

                @Override
                public void munmap(long address, long size) {
                    super.munmap(address, size);
                    count++;
                }
            }

            final CountingFacade facade = new CountingFacade();

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return facade;
                }
            };


            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path.trimTo(plen), "x")) {
                try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                    for (int i = 0; i < 100000; i++) {
                        int key = rnd.nextPositiveInt() % 1024;
                        long value = rnd.nextPositiveLong();
                        writer.add(key, value);

                        BitmapIndexCursor cursor = reader.getCursor(key, Long.MAX_VALUE);
                        boolean found = false;
                        while (cursor.hasNext()) {
                            if (value == cursor.next()) {
                                found = true;
                                break;
                            }
                        }
                        Assert.assertTrue(found);
                    }
                    Assert.assertEquals(0, facade.count);
                }
            }
        });
    }

    @Test
    public void testSimpleRollback() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            Rnd modelRnd = new Rnd();
            final int maxKeys = 1024;
            final int N = 1000000;
            final int CUTOFF = 60000;

            // this is an assertion in case somebody change the test
            //noinspection ConstantConditions
            assert CUTOFF < N;

            IntList keys = new IntList();
            IntObjHashMap<LongList> lists = new IntObjHashMap<>();

            // populate model for both reader and writer
            for (int i = 0; i < N; i++) {
                int key = modelRnd.nextPositiveInt() % maxKeys;
                LongList list = lists.get(key);
                if (list == null) {
                    lists.put(key, list = new LongList());
                    keys.add(key);
                }

                if (i > CUTOFF) {
                    continue;
                }

                list.add(i);
            }

            Rnd rnd = new Rnd();
            create(configuration, path.trimTo(plen), "x", 1024);
            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path.trimTo(plen), "x")) {
                for (int i = 0; i < N; i++) {
                    writer.add(rnd.nextPositiveInt() % maxKeys, i);
                }
                writer.rollbackValues(CUTOFF);
            }

            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                for (int i = 0, n = keys.size(); i < n; i++) {
                    int key = keys.getQuick(i);
                    // do not limit reader, we have to read everything index has
                    BitmapIndexCursor cursor = reader.getCursor(key, Long.MAX_VALUE);
                    LongList list = lists.get(key);

                    int v = list.size();
                    while (cursor.hasNext()) {
                        Assert.assertEquals(list.getQuick(--v), cursor.next());
                    }
                    Assert.assertEquals(0, v);
                }
            }

            // add more data to model
            for (int i = 0; i < N; i++) {
                int key = modelRnd.nextPositiveInt() % maxKeys;
                LongList list = lists.get(key);
                if (list == null) {
                    lists.put(key, list = new LongList());
                    keys.add(key);
                }
                list.add(i + N);
            }

            // add more date to index
            try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path.trimTo(plen), "x")) {
                for (int i = 0; i < N; i++) {
                    writer.add(rnd.nextPositiveInt() % maxKeys, i + N);
                }
            }

            // assert against model again
            try (BitmapIndexBackwardReader reader = new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0)) {
                for (int i = 0, n = keys.size(); i < n; i++) {
                    int key = keys.getQuick(i);
                    // do not limit reader, we have to read everything index has
                    BitmapIndexCursor cursor = reader.getCursor(key, Long.MAX_VALUE);
                    LongList list = lists.get(key);

                    int v = list.size();
                    while (cursor.hasNext()) {
                        Assert.assertEquals(list.getQuick(--v), cursor.next());
                    }
                    Assert.assertEquals(0, v);
                }
            }

        });
    }

    @Test
    public void testWriterConstructorBadSequence() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            setupIndexHeader();
            assertWriterConstructorFail("Sequence mismatch");
        });
    }

    @Test
    public void testWriterConstructorBadSig() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (AppendMemory mem = openKey()) {
                mem.skip(BitmapIndexUtils.KEY_FILE_RESERVED);
            }
            assertWriterConstructorFail("Unknown format");
        });
    }

    @Test
    public void testWriterConstructorFileTooSmall() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            openKey().close();
            assertWriterConstructorFail("Index file too short");
        });
    }

    @Test
    public void testWriterConstructorIncorrectValueCount() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (AppendMemory mem = openKey()) {
                mem.putByte(BitmapIndexUtils.SIGNATURE);
                mem.skip(9);
                mem.putLong(1000);
                mem.skip(4);
                mem.putLong(0);
                mem.skip(BitmapIndexUtils.KEY_FILE_RESERVED - mem.getAppendOffset());

            }
            assertWriterConstructorFail("Incorrect file size");
        });
    }

    @Test
    public void testWriterConstructorKeyMismatch() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (AppendMemory mem = openKey()) {
                mem.putByte(BitmapIndexUtils.SIGNATURE);
                mem.skip(20);
                mem.putLong(300);
                mem.skip(BitmapIndexUtils.KEY_FILE_RESERVED - mem.getAppendOffset());
            }
            assertWriterConstructorFail("Key count");
        });
    }

    private static void indexInts(SlidingWindowMemory srcMem, BitmapIndexWriter writer, long lo, long hi) {
        srcMem.updateSize();
        for (long r = lo; r < hi; r++) {
            final long offset = r * 4;
            writer.add(srcMem.getInt(offset), offset);
        }
    }

    private void assertBackwardReaderConstructorFail(CharSequence contains) {
        try {
            new BitmapIndexBackwardReader(configuration, path.trimTo(plen), "x", 0);
            Assert.fail();
        } catch (CairoException e) {
            Assert.assertTrue(Chars.contains(e.getMessage(), contains));
        }
    }

    private void assertCursorLimit(BitmapIndexBackwardReader reader, long max, LongList tmp) {
        tmp.clear();
        BitmapIndexCursor cursor = reader.getCursor(0, max);
        while (cursor.hasNext()) {
            tmp.add(cursor.next());
        }

        int len = tmp.size();
        for (int i = 0; i < max; i++) {
            if (i % 3 == 0) {
                continue;
            }

            Assert.assertEquals(i, tmp.getQuick(--len));
            Assert.assertEquals(i, tmp.getQuick(--len));
            Assert.assertEquals(i, tmp.getQuick(--len));
        }
    }

    private void assertThat(String expected, BitmapIndexCursor cursor, LongList temp) {
        temp.clear();
        while (cursor.hasNext()) {
            temp.add(cursor.next());
        }
        Assert.assertEquals(expected, temp.toString());
    }

    private void assertWriterConstructorFail(CharSequence contains) {
        try {
            new BitmapIndexWriter(configuration, path.trimTo(plen), "x");
            Assert.fail();
        } catch (CairoException e) {
            Assert.assertTrue(Chars.contains(e.getMessage(), contains));
        }
    }

    private AppendMemory openKey() {
        try (Path path = new Path()) {
            return new AppendMemory(configuration.getFilesFacade(), path.of(configuration.getRoot()).concat("x").put(".k").$(), configuration.getFilesFacade().getPageSize());
        }
    }

    private void setupIndexHeader() {
        try (AppendMemory mem = openKey()) {
            mem.putByte(BitmapIndexUtils.SIGNATURE);
            mem.putLong(10); // sequence
            mem.putLong(0); // value mem size
            mem.putInt(64); // block length
            mem.putLong(0); // key count
            mem.putLong(9); // sequence check
            mem.skip(BitmapIndexUtils.KEY_FILE_RESERVED - mem.getAppendOffset());
        }
    }

    private void testConcurrentRW(int N, int maxKeys) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            Rnd rnd = new Rnd();

            IntList keys = new IntList();
            IntObjHashMap<LongList> lists = new IntObjHashMap<>();

            // populate model for both reader and writer
            for (int i = 0; i < N; i++) {
                int key = rnd.nextPositiveInt() % maxKeys;

                LongList list = lists.get(key);
                if (list == null) {
                    lists.put(key, list = new LongList());
                    keys.add(key);
                }
                list.add(i);
            }

            final int threadCount = 3;
            CountDownLatch stopLatch = new CountDownLatch(threadCount);
            CyclicBarrier startBarrier = new CyclicBarrier(threadCount);
            AtomicInteger errors = new AtomicInteger();

            // create empty index
            create(configuration, path.trimTo(plen), "x", 1024);

            new Thread(() -> {
                try {
                    startBarrier.await();
                    try (Path path = new Path().of(configuration.getRoot())) {
                        try (BitmapIndexWriter writer = new BitmapIndexWriter(configuration, path, "x")) {
                            int pass = 0;
                            while (true) {
                                boolean added = false;
                                for (int i = 0, n = keys.size(); i < n; i++) {
                                    int key = keys.getQuick(i);
                                    LongList values = lists.get(key);
                                    if (pass < values.size()) {
                                        writer.add(key, values.getQuick(pass));
                                        added = true;
                                    }
                                }
                                pass++;
                                if (!added) {
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    stopLatch.countDown();
                }
            }).start();

            class MyReader implements Runnable {
                @Override
                public void run() {
                    try {
                        startBarrier.await();
                        try (Path path = new Path().of(configuration.getRoot())) {
                            try (BitmapIndexBackwardReader reader1 = new BitmapIndexBackwardReader(configuration, path, "x", 0)) {
                                LongList tmp = new LongList();
                                while (true) {
                                    boolean keepGoing = false;
                                    for (int i = keys.size() - 1; i > -1; i--) {
                                        int key = keys.getQuick(i);
                                        LongList values = lists.get(key);
                                        BitmapIndexCursor cursor = reader1.getCursor(key, Long.MAX_VALUE);

                                        tmp.clear();
                                        while (cursor.hasNext()) {
                                            tmp.add(cursor.next());
                                        }

                                        int sz = tmp.size();
                                        for (int k = 0; k < sz; k++) {
                                            Assert.assertEquals(values.getQuick(sz - k - 1), tmp.getQuick(k));
                                        }

                                        if (sz < values.size()) {
                                            keepGoing = true;
                                        }
                                    }

                                    if (!keepGoing) {
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        stopLatch.countDown();
                    }
                }
            }

            new Thread(new MyReader()).start();
            new Thread(new MyReader()).start();

            Assert.assertTrue(stopLatch.await(20000, TimeUnit.SECONDS));
            Assert.assertEquals(0, errors.get());
        });
    }
}