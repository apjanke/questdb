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

public class BitmapIndexNullReader implements BitmapIndexReader {

    private final NullCursor cursor = new NullCursor();

    @Override
    public BitmapIndexCursor getCursor(int key, long maxValue) {
        cursor.value = maxValue;
        return cursor;
    }

    @Override
    public int getKeyCount() {
        return 1;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    private class NullCursor implements BitmapIndexCursor {
        private long value;

        @Override
        public boolean hasNext() {
            return value > -1;
        }

        @Override
        public long next() {
            return value--;
        }
    }
}
