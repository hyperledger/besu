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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.frame.Memory;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class MemoryTest {

  private final Memory memory = new Memory();
  private static final Bytes32 WORD1 = fillBytes32(1);
  private static final Bytes32 WORD2 = fillBytes32(2);
  private static final Bytes32 WORD3 = fillBytes32(3);
  private static final Bytes32 WORD4 = fillBytes32(4);

  @Test
  public void shouldSetAndGetMemoryByWord() {
    final int index = 20;
    final Bytes32 value = Bytes32.fromHexString("0xABCDEF");
    memory.setWord(index, value);
    assertThat(memory.getWord(index)).isEqualTo(value);
  }

  @Test
  public void shouldSetMemoryWhenLengthEqualToSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2, WORD3);
    memory.setBytes(0, value.size(), value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
  }

  @Test
  public void shouldSetMemoryWhenLengthLessThanSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2, WORD3);
    memory.setBytes(0, 64, value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldSetMemoryWhenLengthGreaterThanSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(0, 96, value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldNotIncreaseActiveWordsIfGetBytesWithoutGrowth() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(0, value.size(), value);
    final int initialActiveWords = memory.getActiveWords();

    assertThat(memory.getBytesWithoutGrowth(64, Bytes32.SIZE)).isEqualTo((Bytes32.ZERO));
    assertThat(memory.getActiveWords()).isEqualTo(initialActiveWords);

    assertThat(memory.getBytes(32, Bytes32.SIZE)).isEqualTo((WORD2));
    assertThat(memory.getActiveWords()).isEqualTo(initialActiveWords);

    assertThat(memory.getBytes(64, Bytes32.SIZE)).isEqualTo((Bytes32.ZERO));
    assertThat(memory.getActiveWords()).isEqualTo(initialActiveWords + 1);
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenLengthGreaterThanSourceLength() {
    memory.setWord(64, WORD3);
    memory.setWord(96, WORD4);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(0, 96, value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenLengthGreaterThanSourceLengthWithMemoryOffset() {
    memory.setWord(64, WORD3);
    memory.setWord(96, WORD4);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(10, 96, value);
    assertThat(memory.getWord(10)).isEqualTo(WORD1);
    assertThat(memory.getWord(42)).isEqualTo(WORD2);
    assertThat(memory.getWord(74)).isEqualTo(Bytes32.ZERO);
    // Word 4 got partially cleared because of the starting offset.
    assertThat(memory.getWord(106))
        .isEqualTo(
            Bytes32.fromHexString(
                "0x4444444444444444444444444444444444444444444400000000000000000000"));
  }

  @Test
  public void shouldClearMemoryAfterSourceDataWhenSourceOffsetPlusLengthGreaterThanSourceLength() {
    memory.setWord(64, WORD3);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(0, 32, 64, value);
    assertThat(memory.getWord(0)).isEqualTo(WORD2);
    assertThat(memory.getWord(32)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
  }

  @Test
  public void shouldClearMemoryWhenSourceOffsetIsGreaterThanSourceLength() {
    memory.setWord(64, WORD3);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytes(0, 94, 64, value);
    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
  }

  @Test
  public void shouldClearMemoryWhenSourceDataIsEmpty() {
    memory.setWord(64, WORD3);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);

    memory.setBytes(0, 96, Bytes.EMPTY);

    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldClearMemoryWhenSourceDataIsEmptyWithSourceOffset() {
    memory.setWord(64, WORD3);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);

    memory.setBytes(0, 0, 96, Bytes.EMPTY);

    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }

  private static Bytes32 fillBytes32(final long value) {
    return Bytes32.fromHexString(Long.toString(value).repeat(64));
  }

  @Test
  public void shouldSetMemoryRightAlignedWhenLengthEqualToSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2, WORD3);
    memory.setBytesRightAligned(0, value.size(), value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
  }

  @Test
  public void shouldSetMemoryRightAlignedWhenLengthLessThanSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2, WORD3);
    memory.setBytesRightAligned(0, 64, value);
    assertThat(memory.getWord(0)).isEqualTo(WORD1);
    assertThat(memory.getWord(32)).isEqualTo(WORD2);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldSetMemoryRightAlignedWhenLengthGreaterThanSourceLength() {
    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytesRightAligned(0, 96, value);
    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(WORD1);
    assertThat(memory.getWord(64)).isEqualTo(WORD2);
  }

  @Test
  public void shouldClearMemoryRightAlignedAfterSourceDataWhenLengthGreaterThanSourceLength() {
    memory.setWord(64, WORD3);
    memory.setWord(96, WORD4);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytesRightAligned(0, 96, value);
    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(WORD1);
    assertThat(memory.getWord(64)).isEqualTo(WORD2);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);
  }

  @Test
  public void
      shouldClearMemoryRightAlignedAfterSourceDataWhenLengthGreaterThanSourceLengthWithMemoryOffset() {
    memory.setWord(64, WORD3);
    memory.setWord(96, WORD4);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);
    assertThat(memory.getWord(96)).isEqualTo(WORD4);

    final Bytes value = Bytes.concatenate(WORD1, WORD2);
    memory.setBytesRightAligned(10, 96, value);
    assertThat(memory.getWord(10)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(42)).isEqualTo(WORD1);
    assertThat(memory.getWord(74)).isEqualTo(WORD2);
    // Word 4 got partially set because of the starting offset.
    assertThat(memory.getWord(96))
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2222222222222222222244444444444444444444444444444444444444444444"));
  }

  @Test
  public void shouldClearMemoryRightAlignedWhenSourceDataIsEmpty() {
    memory.setWord(64, WORD3);
    assertThat(memory.getWord(64)).isEqualTo(WORD3);

    memory.setBytesRightAligned(0, 96, Bytes.EMPTY);

    assertThat(memory.getWord(0)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(32)).isEqualTo(Bytes32.ZERO);
    assertThat(memory.getWord(64)).isEqualTo(Bytes32.ZERO);
  }
}
