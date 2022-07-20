/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.rollup.blockcreation;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeBlockCreator;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollupMergeCoordinator extends MergeCoordinator implements MergeMiningCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(RollupMergeCoordinator.class);

  public RollupMergeCoordinator(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final MiningParameters miningParams,
      final BackwardSyncContext backwardSyncContext) {
    super(
        protocolContext, protocolSchedule, pendingTransactions, miningParams, backwardSyncContext);
  }

  public static class PayloadCreationResult {

    private final PayloadIdentifier payloadIdentifier;
    private final BlockCreationResult blockCreationResult;
    private final BlockValidator.Result blockValidationResult;

    public PayloadCreationResult(
        final PayloadIdentifier payloadIdentifier,
        final BlockCreationResult blockCreationResult,
        final Result blockValidationResult) {
      this.payloadIdentifier = payloadIdentifier;
      this.blockCreationResult = blockCreationResult;
      this.blockValidationResult = blockValidationResult;
    }

    public PayloadIdentifier getPayloadId() {
      return payloadIdentifier;
    }

    public BlockCreationResult getBlockCreationResult() {
      return blockCreationResult;
    }

    public Result getBlockValidationResult() {
      return blockValidationResult;
    }
  }

  public PayloadCreationResult createBlock(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Address feeRecipient,
      final List<Transaction> transactions,
      final Bytes32 prevRandao) {
    final PayloadIdentifier payloadIdentifier =
        PayloadIdentifier.forPayloadParams(parentHeader.getBlockHash(), timestamp);
    final MergeBlockCreator mergeBlockCreator =
        this.mergeBlockCreator.forParams(parentHeader, Optional.ofNullable(feeRecipient));

    final BlockCreator.BlockCreationResult blockCreationResult =
        mergeBlockCreator.createBlock(Optional.of(transactions), prevRandao, timestamp);

    BlockValidator.Result blockValidationResult = validateBlock(blockCreationResult.getBlock());
    if (blockValidationResult.isValid) {
      mergeContext.putPayloadById(payloadIdentifier, blockCreationResult.getBlock());
    } else {
      LOG.warn(
          "Failed to execute new block {}, reason {}",
          blockCreationResult.getBlock().getHash(),
          blockValidationResult.errorMessage);
    }

    return new PayloadCreationResult(payloadIdentifier, blockCreationResult, blockValidationResult);
  }
}
