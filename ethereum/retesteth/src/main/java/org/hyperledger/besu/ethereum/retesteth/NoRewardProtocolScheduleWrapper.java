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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.PermissionTransactionFilter;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Predicate;

public class NoRewardProtocolScheduleWrapper implements ProtocolSchedule {

  private final ProtocolSchedule delegate;

  NoRewardProtocolScheduleWrapper(final ProtocolSchedule delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader) {
    final ProtocolSpec original = delegate.getByBlockHeader(blockHeader);
    final BlockProcessor noRewardBlockProcessor =
        new MainnetBlockProcessor(
            original.getTransactionProcessor(),
            original.getTransactionReceiptFactory(),
            Wei.ZERO,
            original.getMiningBeneficiaryCalculator(),
            original.isSkipZeroBlockRewards(),
            delegate);
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
        original.getTransactionValidatorFactory(),
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
        Optional.empty(),
        original.getWithdrawalsValidator(),
        original.getWithdrawalsProcessor(),
        original.getDepositsValidator(),
        original.getExecutionWitnessValidator(),
        original.getHistoricalBlockHashProcessor(),
        original.isPoS(),
        original.isReplayProtectionSupported());
  }

  @Override
  public boolean anyMatch(final Predicate<ScheduledProtocolSpec> predicate) {
    return delegate.anyMatch(predicate);
  }

  @Override
  public boolean isOnMilestoneBoundary(final BlockHeader blockHeader) {
    return delegate.isOnMilestoneBoundary(blockHeader);
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return delegate.getChainId();
  }

  @Override
  public void putBlockNumberMilestone(final long blockNumber, final ProtocolSpec protocolSpec) {
    delegate.putBlockNumberMilestone(blockNumber, protocolSpec);
  }

  @Override
  public void putTimestampMilestone(final long timestamp, final ProtocolSpec protocolSpec) {
    delegate.putTimestampMilestone(timestamp, protocolSpec);
  }

  @Override
  public Optional<ScheduledProtocolSpec.Hardfork> hardforkFor(
      final Predicate<ScheduledProtocolSpec> predicate) {
    return delegate.hardforkFor(predicate);
  }

  @Override
  public String listMilestones() {
    return delegate.listMilestones();
  }

  @Override
  public void setPermissionTransactionFilter(
      final PermissionTransactionFilter permissionTransactionFilter) {
    delegate.setPermissionTransactionFilter(permissionTransactionFilter);
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    delegate.setPublicWorldStateArchiveForPrivacyBlockProcessor(publicWorldStateArchive);
  }
}
