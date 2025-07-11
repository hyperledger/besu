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

import org.hyperledger.besu.collections.undo.UndoList;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.ValidationPolicy;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

public class RangeHeadersValidationStep implements Function<RangeHeaders, Stream<BlockHeader>> {
  private static final Logger LOG = LoggerFactory.getLogger(RangeHeadersValidationStep.class);
  private static final int LOG_REPEAT_DELAY_SECONDS = 30;

  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final ValidationPolicy validationPolicy;
  private final MutableBlockchain blockchain;
  private final boolean isPoS;
  private final long lastPoWBlockNumber;
  private final Optional<ConsensusContext> consensusContext;
  private final AtomicBoolean shouldLog = new AtomicBoolean(true);

  public RangeHeadersValidationStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final ValidationPolicy validationPolicy,
      final MutableBlockchain blockchain,
      final boolean isPoS,
      final long checkpointBlockNumber,
      final Optional<ConsensusContext> consensusContext){
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.validationPolicy = validationPolicy;
    this.blockchain = blockchain;
    this.isPoS = isPoS;
    this.lastPoWBlockNumber = checkpointBlockNumber - 1;
    this.consensusContext = consensusContext;
    shouldLog.set(true);
  }

  @Override
  public Stream<BlockHeader> apply(final RangeHeaders rangeHeaders) {
    final BlockHeader rangeStart = rangeHeaders.getRange().getStart();

    return rangeHeaders
        .getFirstHeaderToImport()
        .map(
            firstHeader -> {
              if (isValid(rangeStart, firstHeader)) {
                return storeOrPassOn(rangeHeaders.getHeadersToImport());
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
                throw InvalidBlockException.fromInvalidBlock(errorMessage, firstHeader);
              }
            })
        .orElse(Stream.empty());
  }

  public Stream<BlockHeader> storeOrPassOn(final List<BlockHeader> blockHeaders) {
    if (blockHeaders.getFirst().getNumber() > lastPoWBlockNumber) {
      // we are not pos or all headers are post-merge, so we can just pass them through
      return blockHeaders.stream();
    } else if (blockHeaders.getLast().getNumber() < lastPoWBlockNumber) {
      // all headers are post-merge, so we can just pass them through
      storeBlockHeaders(blockHeaders);
      logProgress(blockHeaders.getLast());
      return Stream.empty();
    } else {
      // the last PoW header is included, so we store all headers up to and including it and we pass
      // the rest through
      final List<BlockHeader> allHeaders = blockHeaders.stream().toList();
      final int preMergeEnd = (int) (lastPoWBlockNumber - allHeaders.getFirst().getNumber() + 1);
      final List<BlockHeader> preMergeHeaders = allHeaders.subList(0, preMergeEnd);
      final List<BlockHeader> postMergeHeaders = allHeaders.subList(preMergeEnd, allHeaders.size());
      storeBlockHeaders(preMergeHeaders);
      final BlockHeader lastPoWBlockHeader = preMergeHeaders.getLast();
      if (isPoS) {
        consensusContext.ifPresent(
                context ->
                        blockchain
                                .getTotalDifficultyByHash(lastPoWBlockHeader.getHash())
                                .ifPresent(context::setIsPostMerge));
      }
      LOG.info("Pre-merge headers import completed at block {}", lastPoWBlockNumber);
      return postMergeHeaders.stream();
    }
  }

  private void storeBlockHeaders( final List<BlockHeader> blockHeaders) {
    Difficulty difficulty = blockchain.calculateTotalDifficulty(blockHeaders.getFirst());
    for (BlockHeader blockHeader : blockHeaders) {
      blockchain.unsafeStoreHeader(blockHeader, difficulty);
      difficulty = difficulty.add(blockHeader.getDifficulty());
    }
  }

  private void logProgress(final BlockHeader blockHeader) {
    if (shouldLog.get()) {
      throttledLog(
              LOG::info,
              String.format(
                      "Pre-merge headers import progress: %d of %d (%.2f%%)",
                      blockHeader.getNumber(),
                      lastPoWBlockNumber,
                      (double) (100 * blockHeader.getNumber()) / lastPoWBlockNumber),
              shouldLog,
              LOG_REPEAT_DELAY_SECONDS);
    }
  }

  private boolean isValid(final BlockHeader expectedParent, final BlockHeader firstHeaderToImport) {
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(firstHeaderToImport);
    final BlockHeaderValidator validator = protocolSpec.getBlockHeaderValidator();
    return validator.validateHeader(
        firstHeaderToImport,
        expectedParent,
        protocolContext,
        validationPolicy.getValidationModeForNextBlock());
  }
}
