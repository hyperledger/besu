/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.rlp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;

import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class RLPTest {

  @Test
  public void calculateSize_singleByteValue() {
    int size = RLP.calculateSize(Bytes.fromHexString("0x01"));
    assertThat(size).isEqualTo(1);
  }

  @Test
  public void calculateSize_minSingleByteValue() {
    int size = RLP.calculateSize(Bytes.fromHexString("0x00"));
    assertThat(size).isEqualTo(1);
  }

  @Test
  public void calculateSize_maxSingleByteValue() {
    int size = RLP.calculateSize(Bytes.fromHexString("0x7F"));
    assertThat(size).isEqualTo(1);
  }

  @Test
  public void calculateSize_smallByteString() {
    // Prefix indicates a payload of size 5, with a 1 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0x85"));
    assertThat(size).isEqualTo(6);
  }

  @Test
  public void calculateSize_nullByteString() {
    // Prefix indicates a payload of size 0, with a 1 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0x80"));
    assertThat(size).isEqualTo(1);
  }

  @Test
  public void calculateSize_minNonNullSmallByteString() {
    // Prefix indicates a payload of size 1, with a 1 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0x81"));
    assertThat(size).isEqualTo(2);
  }

  @Test
  public void calculateSize_maxSmallByteString() {
    // Prefix indicates a payload of size 55, with a 1 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xB7"));
    assertThat(size).isEqualTo(56);
  }

  @Test
  public void calculateSize_longByteString() {
    // Prefix indicates a payload of 56 bytes, with a 2 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xB838"));
    assertThat(size).isEqualTo(58);
  }

  @Test
  public void calculateSize_longByteStringWithMultiByteSize() {
    // Prefix indicates a payload of 258 bytes, with a 3 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xB90102"));
    assertThat(size).isEqualTo(261);
  }

  @Test
  public void calculateSize_shortList() {
    // Prefix indicates a payload of 5 bytes, with a 1 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xC5"));
    assertThat(size).isEqualTo(6);
  }

  @Test
  public void calculateSize_emptyList() {
    int size = RLP.calculateSize(Bytes.fromHexString("0xC0"));
    assertThat(size).isEqualTo(1);
  }

  @Test
  public void calculateSize_minNonEmptyList() {
    int size = RLP.calculateSize(Bytes.fromHexString("0xC1"));
    assertThat(size).isEqualTo(2);
  }

  @Test
  public void calculateSize_maxShortList() {
    int size = RLP.calculateSize(Bytes.fromHexString("0xF7"));
    assertThat(size).isEqualTo(56);
  }

  @Test
  public void calculateSize_longList() {
    // Prefix indicates a payload of 56 bytes, with a 2 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xF838"));
    assertThat(size).isEqualTo(58);
  }

  @Test
  public void calculateSize_longListWithMultiByteSize() {
    // Prefix indicates a payload of 258 bytes, with a 3 byte prefix
    int size = RLP.calculateSize(Bytes.fromHexString("0xF90102"));
    assertThat(size).isEqualTo(261);
  }

  @Test
  public void calculateSize_fuzz() {
    final Random random = new Random(1);
    for (int i = 0; i < 1000; ++i) {
      BytesValueRLPOutput out = RLPTestUtil.randomRLPValue(random.nextInt());
      assertThat(RLP.calculateSize(out.encoded())).isEqualTo(out.encodedSize());
    }
  }

  @Test
  public void calculateSize_extremelyDeepNestedList() {
    final int MAX_DEPTH = 20000;

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    int depth = 0;

    for (int i = 0; i < MAX_DEPTH; ++i) {
      out.startList();
      depth += 1;
    }
    while (depth > 0) {
      out.endList();
      --depth;
    }
    assertThat(RLP.calculateSize(out.encoded())).isEqualTo(out.encodedSize());
  }

  @Test
  public void calculateSize_maxRLPStringLength() {
    // Value represents a single item with an encoded payload size of MAX_VALUE - 5 and
    // 5 bytes of metadata (payload is not actually present)
    assertThat(RLP.calculateSize(h("0xBB7FFFFFFA"))).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void calculateSize_overflowMaxRLPStringLength() {
    // Value represents a single item with an encoded payload size of MAX_VALUE - 4 and
    // 5 bytes of metadata (payload is not actually present)
    assertThatThrownBy(() -> RLP.calculateSize(h("0xBB7FFFFFFB")))
        .isInstanceOf(RLPException.class)
        .hasMessageContaining("RLP item exceeds max supported size of 2147483647: 2147483648");
  }

  private static Bytes h(final String hex) {
    return Bytes.fromHexString(hex);
  }
}
