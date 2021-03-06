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

import com.questdb.common.Record;
import com.questdb.common.RecordCursor;
import com.questdb.common.StorageFacade;
import com.questdb.std.Rows;

public class TableReaderRecordCursor implements RecordCursor {

    private final TableReader reader;
    private final TableReaderRecord record;
    private int partitionIndex = 0;
    private int partitionCount;
    private long maxRecordIndex = -1;


    public TableReaderRecordCursor(TableReader reader) {
        this.reader = reader;
        this.record = new TableReaderRecord(reader);
    }

    @Override
    public Record getRecord() {
        return record;
    }

    @Override
    public Record newRecord() {
        return new TableReaderRecord(reader);
    }

    @Override
    public StorageFacade getStorageFacade() {
        return null;
    }

    @Override
    public Record recordAt(long rowId) {
        recordAt(record, rowId);
        return record;
    }

    @Override
    public void recordAt(Record record, long rowId) {
        ((TableReaderRecord) record).jumpTo(Rows.toPartitionIndex(rowId), Rows.toLocalRowID(rowId));
    }

    @Override
    public void releaseCursor() {
        // nothing to do
    }

    @Override
    public void toTop() {
        partitionIndex = 0;
        partitionCount = reader.getPartitionCount();
        record.jumpTo(0, -1);
        maxRecordIndex = -1;
    }

    @Override
    public boolean hasNext() {
        return record.getRecordIndex() < maxRecordIndex || switchPartition();
    }

    @Override
    public Record next() {
        record.incrementRecordIndex();
        return record;
    }

    private boolean switchPartition() {
        while (partitionIndex < partitionCount) {
            final long partitionSize = reader.openPartition(partitionIndex++);
            if (partitionSize > 0) {
                maxRecordIndex = partitionSize - 1;
                record.jumpTo(partitionIndex - 1, -1);
                return true;
            }
        }
        return false;
    }
}
