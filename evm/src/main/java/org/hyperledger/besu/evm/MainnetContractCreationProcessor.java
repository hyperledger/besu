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
package org.hyperledger.besu.evm;

// import org.hyperledger.besu.ethereum.core.Account;
// import org.hyperledger.besu.ethereum.core.ModificationNotAllowedException;
// import org.hyperledger.besu.ethereum.core.MutableAccount;

import org.hyperledger.besu.plugin.data.Account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/** A contract creation message processor. */
public class MainnetContractCreationProcessor extends AbstractMessageProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean requireCodeDepositToSucceed;

  private final GasCalculator gasCalculator;

  private final long initialContractNonce;

  private final List<ContractValidationRule> contractValidationRules;

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final List<ContractValidationRule> contractValidationRules,
      final long initialContractNonce,
      final Collection<Address> forceCommitAddresses) {
    super(evm, forceCommitAddresses);
    this.gasCalculator = gasCalculator;
    this.requireCodeDepositToSucceed = requireCodeDepositToSucceed;
    this.contractValidationRules = contractValidationRules;
    this.initialContractNonce = initialContractNonce;
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
        ImmutableSet.of());
  }

  private static boolean accountExists(final Account account) {
    // The account exists if it has sent a transaction
    // or already has its code initialized.
    return account.getNonce() > 0 || !account.getCodeHash().isEmpty();
  }

  @Override
  public void start(final MessageFrame frame, final OperationTracer operationTracer) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing contract-creation");
    }
    //    try {
    final Address senderAddress = frame.getSenderAddress();
    EVMWorldState worldState = frame.getWorldState();
    final var sender = worldState.getAccount(senderAddress);
    final var initialSenderBalance = Wei.fromQuantity(sender.getBalance());
    final var updatedSenderBalance = initialSenderBalance.subtractExact(frame.getValue());
    // sender.decrementBalance(frame.getValue());

    Address contractAddress = frame.getContractAddress();
    final var contract = worldState.getOrCreateAccount(contractAddress);
    // TODO check failure to create

    if (accountExists(contract.get())) {
      LOG.trace(
          "Contract creation error: account as already been created for address {}",
          contractAddress);
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      operationTracer.traceAccountCreationResult(
          frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    } else {
      worldState.updateAccountBalance(senderAddress, updatedSenderBalance);
      worldState.updateAccountBalance(contractAddress, frame.getValue());
      worldState.updateAccountNonce(contractAddress, initialContractNonce);
      worldState.getAccountState(contractAddress).clearStorage();
      frame.setState(MessageFrame.State.CODE_EXECUTING);
    }
    //    } catch (final ModificationNotAllowedException ex) {
    //      LOG.trace("Contract creation error: illegal modification not allowed from private
    // state");
    //      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    //    }
  }

  @Override
  protected void codeSuccess(final MessageFrame frame, final OperationTracer operationTracer) {
    final Bytes contractCode = frame.getOutputData();

    final Gas depositFee = gasCalculator.codeDepositGasCost(contractCode.size());

    Address contractAddress = frame.getContractAddress();
    if (frame.getRemainingGas().compareTo(depositFee) < 0) {
      LOG.trace(
          "Not enough gas to pay the code deposit fee for {}: "
              + "remaining gas = {} < {} = deposit fee",
          contractAddress,
          frame.getRemainingGas(),
          depositFee);
      if (requireCodeDepositToSucceed) {
        LOG.trace("Contract creation error: insufficient funds for code deposit");
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(
            frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    } else {
      if (contractValidationRules.stream().allMatch(rule -> rule.validate(frame))) {
        frame.decrementRemainingGas(depositFee);

        // Finalize contract creation, setting the contract code.
        //        final var contract =
        //            frame.getWorldState().getAccount(frame.getContractAddress());
        frame.getWorldState().updateAccountCode(contractAddress, contractCode);
        //        contract.setCode(contractCode);
        LOG.trace(
            "Successful creation of contract {} with code of size {} (Gas remaining: {})",
            contractAddress,
            contractCode.size(),
            frame.getRemainingGas());
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      } else {
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(
            frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      }
    }
  }
}
