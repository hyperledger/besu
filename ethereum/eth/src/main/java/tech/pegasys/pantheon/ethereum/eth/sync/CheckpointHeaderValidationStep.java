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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Function;
import java.util.stream.Stream;

public class CheckpointHeaderValidationStep<C>
    implements Function<CheckpointRangeHeaders, Stream<BlockHeader>> {

  private final ProtocolSchedule<C> protocolSchedule;
  private final ProtocolContext<C> protocolContext;
  private final ValidationPolicy validationPolicy;

  public CheckpointHeaderValidationStep(
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final ValidationPolicy validationPolicy) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.validationPolicy = validationPolicy;
  }

  @Override
  public Stream<BlockHeader> apply(final CheckpointRangeHeaders checkpointRangeHeaders) {
    final BlockHeader rangeStart = checkpointRangeHeaders.getCheckpointRange().getStart();
    final BlockHeader firstHeaderToImport = checkpointRangeHeaders.getFirstHeaderToImport();

    if (isValid(rangeStart, firstHeaderToImport)) {
      return checkpointRangeHeaders.getHeadersToImport().stream();
    } else {
      final BlockHeader rangeEnd = checkpointRangeHeaders.getCheckpointRange().getEnd();
      final String errorMessage =
          String.format(
              "Invalid checkpoint headers.  Headers downloaded between #%d (%s) and #%d (%s) do not connect at #%d (%s)",
              rangeStart.getNumber(),
              rangeStart.getHash(),
              rangeEnd.getNumber(),
              rangeEnd.getHash(),
              firstHeaderToImport.getNumber(),
              firstHeaderToImport.getHash());
      throw new InvalidBlockException(
          errorMessage, firstHeaderToImport.getNumber(), firstHeaderToImport.getHash());
    }
  }

  private boolean isValid(final BlockHeader expectedParent, final BlockHeader firstHeaderToImport) {
    final BlockHeaderValidator<C> validator =
        protocolSchedule
            .getByBlockNumber(firstHeaderToImport.getNumber())
            .getBlockHeaderValidator();
    return validator.validateHeader(
        firstHeaderToImport,
        expectedParent,
        protocolContext,
        validationPolicy.getValidationModeForNextBlock());
  }
}
