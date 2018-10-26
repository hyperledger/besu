/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.rlp;

import static junit.framework.TestCase.assertEquals;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class RLPTest {

  @Test
  public void calculateSize_singleByteValue() {
    int size = RLP.calculateSize(BytesValue.fromHexString("0x01"));
    assertEquals(1, size);
  }

  @Test
  public void calculateSize_smallByteString() {
    // Prefix indicates a payload of size 5, with a 1 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0x85"));
    assertEquals(6, size);
  }

  @Test
  public void calculateSize_longByteString() {
    // Prefix indicates a payload of 56 bytes, with a 2 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0xB838"));
    assertEquals(58, size);
  }

  @Test
  public void calculateSize_longByteStringWithMultiByteSize() {
    // Prefix indicates a payload of 258 bytes, with a 3 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0xB90102"));
    assertEquals(261, size);
  }

  @Test
  public void calculateSize_shortList() {
    // Prefix indicates a payload of 5 bytes, with a 1 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0xC5"));
    assertEquals(6, size);
  }

  @Test
  public void calculateSize_longList() {
    // Prefix indicates a payload of 56 bytes, with a 2 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0xF838"));
    assertEquals(58, size);
  }

  @Test
  public void calculateSize_longListWithMultiByteSize() {
    // Prefix indicates a payload of 258 bytes, with a 3 byte prefix
    int size = RLP.calculateSize(BytesValue.fromHexString("0xF90102"));
    assertEquals(261, size);
  }
}
