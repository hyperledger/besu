/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class Era1BlockIndexConverterTest {
  @Test
  public void testConvert() {
    Block block = Mockito.mock(Block.class);
    BlockHeader blockHeader = Mockito.mock(BlockHeader.class);
    Mockito.when(block.getHeader()).thenReturn(blockHeader);
    Mockito.when(blockHeader.getNumber()).thenReturn(789L);

    Era1BlockIndexConverter era1BlockIndexConverter = new Era1BlockIndexConverter();
    byte[] actualBlockIndex =
        era1BlockIndexConverter.convert(List.of(block), Map.of(block, 123L), 456);

    Assertions.assertEquals(24, actualBlockIndex.length);
    Assertions.assertEquals(
        Bytes.ofUnsignedLong(789L, ByteOrder.LITTLE_ENDIAN), Bytes.wrap(actualBlockIndex, 0, 8));
    Assertions.assertEquals(
        Bytes.ofUnsignedLong(123 - 456, ByteOrder.LITTLE_ENDIAN),
        Bytes.wrap(actualBlockIndex, 8, 8));
    Assertions.assertEquals(
        Bytes.ofUnsignedLong(1, ByteOrder.LITTLE_ENDIAN), Bytes.wrap(actualBlockIndex, 16, 8));
  }
}
