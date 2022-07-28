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
package org.hyperledger.besu.ethereum.eth.sync.range;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.util.function.Function;
import java.util.stream.Stream;

public class RangeHeadersValidationStep implements Function<RangeHeaders, Stream<BlockHeader>> {

  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final ValidationPolicy validationPolicy;

  public RangeHeadersValidationStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final ValidationPolicy validationPolicy) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.validationPolicy = validationPolicy;
  }

  @Override
  public Stream<BlockHeader> apply(final RangeHeaders rangeHeaders) {
    final BlockHeader rangeStart = rangeHeaders.getRange().getStart();

    return rangeHeaders
        .getFirstHeaderToImport()
        .map(
            firstHeader -> {
              if (isValid(rangeStart, firstHeader)) {
                return rangeHeaders.getHeadersToImport().stream();
              } else {
                final String rangeEndDescription;
                if (rangeHeaders.getRange().hasEnd()) {
                  final BlockHeader rangeEnd = rangeHeaders.getRange().getEnd();
                  rangeEndDescription =
                      String.format("#%d (%s)", rangeEnd.getNumber(), rangeEnd.getBlockHash());
                } else {
                  rangeEndDescription = "chain head";
                }
                final String errorMessage =
                    String.format(
                        "Invalid range headers.  Headers downloaded between #%d (%s) and %s do not connect at #%d (%s)",
                        rangeStart.getNumber(),
                        rangeStart.getHash(),
                        rangeEndDescription,
                        firstHeader.getNumber(),
                        firstHeader.getHash());
                throw new InvalidBlockException(
                    errorMessage, firstHeader.getNumber(), firstHeader.getHash());
              }
            })
        .orElse(Stream.empty());
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
