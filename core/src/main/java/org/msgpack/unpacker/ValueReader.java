//
// MessagePack for Java
//
// Copyright (C) 2009-2013 FURUHASHI Sadayuki
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
package org.msgpack.unpacker;

import java.lang.Iterable;
import java.io.IOException;
import java.io.Closeable;
import org.msgpack.type.Value;

public class ValueReader implements Closeable, Iterable<Value> {
    private Unpacker unpacker;

    public ValueReader(Unpacker unpacker) {
        this.unpacker = unpacker;
    }

    public Value read() throws IOException {
        // TODO
        return null;
    }

    public ValueReaderIterator iterator() {
        // TODO
        return null;
    }

    public void close() throws IOException {
        unpacker.close();
    }
}
