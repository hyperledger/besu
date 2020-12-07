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
package org.hyperledger.besu.ethereum.transaction;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/*
 * Used to process transactions for eth_call and eth_estimateGas.
 *
 * The processing won't affect the world state, it is used to execute read operations on the
 * blockchain or to estimate the transaction gas cost.
 */
public class TransactionSimulator {

  // Dummy signature for transactions to not fail being processed.
  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  // TODO: Identify a better default from account to use, such as the registered
  // coinbase or an account currently unlocked by the client.
  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;

  public TransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).orElse(null);
    return process(callParams, transactionValidationParams, operationTracer, header);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams, final Hash blockHeaderHash) {
    final BlockHeader header = blockchain.getBlockHeader(blockHeaderHash).orElse(null);
    return process(
        callParams,
        TransactionValidationParams.transactionSimulator(),
        OperationTracer.NO_TRACING,
        header);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams, final long blockNumber) {
    return process(
        callParams,
        TransactionValidationParams.transactionSimulator(),
        OperationTracer.NO_TRACING,
        blockNumber);
  }

  public Optional<TransactionSimulatorResult> processAtHead(final CallParameter callParams) {
    return process(
        callParams,
        TransactionValidationParams.transactionSimulator(),
        OperationTracer.NO_TRACING,
        blockchain.getChainHeadHeader());
  }

  private Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    final MutableWorldState worldState =
        worldStateArchive.getMutable(header.getStateRoot()).orElse(null);
    if (worldState == null) {
      return Optional.empty();
    }

    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;
    final Account sender = worldState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;
    final long gasLimit =
        callParams.getGasLimit() >= 0 ? callParams.getGasLimit() : header.getGasLimit();
    final Wei gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final Bytes payload = callParams.getPayload() != null ? callParams.getPayload() : Bytes.EMPTY;

    final WorldUpdater updater = worldState.updater();

    if (transactionValidationParams.isAllowExceedingBalance()) {
      updater.getOrCreate(senderAddress).getMutable().incrementBalance(Wei.of(Long.MAX_VALUE));
    }

    final Transaction.Builder transactionBuilder =
        Transaction.builder()
            .nonce(nonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .to(callParams.getTo())
            .sender(senderAddress)
            .value(value)
            .payload(payload)
            .signature(FAKE_SIGNATURE);
    callParams.getGasPremium().ifPresent(transactionBuilder::gasPremium);
    callParams.getFeeCap().ifPresent(transactionBuilder::feeCap);

    final Transaction transaction = transactionBuilder.build();

    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());

    final MainnetTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockNumber(header.getNumber()).getTransactionProcessor();
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            blockchain,
            updater,
            header,
            transaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            new BlockHashLookup(header, blockchain),
            false,
            transactionValidationParams,
            operationTracer);

    return Optional.of(new TransactionSimulatorResult(transaction, result));
  }

  public Optional<Boolean> doesAddressExistAtHead(final Address address) {
    return doesAddressExist(address, blockchain.getChainHeadHeader());
  }

  public Optional<Boolean> doesAddressExist(final Address address, final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }

    final MutableWorldState worldState =
        worldStateArchive.getMutable(header.getStateRoot()).orElse(null);
    if (worldState == null) {
      return Optional.empty();
    }

    return Optional.of(worldState.get(address) != null);
  }
}
