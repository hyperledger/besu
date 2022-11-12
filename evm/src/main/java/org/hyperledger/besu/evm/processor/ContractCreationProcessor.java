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
package org.hyperledger.besu.evm.processor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A contract creation message processor. */
public class ContractCreationProcessor extends AbstractMessageProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ContractCreationProcessor.class);

  private final boolean requireCodeDepositToSucceed;

  private final GasCalculator gasCalculator;

  private final long initialContractNonce;

  private final List<ContractValidationRule> contractValidationRules;

  public ContractCreationProcessor(
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

  public ContractCreationProcessor(
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
    return account.getNonce() > 0 || !account.getCode().isEmpty();
  }

  @Override
  public void start(final MessageFrame frame, final OperationTracer operationTracer) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing contract-creation");
    }
    try {

      final MutableAccount sender = frame.getWorldUpdater().getSenderAccount(frame).getMutable();
      sender.decrementBalance(frame.getValue());

      final MutableAccount contract =
          frame.getWorldUpdater().getOrCreate(frame.getContractAddress()).getMutable();
      if (accountExists(contract)) {
        LOG.trace(
            "Contract creation error: account has already been created for address {}",
            frame.getContractAddress());
        frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(
            frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        contract.incrementBalance(frame.getValue());
        contract.setNonce(initialContractNonce);
        contract.clearStorage();
        frame.setState(MessageFrame.State.CODE_EXECUTING);
      }
    } catch (final ModificationNotAllowedException ex) {
      LOG.trace("Contract creation error: attempt to mutate an immutable account");
      frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
      frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    }
  }

  @Override
  public void codeSuccess(final MessageFrame frame, final OperationTracer operationTracer) {
    final Bytes contractCode = frame.getOutputData();

    final long depositFee = gasCalculator.codeDepositGasCost(contractCode.size());

    if (frame.getRemainingGas() < depositFee) {
      LOG.trace(
          "Not enough gas to pay the code deposit fee for {}: "
              + "remaining gas = {} < {} = deposit fee",
          frame.getContractAddress(),
          frame.getRemainingGas(),
          depositFee);
      if (requireCodeDepositToSucceed) {
        LOG.trace("Contract creation error: insufficient funds for code deposit");
        frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(
            frame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
      } else {
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    } else {
      final var invalidReason =
          contractValidationRules.stream()
              .map(rule -> rule.validate(frame))
              .filter(Optional::isPresent)
              .findFirst();
      if (invalidReason.isEmpty()) {
        frame.decrementRemainingGas(depositFee);

        // Finalize contract creation, setting the contract code.
        final MutableAccount contract =
            frame.getWorldUpdater().getOrCreate(frame.getContractAddress()).getMutable();
        contract.setCode(contractCode);
        LOG.trace(
            "Successful creation of contract {} with code of size {} (Gas remaining: {})",
            frame.getContractAddress(),
            contractCode.size(),
            frame.getRemainingGas());
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      } else {
        final Optional<ExceptionalHaltReason> exceptionalHaltReason = invalidReason.get();
        frame.setExceptionalHaltReason(exceptionalHaltReason);
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(frame, exceptionalHaltReason);
      }
    }
  }
}
