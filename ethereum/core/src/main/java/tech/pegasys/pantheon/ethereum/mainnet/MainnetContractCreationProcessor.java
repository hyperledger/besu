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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A contract creation message processor. */
public class MainnetContractCreationProcessor extends AbstractMessageProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean requireCodeDepositToSucceed;

  private final GasCalculator gasCalculator;

  private final long initialContractNonce;

  private final int codeSizeLimit;

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final int codeSizeLimit,
      final long initialContractNonce,
      final Collection<Address> forceCommitAddresses) {
    super(evm, forceCommitAddresses);
    this.gasCalculator = gasCalculator;
    this.requireCodeDepositToSucceed = requireCodeDepositToSucceed;
    this.codeSizeLimit = codeSizeLimit;
    this.initialContractNonce = initialContractNonce;
  }

  public MainnetContractCreationProcessor(
      final GasCalculator gasCalculator,
      final EVM evm,
      final boolean requireCodeDepositToSucceed,
      final int codeSizeLimit,
      final long initialContractNonce) {
    this(
        gasCalculator,
        evm,
        requireCodeDepositToSucceed,
        codeSizeLimit,
        initialContractNonce,
        ImmutableSet.of());
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
      if (contractCode.size() > codeSizeLimit) {
        LOG.trace(
            "Contract creation error: code size {} exceeds code size limit {}",
            contractCode.size(),
            codeSizeLimit);
        frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      } else {
        frame.decrementRemainingGas(depositFee);

        // Finalize contract creation, setting the contract code.
        final MutableAccount contract =
            frame.getWorldState().getOrCreate(frame.getContractAddress());
        contract.setCode(contractCode);
        LOG.trace(
            "Successful creation of contract {} with code of size {} (Gas remaining: {})",
            frame.getContractAddress(),
            contractCode.size(),
            frame.getRemainingGas());
        frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
      }
    }
  }
}
