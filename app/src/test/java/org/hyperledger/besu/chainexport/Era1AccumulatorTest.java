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

import org.hyperledger.besu.util.ssz.Merkleizer;

import java.nio.ByteOrder;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Era1AccumulatorTest {
  private @Mock Merkleizer merkleizer;

  private Era1Accumulator era1Accumulator;

  @BeforeEach
  public void beforeTest() {
    era1Accumulator = new Era1Accumulator(merkleizer);
  }

  @Test
  public void testAccumulate() {
    // mock addBlock calls
    Bytes32 blockHash1 = Bytes32.random();
    UInt256 totalDifficulty1 = UInt256.ONE;
    Bytes32 block1Accumulation = Bytes32.random();
    Bytes32 blockHash2 = Bytes32.random();
    UInt256 totalDifficulty2 = UInt256.valueOf(2);
    Bytes32 block2Accumulation = Bytes32.random();
    Mockito.when(
            merkleizer.merkleizeChunks(
                Mockito.argThat(
                    new ListOfBytes32ArgumentMatcher(
                        blockHash1,
                        Bytes32.wrap(totalDifficulty1.toArray(ByteOrder.LITTLE_ENDIAN))))))
        .thenReturn(block1Accumulation);
    Mockito.when(
            merkleizer.merkleizeChunks(
                Mockito.argThat(
                    new ListOfBytes32ArgumentMatcher(
                        blockHash2,
                        Bytes32.wrap(totalDifficulty2.toArray(ByteOrder.LITTLE_ENDIAN))))))
        .thenReturn(block2Accumulation);

    // mock accumulate call
    Bytes32 rootHash = Bytes32.random();
    Bytes32 expectedAccumulatorHash = Bytes32.random();
    Mockito.when(
            merkleizer.merkleizeChunks(
                Mockito.argThat(
                    new ListOfBytes32ArgumentMatcher(block1Accumulation, block2Accumulation)),
                Mockito.eq(8192)))
        .thenReturn(rootHash);
    Mockito.when(merkleizer.mixinLength(rootHash, UInt256.valueOf(2)))
        .thenReturn(expectedAccumulatorHash);

    era1Accumulator.addBlock(blockHash1, totalDifficulty1);
    era1Accumulator.addBlock(blockHash2, totalDifficulty2);
    Bytes32 actualAccumulatorHash = era1Accumulator.accumulate();

    // verify addBlock calls
    Mockito.verify(merkleizer)
        .merkleizeChunks(
            Mockito.argThat(
                new ListOfBytes32ArgumentMatcher(
                    blockHash1, Bytes32.wrap(totalDifficulty1.toArray(ByteOrder.LITTLE_ENDIAN)))));
    Mockito.verify(merkleizer)
        .merkleizeChunks(
            Mockito.argThat(
                new ListOfBytes32ArgumentMatcher(
                    blockHash2, Bytes32.wrap(totalDifficulty2.toArray(ByteOrder.LITTLE_ENDIAN)))));

    // verify accumulate call
    Mockito.verify(merkleizer)
        .merkleizeChunks(
            Mockito.argThat(
                new ListOfBytes32ArgumentMatcher(block1Accumulation, block2Accumulation)),
            Mockito.eq(8192));
    Mockito.verify(merkleizer).mixinLength(rootHash, UInt256.valueOf(2));

    Assertions.assertEquals(expectedAccumulatorHash, actualAccumulatorHash);
  }

  private static class ListOfBytes32ArgumentMatcher implements ArgumentMatcher<List<Bytes32>> {
    private final List<Bytes32> expectedArgument;

    private ListOfBytes32ArgumentMatcher(final Bytes32... expectedArgs) {
      this.expectedArgument = List.of(expectedArgs);
    }

    @Override
    public boolean matches(final List<Bytes32> actualArgument) {
      if (actualArgument == null) {
        return false;
      }
      if (expectedArgument.size() != actualArgument.size()) {
        return false;
      }
      for (int i = 0; i < expectedArgument.size(); i++) {
        if (!expectedArgument.get(i).equals(actualArgument.get(i))) {
          return false;
        }
      }
      return true;
    }
  }
}
