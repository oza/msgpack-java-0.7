//
// MessagePack for Java
//
// Copyright (C) 2009-2011 FURUHASHI Sadayuki
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

import java.io.IOException;
import java.io.EOFException;
import java.math.BigInteger;
import org.msgpack.MessageTypeException;
import org.msgpack.packer.Unconverter;
import org.msgpack.io.Input;

abstract class AbstractMessagePackUnpacker extends Unpacker {
    protected final Input in;

    private static final byte REQUIRE_TO_READ_HEAD = (byte)0xc6;

    private byte headByte = REQUIRE_TO_READ_HEAD;

    private byte[] raw;
    private int rawFilled;

    private final UnpackerStack stack = new UnpackerStack();

    private final IntAccept intAccept = new IntAccept();
    private final LongAccept longAccept = new LongAccept();
    private final BigIntegerAccept bigIntegerAccept = new BigIntegerAccept();
    private final DoubleAccept doubleAccept = new DoubleAccept();
    private final ByteArrayAccept byteArrayAccept = new ByteArrayAccept();
    private final ArrayAccept arrayAccept = new ArrayAccept();
    private final MapAccept mapAccept = new MapAccept();
    private final ValueAccept valueAccept = new ValueAccept();
    private final SkipAccept skipAccept = new SkipAccept();

    protected AbstractMessagePackUnpacker(Input in) {
        this.in = in;
    }

    private byte getHeadByte() throws IOException {
        byte b = headByte;
        if(b == REQUIRE_TO_READ_HEAD) {
            b = headByte = in.readByte();
        }
        return b;
    }

    final void readOne(Accept a) throws IOException {
        stack.checkCount();
        if(readOneWithoutStack(a)) {
            stack.reduceCount();
        }
    }

    final boolean readOneWithoutStack(Accept a) throws IOException {
        byte b = getHeadByte();

        if(raw == null) {
            readRawBodyCont();
            a.acceptRaw(raw);
            raw = null;
            return true;
        }

        if((b & 0x80) == 0) {  // Positive Fixnum
            //System.out.println("positive fixnum "+b);
            a.acceptInteger(b);
            return true;
        }

        if((b & 0xe0) == 0xe0) {  // Negative Fixnum
            //System.out.println("negative fixnum "+b);
            a.acceptInteger(b);
            return true;
        }

        if((b & 0xe0) == 0xa0) {  // FixRaw
            int count = b & 0x1f;
            if(count == 0) {
                a.acceptEmptyRaw();
                return true;
            }
            readRawBody(count);
            a.acceptRaw(raw);
            raw = null;
            return true;
        }

        if((b & 0xf0) == 0x90) {  // FixArray
            int count = b & 0x0f;
            //System.out.println("fixarray count:"+count);
            if(count == 0) {
                a.acceptEmptyArray();
                return true;
            }
            a.acceptArray(count);
            stack.pushArray(count);
            return false;
        }

        if((b & 0xf0) == 0x80) {  // FixMap
            int count = b & 0x0f;
            //System.out.println("fixmap count:"+count/2);
            if(count == 0) {
                a.acceptEmptyMap();
                return true;
            }
            a.acceptMap(count);
            stack.pushMap(count);
            return false;
        }

        switch(b & 0xff) {
        case 0xc0:  // nil
            a.acceptNil();
            return true;
        case 0xc2:  // false
            a.acceptBoolean(false);
            return true;
        case 0xc3:  // true
            a.acceptBoolean(true);
            return true;
        case 0xca:  // float
            a.acceptFloat(in.getFloat());
            in.advance();
            return true;
        case 0xcb:  // double
            a.acceptDouble(in.getDouble());
            in.advance();
            return true;
        case 0xcc:  // unsigned int  8
            a.acceptUnsignedInteger(in.getByte());
            in.advance();
            return true;
        case 0xcd:  // unsigned int 16
            a.acceptUnsignedInteger(in.getShort());
            in.advance();
            return true;
        case 0xce:  // unsigned int 32
            a.acceptUnsignedInteger(in.getInt());
            in.advance();
            return true;
        case 0xcf:  // unsigned int 64
            a.acceptInteger(in.getLong());
            in.advance();
            return true;
        case 0xd0:  // signed int  8
            a.acceptInteger(in.getByte());
            in.advance();
            return true;
        case 0xd1:  // signed int 16
            a.acceptInteger(in.getShort());
            in.advance();
            return true;
        case 0xd2:  // signed int 32
            a.acceptInteger(in.getInt());
            in.advance();
            return true;
        case 0xd3:  // signed int 64
            a.acceptInteger(in.getLong());
            in.advance();
            return true;
        case 0xda:  // raw 16
            {
                int count = in.getShort() & 0xffff;
                if(count == 0) {
                    a.acceptEmptyRaw();
                    in.advance();
                    return true;
                }
                in.advance();
                readRawBody(count);
                a.acceptRaw(raw);
                raw = null;
                return true;
            }
        case 0xdb:  // raw 32
            {
                int count = in.getInt();
                if(count < 0) {
                    throw new IOException("Raw size too large");
                }
                if(count == 0) {
                    a.acceptEmptyRaw();
                    in.advance();
                    return true;
                }
                in.advance();
                readRawBody(count);
                a.acceptRaw(raw);
                raw = null;
                return true;
            }
        case 0xdc:  // array 16
            {
                int count = in.getShort() & 0xff;
                if(count == 0) {
                    a.acceptEmptyArray();
                    in.advance();
                    return true;
                }
                a.acceptArray(count);
                stack.pushArray(count);
                in.advance();
                return false;
            }
        case 0xdd:  // array 32
            {
                int count = in.getInt();
                if(count < 0) {
                    throw new IOException("Array size too large");
                }
                if(count == 0) {
                    a.acceptEmptyArray();
                    in.advance();
                    return true;
                }
                a.acceptArray(count);
                stack.pushArray(count);
                in.advance();
                return false;
            }
        case 0xde:  // map 16
            {
                int count = in.getShort() & 0xff;
                if(count == 0) {
                    a.acceptEmptyMap();
                    in.advance();
                    return true;
                }
                a.acceptMap(count);
                stack.pushMap(count);
                in.advance();
                return false;
            }
        case 0xdf:  // map 32
            {
                int count = in.getInt();
                if(count < 0) {
                    throw new IOException("Map size too large");
                }
                if(count == 0) {
                    a.acceptEmptyMap();
                    in.advance();
                    return true;
                }
                a.acceptMap(count);
                stack.pushMap(count);
                in.advance();
                return false;
            }
        default:
            //System.out.println("unknown b "+(b&0xff));
            // headByte = CS_INVALID
            headByte = REQUIRE_TO_READ_HEAD;
            throw new MessageTypeException("Invalid byte: "+b);  // TODO error
        }
    }

    private void readRawBody(int size) throws IOException {
        raw = new byte[size];
        rawFilled = 0;
        readRawBodyCont();
    }

    private void readRawBodyCont() throws IOException {
        int len = in.read(raw, rawFilled, raw.length - rawFilled);
        rawFilled -= len;
        if(rawFilled > 0) {
            throw new EOFException();
        }
    }

    @Override
    public boolean tryReadNil() throws IOException {
        stack.checkCount();
        int b = getHeadByte() & 0xff;
        if(b != 0xc0) {
            return false;
        }
        stack.reduceCount();
        return true;
    }

    @Override
    public void readNil() throws IOException {
        if(!tryReadNil()) {
            throw new MessageTypeException("Expected Nil but got not nil value");
        }
    }

    @Override
    public boolean readBoolean() throws IOException {
        // optimized: readOne(booleanAccept());
        stack.checkCount();
        int b = getHeadByte() & 0xff;
        if(b == 0xc2) {
            stack.reduceCount();
            return false;
        } else if(b == 0xc3) {
            stack.reduceCount();
            return true;
        }
        throw new MessageTypeException("Expected Boolean but got not boolean value");
    }

    @Override
    public byte readByte() throws IOException {
        stack.checkCount();
        readOneWithoutStack(intAccept);
        int value = intAccept.value;
        if(value < (int)Byte.MIN_VALUE || value > (int)Byte.MAX_VALUE) {
            throw new MessageTypeException();  // TODO message
        }
        stack.reduceCount();
        return (byte)value;
    }

    @Override
    public short readShort() throws IOException {
        stack.checkCount();
        readOneWithoutStack(intAccept);
        int value = intAccept.value;
        if(value < (int)Short.MIN_VALUE || value > (int)Short.MAX_VALUE) {
            throw new MessageTypeException();  // TODO message
        }
        stack.reduceCount();
        return (short)value;
    }

    @Override
    public int readInt() throws IOException {
        readOne(intAccept);
        return intAccept.value;
    }

    @Override
    public long readLong() throws IOException {
        readOne(longAccept);
        return longAccept.value;
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        readOne(bigIntegerAccept);
        return bigIntegerAccept.value;
    }

    @Override
    public float readFloat() throws IOException {
        readOne(doubleAccept);
        return (float)doubleAccept.value;
    }

    @Override
    public double readDouble() throws IOException {
        readOne(doubleAccept);
        return doubleAccept.value;
    }

    public byte[] readByteArray() throws IOException {
        readOne(byteArrayAccept);
        return byteArrayAccept.value;
    }

    @Override
    public int readArrayBegin() throws IOException {
        readOne(arrayAccept);
        return arrayAccept.size;
    }

    @Override
    public void readArrayEnd(boolean check) throws IOException {
        if(stack.topIsArray()) {
            throw new MessageTypeException("readArrayEnd() is called but readArrayBegin() is not called");
        }

        int remain = stack.getTopCount();
        if(remain > 0) {
            if(check) {
                throw new MessageTypeException("readArrayEnd(check=true) is called but the array is not end");
            }
            for(int i=0; i < remain; i++) {
                skip();
            }
        }
        stack.pop();
    }

    @Override
    public int readMapBegin() throws IOException {
        readOne(mapAccept);
        return mapAccept.size;
    }

    @Override
    public void readMapEnd(boolean check) throws IOException {
        if(!stack.topIsMap()) {
            throw new MessageTypeException("readMapEnd() is called but readMapBegin() is not called");
        }

        int remain = stack.getTopCount();
        if(remain > 0) {
            if(check) {
                throw new MessageTypeException("readMapEnd(check=true) is called but the map is not end");
            }
            for(int i=0; i < remain; i++) {
                skip();
            }
        }
        stack.pop();
    }

    @Override
    void iterateNext(Unconverter uc) throws IOException {
        if(uc.getResult() != null) {
            uc.resetResult();
        }
        valueAccept.setUnconverter(uc);

        stack.checkCount();

        boolean primitive = readOneWithoutStack(skipAccept);
        if(primitive) {
            stack.reduceCount();
            if(uc.getResult() != null) {
                return;
            }
            if(stack.getTopCount() == 0) {
                if(stack.topIsArray()) {
                    uc.writeArrayEnd();
                    stack.pop();
                } else if(stack.topIsMap()) {
                    uc.writeMapEnd();
                    stack.pop();
                } else {
                    // FIXME error?
                }
                if(uc.getResult() != null) {
                    return;
                }
            }
        }
        while(true) {
            if(readOneWithoutStack(valueAccept)) {
                stack.reduceCount();
                if(stack.getTopCount() == 0) {
                    if(stack.topIsArray()) {
                        uc.writeArrayEnd();
                        stack.pop();
                    } else if(stack.topIsMap()) {
                        uc.writeMapEnd();
                        stack.pop();
                    } else {
                        // FIXME error?
                    }
                    if(uc.getResult() != null) {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void skip() throws IOException {
        stack.checkCount();
        if(readOneWithoutStack(skipAccept)) {
            stack.reduceCount();
            return;
        }
        int targetDepth = stack.getDepth()-1;
        while(true) {
            if(readOneWithoutStack(skipAccept)) {
                stack.reduceCount();
                if(stack.getTopCount() == 0) {
                    stack.pop();
                    if(stack.getDepth() <= targetDepth) {
                        return;
                    }
                }
            }
        }
    }
}
