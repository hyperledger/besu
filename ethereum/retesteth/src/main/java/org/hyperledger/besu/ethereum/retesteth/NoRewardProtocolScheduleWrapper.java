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

import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockImporter;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;
import java.util.Optional;

public class NoRewardProtocolScheduleWrapper<C> implements ProtocolSchedule<C> {

  private final ProtocolSchedule<C> delegate;

  NoRewardProtocolScheduleWrapper(final ProtocolSchedule<C> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProtocolSpec<C> getByBlockNumber(final long number) {
    final ProtocolSpec<C> original = delegate.getByBlockNumber(number);
    final BlockProcessor noRewardBlockProcessor =
        new MainnetBlockProcessor(
            original.getTransactionProcessor(),
            original.getTransactionReceiptFactory(),
            Wei.ZERO,
            original.getMiningBeneficiaryCalculator(),
            original.isSkipZeroBlockRewards());
    final BlockValidator<C> noRewardBlockValidator =
        new MainnetBlockValidator<>(
            original.getBlockHeaderValidator(),
            original.getBlockBodyValidator(),
            noRewardBlockProcessor);
    final BlockImporter<C> noRewardBlockImporter =
        new MainnetBlockImporter<>(noRewardBlockValidator);
    return new ProtocolSpec<>(
        original.getName(),
        original.getEvm(),
        original.getTransactionValidator(),
        original.getTransactionProcessor(),
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
        original.getGasCalculator());
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return delegate.getChainId();
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    delegate.setTransactionFilter(transactionFilter);
  }
}
