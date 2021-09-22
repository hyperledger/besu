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
package org.hyperledger.besu.ethereum.retesteth;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

public class NoRewardProtocolScheduleWrapper implements ProtocolSchedule {

  private final ProtocolSchedule delegate;

  NoRewardProtocolScheduleWrapper(final ProtocolSchedule delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProtocolSpec getByBlockNumber(final long number) {
    final ProtocolSpec original = delegate.getByBlockNumber(number);
    final BlockProcessor noRewardBlockProcessor =
        new MainnetBlockProcessor(
            original.getTransactionProcessor(),
            original.getTransactionReceiptFactory(),
            Wei.ZERO,
            original.getMiningBeneficiaryCalculator(),
            original.isSkipZeroBlockRewards(),
            Optional.empty());
    final BlockValidator noRewardBlockValidator =
        new MainnetBlockValidator(
            original.getBlockHeaderValidator(),
            original.getBlockBodyValidator(),
            noRewardBlockProcessor,
            original.getBadBlocksManager());
    final BlockImporter noRewardBlockImporter = new MainnetBlockImporter(noRewardBlockValidator);
    return new ProtocolSpec(
        original.getName(),
        original.getEvm(),
        original.getTransactionValidator(),
        original.getTransactionProcessor(),
        original.getPrivateTransactionProcessor(),
        original.getBlockHeaderValidator(),
        original.getOmmerHeaderValidator(),
        original.getBlockBodyValidator(),
        noRewardBlockProcessor,
        noRewardBlockImporter,
        noRewardBlockValidator,
        original.getBlockHeaderFunctions(),
        original.getTransactionReceiptFactory(),
        original.getDifficultyCalculator(),
        Wei.ZERO, // block reward
        original.getMiningBeneficiaryCalculator(),
        original.getPrecompileContractRegistry(),
        original.isSkipZeroBlockRewards(),
        original.getGasCalculator(),
        original.getGasLimitCalculator(),
        original.getFeeMarket(),
        original.getBadBlocksManager(),
        Optional.empty());
  }

  @Override
  public Stream<Long> streamMilestoneBlocks() {
    return delegate.streamMilestoneBlocks();
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return delegate.getChainId();
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    delegate.setTransactionFilter(transactionFilter);
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    delegate.setPublicWorldStateArchiveForPrivacyBlockProcessor(publicWorldStateArchive);
  }
}
