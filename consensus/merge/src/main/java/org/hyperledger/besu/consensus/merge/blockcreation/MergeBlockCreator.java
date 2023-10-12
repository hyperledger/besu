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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

/** The Merge block creator. */
class MergeBlockCreator extends AbstractBlockCreator {

  /**
   * Instantiates a new Merge block creator.
   *
   * @param miningParameters the mining parameters
   * @param extraDataCalculator the extra data calculator
   * @param transactionPool the pending transactions
   * @param protocolContext the protocol context
   * @param protocolSchedule the protocol schedule
   * @param miningBeneficiary the mining beneficiary
   * @param parentHeader the parent header
   */
  public MergeBlockCreator(
      final MiningParameters miningParameters,
      final ExtraDataCalculator extraDataCalculator,
      final TransactionPool transactionPool,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final Address miningBeneficiary,
      final BlockHeader parentHeader,
      final Optional<Address> depositContractAddress) {
    super(
        miningParameters,
        miningBeneficiary,
        __ -> miningBeneficiary,
        extraDataCalculator,
        transactionPool,
        protocolContext,
        protocolSchedule,
        parentHeader,
        depositContractAddress);
  }

  /**
   * Create block and return block creation result.
   *
   * @param maybeTransactions the maybe transactions
   * @param random the random
   * @param timestamp the timestamp
   * @param withdrawals optional list of withdrawals
   * @param parentBeaconBlockRoot optional root hash of the parent beacon block
   * @return the block creation result
   */
  public BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Bytes32 random,
      final long timestamp,
      final Optional<List<Withdrawal>> withdrawals,
      final Optional<Bytes32> parentBeaconBlockRoot) {

    return createBlock(
        maybeTransactions,
        Optional.of(Collections.emptyList()),
        withdrawals,
        Optional.of(random),
        parentBeaconBlockRoot,
        timestamp,
        false);
  }

  @Override
  public BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp) {
    throw new UnsupportedOperationException("random is required");
  }

  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
    return BlockHeaderBuilder.create()
        .difficulty(Difficulty.ZERO)
        .populateFrom(sealableBlockHeader)
        .nonce(0L)
        .blockHeaderFunctions(blockHeaderFunctions)
        .buildBlockHeader();
  }
}
