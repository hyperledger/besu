/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A contract creation message processor. */
public class MainnetContractCreationProcessor extends AbstractMessageProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean requireCodeDepositToSucceed;

  private final GasCalculator gasCalculator;

  private final long initialContractNonce;

  private final List<ContractValidationRule> contractValidationRules;

  private final int accountVersion;

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final List<ContractValidationRule> contractValidationRules,
      final long initialContractNonce,
      final Collection<Address> forceCommitAddresses,
      final int accountVersion) {
    super(evm, forceCommitAddresses);
    this.gasCalculator = gasCalculator;
    this.requireCodeDepositToSucceed = requireCodeDepositToSucceed;
    this.contractValidationRules = contractValidationRules;
    this.initialContractNonce = initialContractNonce;
    this.accountVersion = accountVersion;
  }

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final List<ContractValidationRule> contractValidationRules,
      final long initialContractNonce,
      final Collection<Address> forceCommitAddresses) {
    this(
        gasCalculator,
        evm,
        requireCodeDepositToSucceed,
        contractValidationRules,
        initialContractNonce,
        forceCommitAddresses,
        Account.DEFAULT_VERSION);
  }

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final List<ContractValidationRule> contractValidationRules,
      final long initialContractNonce) {
    this(
        gasCalculator,
        evm,
        requireCodeDepositToSucceed,
        contractValidationRules,
        initialContractNonce,
        ImmutableSet.of(),
        Account.DEFAULT_VERSION);
  }

  private static boolean accountExists(final Account account) {
    // The account exists if it has sent a transaction
    // or already has its code initialized.
    return account.getNonce() > 0 || !account.getCode().isEmpty();
  }

  @Override
  public void start(final MessageFrame frame) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing contract-creation");
    }

    final MutableAccount sender = frame.getWorldState().getMutable(frame.getSenderAddress());
    sender.decrementBalance(frame.getValue());

    final MutableAccount contract = frame.getWorldState().getOrCreate(frame.getContractAddress());
    if (accountExists(contract)) {
      LOG.trace(
          "Contract creation error: account as already been created for address {}",
          frame.getContractAddress());
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    } else {
      contract.incrementBalance(frame.getValue());
      contract.setNonce(initialContractNonce);
      contract.clearStorage();
      frame.setState(MessageFrame.State.CODE_EXECUTING);
    }
  }

  @Override
  protected void codeSuccess(final MessageFrame frame) {
    final BytesValue contractCode = frame.getOutputData();

    final Gas depositFee = gasCalculator.codeDepositGasCost(contractCode.size());

    if (frame.getRemainingGas().compareTo(depositFee) < 0) {
      LOG.trace(
          "Not enough gas to pay the code deposit fee for {}: "
              + "remaining gas = {} < {} = deposit fee",
          frame.getContractAddress(),
          frame.getRemainingGas(),
          depositFee);
      if (requireCodeDepositToSucceed) {
        LOG.trace("Contract creation error: insufficient funds for code deposit");
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      } else {
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    } else {
      if (contractValidationRules.stream().allMatch(rule -> rule.validate(frame))) {
        frame.decrementRemainingGas(depositFee);

        // Finalize contract creation, setting the contract code.
        final MutableAccount contract =
            frame.getWorldState().getOrCreate(frame.getContractAddress());
        contract.setCode(contractCode);
        contract.setVersion(accountVersion);
        LOG.trace(
            "Successful creation of contract {} with code of size {} (Gas remaining: {})",
            frame.getContractAddress(),
            contractCode.size(),
            frame.getRemainingGas());
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      } else {
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      }
    }
  }
}
