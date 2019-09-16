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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

import com.google.common.base.Strings;
import org.junit.Test;

public class MemoryTest {

  private final Memory memory = new Memory();
  private static final Bytes32 WORD1 = fillBytes32(1);
  private static final Bytes32 WORD2 = fillBytes32(2);
  private static final Bytes32 WORD3 = fillBytes32(3);
  private static final Bytes32 WORD4 = fillBytes32(4);

  @Test
  public void shouldSetAndGetMemoryByWord() {
    final UInt256 index = UInt256.of(20);
    final Bytes32 value = Bytes32.fromHexString("0xABCDEF");
    memory.setWord(index, value);
    assertThat(memory.getWord(index)).isEqualTo(value);
  }

  @Test
  public void shouldSetMemoryWhenLengthEqualToSourceLength() {
    final BytesValue value = BytesValues.concatenate(WORD1, WORD2, WORD3);
    memory.setBytes(UInt256.ZERO, UInt256.of(value.size()), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(WORD1);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);
  }

  @Test
  public void shouldSetMemoryWhenLengthLessThanSourceLength() {
    final BytesValue value = BytesValues.concatenate(WORD1, WORD2, WORD3);
    memory.setBytes(UInt256.ZERO, UInt256.of(64), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(WORD1);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldSetMemoryWhenLengthGreaterThanSourceLength() {
    final BytesValue value = BytesValues.concatenate(WORD1, WORD2);
    memory.setBytes(UInt256.ZERO, UInt256.of(96), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(WORD1);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenLengthGreaterThanSourceLength() {
    memory.setWord(UInt256.of(64), WORD3);
    memory.setWord(UInt256.of(96), WORD4);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);
    assertThat(memory.getWord(UInt256.of(96))).isEqualTo(WORD4);

    final BytesValue value = BytesValues.concatenate(WORD1, WORD2);
    memory.setBytes(UInt256.ZERO, UInt256.of(96), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(WORD1);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(96))).isEqualTo(WORD4);
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenLengthGreaterThanSourceLengthWithMemoryOffset() {
    memory.setWord(UInt256.of(64), WORD3);
    memory.setWord(UInt256.of(96), WORD4);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);
    assertThat(memory.getWord(UInt256.of(96))).isEqualTo(WORD4);

    final BytesValue value = BytesValues.concatenate(WORD1, WORD2);
    memory.setBytes(UInt256.of(10), UInt256.of(96), value);
    assertThat(memory.getWord(UInt256.of(10))).isEqualTo(WORD1);
    assertThat(memory.getWord(UInt256.of(42))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(74))).isEqualTo(Bytes32.ZERO);
    // Word 4 got partially cleared because of the starting offset.
    assertThat(memory.getWord(UInt256.of(106)))
        .isEqualTo(
            Bytes32.fromHexString(
                "0x4444444444444444444444444444444444444444444400000000000000000000"));
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenSourceOffsetPlusLengthGreaterThanSourceLength() {
    memory.setWord(UInt256.of(64), WORD3);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);

    final BytesValue value = BytesValues.concatenate(WORD1, WORD2);
    memory.setBytes(UInt256.ZERO, UInt256.of(32), UInt256.of(64), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(WORD2);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);
  }

  @Test
  public void shouldClearMemoryWhenSourceOffsetIsGreaterThanSourceLength() {
    memory.setWord(UInt256.of(64), WORD3);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);

    final BytesValue value = BytesValues.concatenate(WORD1, WORD2);
    memory.setBytes(UInt256.ZERO, UInt256.of(94), UInt256.of(64), value);
    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);
  }

  @Test
  public void shouldClearMemoryWhenSourceDataIsEmpty() {
    memory.setWord(UInt256.of(64), WORD3);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);

    memory.setBytes(UInt256.ZERO, UInt256.of(96), BytesValue.EMPTY);

    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldClearMemoryWhenSourceDataIsEmptyWithSourceOffset() {
    memory.setWord(UInt256.of(64), WORD3);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(WORD3);

    memory.setBytes(UInt256.ZERO, UInt256.ZERO, UInt256.of(96), BytesValue.EMPTY);

    assertThat(memory.getWord(UInt256.of(0))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(32))).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(UInt256.of(64))).isEqualTo(Bytes32.ZERO);
  }

  private static Bytes32 fillBytes32(final long value) {
    return Bytes32.fromHexString(Strings.repeat(Long.toString(value), 64));
  }
}
