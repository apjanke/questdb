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

package com.questdb.ql.sys;

import com.questdb.BootstrapEnv;
import com.questdb.common.RecordMetadata;
import com.questdb.ql.RecordSource;
import com.questdb.store.factory.ReaderFactory;

public class $LocalesFactory implements SystemViewFactory {

    public static final $LocalesFactory INSTANCE = new $LocalesFactory();

    @Override
    public RecordSource create(ReaderFactory factory, BootstrapEnv env) {
        return new $LocalesRecordSource(env.dateLocaleFactory);
    }

    @Override
    public RecordMetadata getMetadata() {
        return new $LocalesRecordMetadata();
    }
}
