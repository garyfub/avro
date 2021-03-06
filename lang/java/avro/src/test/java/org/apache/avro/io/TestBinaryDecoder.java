/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.avro.RandomData;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.util.ByteBufferInputStream;
import org.apache.avro.util.ByteBufferOutputStream;
import org.apache.avro.util.Utf8;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestBinaryDecoder {
  // prime number buffer size so that looping tests hit the buffer edge
  // at different points in the loop.
  DecoderFactory factory;
  public TestBinaryDecoder(boolean useDirect) {
    factory = new DecoderFactory().configureDecoderBufferSize(521);
    factory.configureDirectDecoder(useDirect);
  }
  
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { true },
        { false },
    });
  }
  
  private Decoder newDecoderWithNoData() throws IOException {
    return newDecoder(new byte[0]);
  }

  private Decoder newDecoder(byte[] bytes, int start, int len)
    throws IOException {
    return factory.createBinaryDecoder(bytes, start, len, null);
    
  }

  private Decoder newDecoder(InputStream in) {
    return factory.createBinaryDecoder(in, null);
  }

  private Decoder newDecoder(byte[] bytes) throws IOException {
    return factory.createBinaryDecoder(bytes, null);
  }

  /** Verify EOFException throw at EOF */

  @Test(expected=EOFException.class)
  public void testEOFBoolean() throws IOException {
    newDecoderWithNoData().readBoolean();
  }
  
  @Test(expected=EOFException.class)
  public void testEOFInt() throws IOException {
    newDecoderWithNoData().readInt();
  }
  
  @Test(expected=EOFException.class)
  public void testEOFLong() throws IOException {
    newDecoderWithNoData().readLong();
  }
  
  @Test(expected=EOFException.class)
  public void testEOFFloat() throws IOException {
    newDecoderWithNoData().readFloat();
  }
  
  @Test(expected=EOFException.class)
  public void testEOFDouble() throws IOException {
    newDecoderWithNoData().readDouble();
  }
  
  @Test(expected=EOFException.class)
  public void testEOFBytes() throws IOException {
    newDecoderWithNoData().readBytes(null);
  }
  
  @Test(expected=EOFException.class)
  public void testEOFString() throws IOException {
    newDecoderWithNoData().readString(new Utf8("a"));
  }
  
  @Test(expected=EOFException.class)
  public void testEOFFixed() throws IOException {
    newDecoderWithNoData().readFixed(new byte[1]);
  }

  @Test(expected=EOFException.class)
  public void testEOFEnum() throws IOException {
    newDecoderWithNoData().readEnum();
  }
  
  @Test
  public void testReuse() throws IOException {
    ByteBufferOutputStream bbo1 = new ByteBufferOutputStream();
    ByteBufferOutputStream bbo2 = new ByteBufferOutputStream();
    byte[] b1 = new byte[] { 1, 2 };
    
    BinaryEncoder e1 = new BinaryEncoder(bbo1);
    e1.writeBytes(b1);
    e1.flush();
    
    BinaryEncoder e2 = new BinaryEncoder(bbo2);
    e2.writeBytes(b1);
    e2.flush();
    
    DirectBinaryDecoder d = new DirectBinaryDecoder(
        new ByteBufferInputStream(bbo1.getBufferList()));
    ByteBuffer bb1 = d.readBytes(null);
    Assert.assertEquals(b1.length, bb1.limit() - bb1.position());
    
    d.init(new ByteBufferInputStream(bbo2.getBufferList()));
    ByteBuffer bb2 = d.readBytes(null);
    Assert.assertEquals(b1.length, bb2.limit() - bb2.position());
    
  }
  
  private static byte[] data = null;
  private static int seed = -1;
  private static Schema schema = null;
  private static int count = 200;
  private static ArrayList<Object> records = new ArrayList<Object>(count);
  @BeforeClass
  public static void generateData() throws IOException {
    seed = (int)System.currentTimeMillis();
    // note some tests (testSkipping) rely on this explicitly
    String jsonSchema =
      "{\"type\": \"record\", \"name\": \"Test\", \"fields\": ["
      +"{\"name\":\"intField\", \"type\":\"int\"},"
      +"{\"name\":\"bytesField\", \"type\":\"bytes\"},"
      +"{\"name\":\"booleanField\", \"type\":\"boolean\"},"
      +"{\"name\":\"stringField\", \"type\":\"string\"},"
      +"{\"name\":\"floatField\", \"type\":\"float\"},"
      +"{\"name\":\"doubleField\", \"type\":\"double\"},"
      +"{\"name\":\"arrayField\", \"type\": " +
          "{\"type\":\"array\", \"items\":\"boolean\"}},"
      +"{\"name\":\"longField\", \"type\":\"long\"}]}";
    schema = Schema.parse(jsonSchema);
    GenericDatumWriter<Object> writer = new GenericDatumWriter<Object>();
    writer.setSchema(schema);
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
    BinaryEncoder encoder = new BinaryEncoder(baos);
    
    for (Object datum : new RandomData(schema, count, seed)) {
      writer.write(datum, encoder);
      records.add(datum);
    }
    data = baos.toByteArray();
  }

  @Test
  public void testDecodeFromSources() throws IOException {
    GenericDatumReader<Object> reader = new GenericDatumReader<Object>();
    reader.setSchema(schema);
    
    ByteArrayInputStream is = new ByteArrayInputStream(data);
    ByteArrayInputStream is2 = new ByteArrayInputStream(data);
    ByteArrayInputStream is3 = new ByteArrayInputStream(data);

    Decoder fromInputStream = newDecoder(is);
    Decoder fromArray = newDecoder(data);
    
    byte[] data2 = new byte[data.length + 30];
    Arrays.fill(data2, (byte)0xff);
    System.arraycopy(data, 0, data2, 15, data.length);

    Decoder fromOffsetArray = newDecoder(data2, 15, data.length);

    BinaryDecoder initOnInputStream = factory.createBinaryDecoder(
        new byte[50], 0, 30, null);
    initOnInputStream = factory.createBinaryDecoder(is2, initOnInputStream);
    BinaryDecoder initOnArray = factory.createBinaryDecoder(is3, null);
    initOnArray = factory.createBinaryDecoder(
        data, 0, data.length, initOnArray);
    
    for (Object datum : records) {
      Assert.assertEquals(
          "InputStream based BinaryDecoder result does not match",
          datum, reader.read(null, fromInputStream));
      Assert.assertEquals(
          "Array based BinaryDecoder result does not match",
          datum, reader.read(null, fromArray));
      Assert.assertEquals(
          "offset Array based BinaryDecoder result does not match",
          datum, reader.read(null, fromOffsetArray));
      Assert.assertEquals(
          "InputStream initialized BinaryDecoder result does not match",
          datum, reader.read(null, initOnInputStream));
      Assert.assertEquals(
          "Array initialized BinaryDecoder result does not match",
          datum, reader.read(null, initOnArray));
    }
  }

  @Test
  public void testInputStreamProxy() throws IOException {
    Decoder d = newDecoder(data);
    if (d instanceof BinaryDecoder) {
      BinaryDecoder bd = (BinaryDecoder) d;
      InputStream test = bd.inputStream();
      InputStream check = new ByteArrayInputStream(data);
      validateInputStreamReads(test, check);
      bd = factory.createBinaryDecoder(data, bd);
      test = bd.inputStream();
      check = new ByteArrayInputStream(data);
      validateInputStreamSkips(test, check);
      // with input stream sources
      bd = factory.createBinaryDecoder(new ByteArrayInputStream(data), bd);
      test = bd.inputStream();
      check = new ByteArrayInputStream(data);
      validateInputStreamReads(test, check);
      bd = factory.createBinaryDecoder(new ByteArrayInputStream(data), bd);
      test = bd.inputStream();
      check = new ByteArrayInputStream(data);
      validateInputStreamSkips(test, check);
    }
  }

  @Test
  public void testInputStreamProxyDetached() throws IOException {
    Decoder d = newDecoder(data);
    if (d instanceof BinaryDecoder) {
      BinaryDecoder bd = (BinaryDecoder) d;
      InputStream test = bd.inputStream();
      InputStream check = new ByteArrayInputStream(data);
      // detach input stream and decoder from old source
      factory.createBinaryDecoder(new byte[56], null);
      InputStream bad = bd.inputStream();
      InputStream check2 = new ByteArrayInputStream(data);
      validateInputStreamReads(test, check);
      Assert.assertFalse(bad.read() == check2.read());
    }
  }
  
  @Test
  public void testInputStreamPartiallyUsed() throws IOException {
    BinaryDecoder bd = factory.createBinaryDecoder(
        new ByteArrayInputStream(data), null);
    InputStream test = bd.inputStream();
    InputStream check = new ByteArrayInputStream(data);
    // triggers buffer fill if unused and tests isEnd()
    try {
      Assert.assertFalse(bd.isEnd()); 
    } catch (UnsupportedOperationException e) {
      // this is ok if its a DirectBinaryDecoder.
      if (bd.getClass() != DirectBinaryDecoder.class) {
        throw e;
      }
    }
    bd.readFloat(); // use data, and otherwise trigger buffer fill
    check.skip(4); // skip the same # of bytes here
    validateInputStreamReads(test, check);
  }

  private void validateInputStreamReads(InputStream test, InputStream check)
      throws IOException {
    byte[] bt = new byte[7];
    byte[] bc = new byte[7]; 
    while (true) {
      int t = test.read();
      int c = check.read();
      Assert.assertEquals(c, t);
      if (-1 == t) break;
      t = test.read(bt);
      c = check.read(bc);
      Assert.assertEquals(c, t);
      Assert.assertArrayEquals(bt, bc);
      if (-1 == t) break;
      t = test.read(bt, 1, 4);
      c = check.read(bc, 1, 4);
      Assert.assertEquals(c, t);
      Assert.assertArrayEquals(bt, bc);
      if (-1 == t) break;
    }
    Assert.assertEquals(0, test.skip(5));
    Assert.assertEquals(0, test.available());
    Assert.assertFalse(test.getClass() != ByteArrayInputStream.class && test.markSupported());
    test.close();
  }
  
  private void validateInputStreamSkips(InputStream test, InputStream check) throws IOException {
    while(true) {
      long t2 = test.skip(19);
      long c2 = check.skip(19);
      Assert.assertEquals(c2, t2);
      if (0 == t2) break;
    }
    Assert.assertEquals(-1, test.read());
  }

  @Test
  public void testBadIntEncoding() throws IOException {
    byte[] badint = new byte[5];
    Arrays.fill(badint, (byte)0xff);
    Decoder bd = factory.createBinaryDecoder(badint, null);
    String message = "";
    try {
      bd.readInt();
    } catch (IOException ioe) {
      message = ioe.getMessage();
    }
    Assert.assertEquals("Invalid int encoding", message);
  }

  @Test
  public void testBadLongEncoding() throws IOException {
    byte[] badint = new byte[10];
    Arrays.fill(badint, (byte)0xff);
    Decoder bd = factory.createBinaryDecoder(badint, null);
    String message = "";
    try {
      bd.readLong();
    } catch (IOException ioe) {
      message = ioe.getMessage();
    }
    Assert.assertEquals("Invalid long encoding", message);
  }

  @Test(expected=EOFException.class)
  public void testIntTooShort() throws IOException {
    byte[] badint = new byte[4];
    Arrays.fill(badint, (byte)0xff);
    newDecoder(badint).readInt();
  }

  @Test(expected=EOFException.class)
  public void testLongTooShort() throws IOException {
    byte[] badint = new byte[9];
    Arrays.fill(badint, (byte)0xff);
    newDecoder(badint).readLong();
  }
  
  @Test(expected=EOFException.class)
  public void testFloatTooShort() throws IOException {
    byte[] badint = new byte[3];
    Arrays.fill(badint, (byte)0xff);
    newDecoder(badint).readInt();
  }

  @Test(expected=EOFException.class)
  public void testDoubleTooShort() throws IOException {
    byte[] badint = new byte[7];
    Arrays.fill(badint, (byte)0xff);
    newDecoder(badint).readLong();
  }

  @Test
  public void testSkipping() throws IOException {
    Decoder d = newDecoder(data);
    skipGenerated(d);
    if (d instanceof BinaryDecoder) {
      BinaryDecoder bd = (BinaryDecoder) d;
      try {
        Assert.assertTrue(bd.isEnd());
      } catch (UnsupportedOperationException e) {
        // this is ok if its a DirectBinaryDecoder.
        if (bd.getClass() != DirectBinaryDecoder.class) {
          throw e;
        }
      }
      bd = factory.createBinaryDecoder(new ByteArrayInputStream(data), bd);
      skipGenerated(bd);
      try {
        Assert.assertTrue(bd.isEnd());
      } catch (UnsupportedOperationException e) {
        // this is ok if its a DirectBinaryDecoder.
        if (bd.getClass() != DirectBinaryDecoder.class) {
          throw e;
        }
      }
    }
  }

  private void skipGenerated(Decoder bd) throws IOException {
    for (int i = 0; i < records.size(); i++) {
      bd.readInt();
      bd.skipBytes();
      bd.skipFixed(1);
      bd.skipString();
      bd.skipFixed(4);
      bd.skipFixed(8);
      long leftover = bd.skipArray();
      // booleans are one byte, array trailer is one byte
      bd.skipFixed((int)leftover + 1); 
      bd.skipFixed(0);
      bd.readLong();
    }
    EOFException eof = null;
    try {
      bd.skipFixed(4);
    } catch (EOFException e) {
      eof = e;
    }
    Assert.assertTrue(null != eof);
  }
}
