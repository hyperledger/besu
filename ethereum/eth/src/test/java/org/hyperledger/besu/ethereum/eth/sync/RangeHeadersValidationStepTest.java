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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeaders;
import org.hyperledger.besu.ethereum.eth.sync.range.RangeHeadersValidationStep;
import org.hyperledger.besu.ethereum.eth.sync.range.SyncTargetRange;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RangeHeadersValidationStepTest {
  @Mock private EthPeer syncTarget;

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private RangeHeadersValidationStep validationStep;
  private final List<Block> blocks = gen.blockSequence(14);
  private final BlockHeader rangeStart = blocks.get(10).getHeader();
  private final BlockHeader rangeEnd = blocks.get(13).getHeader();
  private final BlockHeader firstHeader = blocks.get(11).getHeader();
  private final RangeHeaders rangeHeaders =
      new RangeHeaders(
          new SyncTargetRange(syncTarget, rangeStart, rangeEnd),
          asList(firstHeader, blocks.get(12).getHeader(), rangeEnd));

  public void setUp() {
    validationStep = new RangeHeadersValidationStep();
  }

  @Test
  public void shouldValidateFirstHeaderAgainstRangeStartHeader() {
    setUp();

    final Stream<BlockHeader> result = validationStep.apply(rangeHeaders);
    assertThat(result).containsExactlyElementsOf(rangeHeaders.getHeadersToImport());
  }

  @Test
  public void shouldThrowExceptionWhenHeadersDontConnect() {
    setUp();

    // create an invalid range that does not connect to the first header
    BlockHeader invalidRangeStart = gen.header(10);
    RangeHeaders invalidRangeHeaders =
        new RangeHeaders(
            new SyncTargetRange(syncTarget, invalidRangeStart, rangeEnd),
            asList(firstHeader, blocks.get(12).getHeader(), rangeEnd));

    assertThatThrownBy(() -> validationStep.apply(invalidRangeHeaders))
        .isInstanceOf(InvalidBlockException.class)
        .hasMessageContaining(
            "Invalid range headers.  Headers downloaded between #"
                + invalidRangeStart.getNumber()
                + " ("
                + invalidRangeStart.getHash()
                + ") and #"
                + rangeEnd.getNumber()
                + " ("
                + rangeEnd.getHash()
                + ") do not connect at #"
                + firstHeader.getNumber()
                + " ("
                + firstHeader.getHash()
                + ")");
  }

  @Test
  public void acceptResponseWithNoHeadersAndNoSetUp() {
    // don't run the setUp
    validationStep = new RangeHeadersValidationStep();
    var emptyRangeHeaders =
        new RangeHeaders(new SyncTargetRange(syncTarget, rangeStart, rangeEnd), List.of());

    final Stream<BlockHeader> result = validationStep.apply(emptyRangeHeaders);
    assertThat(result).isEmpty();

    verifyNoMoreInteractions(syncTarget);
  }
}
