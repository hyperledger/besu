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
package org.hyperledger.besu.consensus.clique.blockcreation;

import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.common.ConsensusHelpers;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockScheduler;
import org.hyperledger.besu.ethereum.blockcreation.AbstractMinerExecutor;
import org.hyperledger.besu.ethereum.chain.EthHashObserver;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;

public class CliqueMinerExecutor extends AbstractMinerExecutor<CliqueContext, CliqueBlockMiner> {

  private final Address localAddress;
  private final NodeKey nodeKey;
  private final EpochManager epochManager;

  public CliqueMinerExecutor(
      final ProtocolContext<CliqueContext> protocolContext,
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final PendingTransactions pendingTransactions,
      final NodeKey nodeKey,
      final MiningParameters miningParams,
      final AbstractBlockScheduler blockScheduler,
      final EpochManager epochManager,
      final Function<Long, Long> gasLimitCalculator) {
    super(
        protocolContext,
        protocolSchedule,
        pendingTransactions,
        miningParams,
        blockScheduler,
        gasLimitCalculator);
    this.nodeKey = nodeKey;
    this.localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    this.epochManager = epochManager;
  }

  @Override
  public CliqueBlockMiner createMiner(
      final Subscribers<MinedBlockObserver> observers,
      final Subscribers<EthHashObserver> ethHashObservers,
      final BlockHeader parentHeader) {
    final Function<BlockHeader, CliqueBlockCreator> blockCreator =
        (header) ->
            new CliqueBlockCreator(
                localAddress, // TOOD(tmm): This can be removed (used for voting not coinbase).
                this::calculateExtraData,
                pendingTransactions,
                protocolContext,
                protocolSchedule,
                gasLimitCalculator,
                nodeKey,
                minTransactionGasPrice,
                header,
                epochManager);

    return new CliqueBlockMiner(
        blockCreator,
        protocolSchedule,
        protocolContext,
        observers,
        blockScheduler,
        parentHeader,
        localAddress);
  }

  @Override
  public Optional<Address> getCoinbase() {
    return Optional.of(localAddress);
  }

  @VisibleForTesting
  Bytes calculateExtraData(final BlockHeader parentHeader) {
    final List<Address> validators = Lists.newArrayList();

    final Bytes vanityDataToInsert =
        ConsensusHelpers.zeroLeftPad(extraData, CliqueExtraData.EXTRA_VANITY_LENGTH);
    // Building ON TOP of canonical head, if the next block is epoch, include validators.
    if (epochManager.isEpochBlock(parentHeader.getNumber() + 1)) {
      final VoteTally voteTally =
          protocolContext
              .getConsensusState()
              .getVoteTallyCache()
              .getVoteTallyAfterBlock(parentHeader);
      validators.addAll(voteTally.getValidators());
    }

    return CliqueExtraData.encodeUnsealed(vanityDataToInsert, validators);
  }
}
