/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2016 Appsicle
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

package com.questdb.ql.impl.analytic;

import com.questdb.ex.JournalException;
import com.questdb.factory.JournalReaderFactory;
import com.questdb.factory.configuration.RecordMetadata;
import com.questdb.misc.Misc;
import com.questdb.ql.*;
import com.questdb.ql.impl.CollectionRecordMetadata;
import com.questdb.ql.impl.RecordList;
import com.questdb.ql.impl.SplitRecordMetadata;
import com.questdb.ql.impl.join.hash.FakeRecord;
import com.questdb.ql.impl.map.MapUtils;
import com.questdb.ql.impl.sort.RecordComparator;
import com.questdb.ql.ops.AbstractCombinedRecordSource;
import com.questdb.std.CharSink;
import com.questdb.std.ObjList;
import com.questdb.std.RedBlackTree;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CachedRowAnalyticRecordSource extends AbstractCombinedRecordSource {

    private final RecordList recordList;
    private final RecordSource recordSource;
    private final ObjList<RedBlackTree> orderedSources;
    private final int orderGroupCount;
    private final ObjList<ObjList<AnalyticFunction>> functionGroups;
    private final ObjList<AnalyticFunction> functions;
    private final RecordMetadata metadata;
    private final AnalyticRecord record;
    private final AnalyticRecordStorageFacade storageFacade;
    private final int split;
    private final FakeRecord fakeRecord = new FakeRecord();
    private final int pageSize;
    private final ObjList<RecordComparator> comparators;
    private RecordCursor parentCursor;

    public CachedRowAnalyticRecordSource(
            int pageSize,
            RecordSource recordSource,
            ObjList<RecordComparator> comparators,
            ObjList<ObjList<AnalyticFunction>> functionGroups) {
        this.pageSize = pageSize;
        this.recordSource = recordSource;
        this.comparators = comparators;
        this.orderGroupCount = comparators.size();
        assert orderGroupCount == functionGroups.size();
        this.orderedSources = new ObjList<>(orderGroupCount);
        this.functionGroups = functionGroups;
        this.recordList = new RecordList(MapUtils.ROWID_RECORD_METADATA, pageSize);
        // create our metadata and also flatten functions for our record representation
        CollectionRecordMetadata funcMetadata = new CollectionRecordMetadata();
        this.functions = new ObjList<>(orderGroupCount);
        for (int i = 0; i < orderGroupCount; i++) {
            ObjList<AnalyticFunction> l = functionGroups.getQuick(i);
            for (int j = 0; j < l.size(); j++) {
                AnalyticFunction f = l.getQuick(j);
                funcMetadata.add(f.getMetadata());
                functions.add(f);
            }
        }
        this.metadata = new SplitRecordMetadata(recordSource.getMetadata(), funcMetadata);
        this.split = recordSource.getMetadata().getColumnCount();
        this.record = new AnalyticRecord(split, functions);
        this.storageFacade = new AnalyticRecordStorageFacade(split, functions);
        this.recordList.setStorageFacade(storageFacade);
    }

    @Override
    public void close() {
        if (recordList != null) {
            recordList.close();
        }

        for (int i = 0; i < orderGroupCount; i++) {
            RedBlackTree tree = orderedSources.getQuick(i);
            if (tree != null) {
                tree.close();
            }
        }

        for (int i = 0, n = functions.size(); i < n; i++) {
            Misc.free(functions.getQuick(i));
        }
    }

    @Override
    public RecordMetadata getMetadata() {
        return metadata;
    }

    @Override
    public RecordCursor prepareCursor(JournalReaderFactory factory, CancellationHandler cancellationHandler) throws JournalException {
        RecordCursor cursor = recordSource.prepareCursor(factory, cancellationHandler);
        this.parentCursor = cursor;
        this.storageFacade.prepare(factory, cursor.getStorageFacade());

        // red&black trees, one for each comparator where comparator is not null
        for (int i = 0; i < orderGroupCount; i++) {
            RecordComparator cmp = comparators.getQuick(i);
            if (cmp != null) {
                orderedSources.add(new RedBlackTree(new MyComparator(cmp, cursor), pageSize));
            } else {
                orderedSources.add(null);
            }
        }

        // step #1: store source cursor in record list
        // - add record list' row ids to all trees, which will put these row ids in necessary order
        // for this we will be using out comparator, which helps tree compare long values
        // based on record these values are addressing
        long rowid = -1;
        while (cursor.hasNext()) {
            cancellationHandler.check();
            Record record = cursor.next();
            long row = record.getRowId();
            rowid = recordList.append(fakeRecord.of(row), rowid);
            if (orderGroupCount > 0) {
                for (int i = 0; i < orderGroupCount; i++) {
                    RedBlackTree tree = orderedSources.getQuick(i);
                    if (tree != null) {
                        tree.add(row);
                    }
                }
            }
        }

        for (int i = 0; i < orderGroupCount; i++) {
            RedBlackTree tree = orderedSources.getQuick(i);
            ObjList<AnalyticFunction> functions = functionGroups.getQuick(i);
            if (tree != null) {
                // step #2: populate all analytic functions with records in order of respective tree
                RedBlackTree.LongIterator iterator = tree.iterator();
                while (iterator.hasNext()) {

                    cancellationHandler.check();

                    Record record = cursor.recordAt(iterator.next());
                    for (int j = 0, n = functions.size(); j < n; j++) {
                        functions.getQuick(j).add(record);
                    }
                }
            } else {
                // step #2: alternatively run record list through two-pass functions
                for (int j = 0, n = functions.size(); j < n; j++) {
                    AnalyticFunction f = functions.getQuick(j);
                    if (f.getType() != AnalyticFunctionType.STREAM) {
                        recordList.toTop();
                        while (recordList.hasNext()) {
                            f.add(cursor.recordAt(recordList.next().getLong(0)));
                        }
                    }
                }
            }
        }

        recordList.toTop();
        for (int i = 0, n = functions.size(); i < n; i++) {
            functions.getQuick(i).prepare(cursor);
        }
        return this;
    }

    @Override
    public void reset() {
        if (recordList != null) {
            recordList.clear();
        }

        for (int i = 0; i < orderGroupCount; i++) {
            RedBlackTree tree = orderedSources.getQuick(i);
            if (tree != null) {
                tree.clear();
            }
        }

        for (int i = 0, n = functions.size(); i < n; i++) {
            functions.getQuick(i).reset();
        }
    }

    @Override
    public StorageFacade getStorageFacade() {
        return storageFacade;
    }

    @Override
    public boolean hasNext() {
        if (recordList.hasNext()) {
            long row = recordList.next().getLong(0);
            record.of(parentCursor.recordAt(row));
            for (int i = 0, n = functions.size(); i < n; i++) {
                functions.getQuick(i).prepareFor(record);
            }
            return true;
        }
        return false;
    }

    @SuppressFBWarnings("IT_NO_SUCH_ELEMENT")
    @Override
    public Record next() {
        return record;
    }

    @Override
    public Record newRecord() {
        return new AnalyticRecord(split, functions);
    }

    @Override
    public void toSink(CharSink sink) {
        sink.put('{');
        sink.putQuoted("op").put(':').putQuoted("CachedRowAnalyticRecordSource").put(',');
        sink.putQuoted("functions").put(':').put(functions.size()).put(',');
        sink.putQuoted("src").put(':').put(recordSource);
        sink.put('}');
    }

    private static class MyComparator implements RedBlackTree.LongComparator {
        private final RecordComparator delegate;
        private final RecordCursor cursor;
        private final Record left;
        private final Record right;

        public MyComparator(RecordComparator delegate, RecordCursor cursor) {
            this.delegate = delegate;
            this.cursor = cursor;
            this.left = cursor.newRecord();
            this.right = cursor.newRecord();
        }

        @Override
        public int compare(long right) {
            cursor.recordAt(this.right, right);
            return delegate.compare(this.right);
        }

        @Override
        public void setLeft(long left) {
            cursor.recordAt(this.left, left);
            delegate.setLeft(this.left);
        }
    }
}