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
package org.hyperledger.besu.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class Bytes32Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan32() {
    Bytes32.wrap(new byte[31]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan32() {
    Bytes32.wrap(new byte[33]);
  }

  @Test
  public void leftPadAValueToBytes32() {
    final Bytes32 b32 = Bytes32.leftPad(BytesValue.of(1, 2, 3));
    assertThat(b32.size()).isEqualTo(32);
    for (int i = 0; i < 28; ++i) {
      assertThat(b32.get(i)).isEqualTo((byte) 0);
    }
    assertThat(b32.get(29)).isEqualTo((byte) 1);
    assertThat(b32.get(30)).isEqualTo((byte) 2);
    assertThat(b32.get(31)).isEqualTo((byte) 3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenLeftPaddingValueLargerThan32() {
    Bytes32.leftPad(MutableBytesValue.create(33));
  }
}
