/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode.DETACHED_ONLY;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CheckpointHeaderValidationStepTest {
  @Mock private ProtocolSchedule<Void> protocolSchedule;
  @Mock private ProtocolSpec<Void> protocolSpec;
  @Mock private ProtocolContext<Void> protocolContext;
  @Mock private BlockHeaderValidator<Void> headerValidator;
  @Mock private ValidationPolicy validationPolicy;
  @Mock private EthPeer syncTarget;
  private final BlockDataGenerator gen = new BlockDataGenerator();
  private CheckpointHeaderValidationStep<Void> validationStep;

  private final BlockHeader checkpointStart = gen.header(10);
  private final BlockHeader checkpointEnd = gen.header(13);
  private final BlockHeader firstHeader = gen.header(11);
  private final CheckpointRangeHeaders rangeHeaders =
      new CheckpointRangeHeaders(
          new CheckpointRange(syncTarget, checkpointStart, checkpointEnd),
          asList(firstHeader, gen.header(12), checkpointEnd));

  @Before
  public void setUp() {
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockHeaderValidator()).thenReturn(headerValidator);
    when(validationPolicy.getValidationModeForNextBlock()).thenReturn(DETACHED_ONLY);

    validationStep =
        new CheckpointHeaderValidationStep<>(protocolSchedule, protocolContext, validationPolicy);
  }

  @Test
  public void shouldValidateFirstHeaderAgainstCheckpointStartHeader() {
    when(headerValidator.validateHeader(
            firstHeader, checkpointStart, protocolContext, DETACHED_ONLY))
        .thenReturn(true);
    final Stream<BlockHeader> result = validationStep.apply(rangeHeaders);

    verify(protocolSchedule).getByBlockNumber(firstHeader.getNumber());
    verify(validationPolicy).getValidationModeForNextBlock();
    verify(headerValidator)
        .validateHeader(firstHeader, checkpointStart, protocolContext, DETACHED_ONLY);
    verifyNoMoreInteractions(headerValidator, validationPolicy);

    assertThat(result).containsExactlyElementsOf(rangeHeaders.getHeadersToImport());
  }

  @Test
  public void shouldThrowExceptionWhenValidationFails() {
    when(headerValidator.validateHeader(
            firstHeader, checkpointStart, protocolContext, DETACHED_ONLY))
        .thenReturn(false);
    assertThatThrownBy(() -> validationStep.apply(rangeHeaders))
        .isInstanceOf(InvalidBlockException.class)
        .hasMessageContaining(
            "Invalid checkpoint headers.  Headers downloaded between #"
                + checkpointStart.getNumber()
                + " ("
                + checkpointStart.getHash()
                + ") and #"
                + checkpointEnd.getNumber()
                + " ("
                + checkpointEnd.getHash()
                + ") do not connect at #"
                + firstHeader.getNumber()
                + " ("
                + firstHeader.getHash()
                + ")");
  }
}
