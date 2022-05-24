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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.util.function.Function;
import java.util.stream.Stream;

public class HeaderRangeValidationStep implements Function<RoundRangeHeaders, Stream<BlockHeader>> {

  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final ValidationPolicy validationPolicy;

  public HeaderRangeValidationStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final ValidationPolicy validationPolicy) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.validationPolicy = validationPolicy;
  }

  @Override
  public Stream<BlockHeader> apply(final RoundRangeHeaders checkpointRangeHeaders) {
    final BlockHeader rangeStart = checkpointRangeHeaders.getCheckpointRange().getStart();
    final BlockHeader firstHeaderToImport = checkpointRangeHeaders.getFirstHeaderToImport();

    if (isValid(rangeStart, firstHeaderToImport)) {
      return checkpointRangeHeaders.getHeadersToImport().stream();
    } else {
      final String rangeEndDescription;
      if (checkpointRangeHeaders.getCheckpointRange().hasEnd()) {
        final BlockHeader rangeEnd = checkpointRangeHeaders.getCheckpointRange().getEnd();
        rangeEndDescription =
            String.format("#%d (%s)", rangeEnd.getNumber(), rangeEnd.getBlockHash());
      } else {
        rangeEndDescription = "chain head";
      }
      final String errorMessage =
          String.format(
              "Invalid checkpoint headers.  Headers downloaded between #%d (%s) and %s do not connect at #%d (%s)",
              rangeStart.getNumber(),
              rangeStart.getHash(),
              rangeEndDescription,
              firstHeaderToImport.getNumber(),
              firstHeaderToImport.getHash());
      throw new InvalidBlockException(
          errorMessage, firstHeaderToImport.getNumber(), firstHeaderToImport.getHash());
    }
  }

  private boolean isValid(final BlockHeader expectedParent, final BlockHeader firstHeaderToImport) {
    final ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockNumber(firstHeaderToImport.getNumber());
    final BlockHeaderValidator validator = protocolSpec.getBlockHeaderValidator();
    return validator.validateHeader(
        firstHeaderToImport,
        expectedParent,
        protocolContext,
        validationPolicy.getValidationModeForNextBlock());
  }
}
