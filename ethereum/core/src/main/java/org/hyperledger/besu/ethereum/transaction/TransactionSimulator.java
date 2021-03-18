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

import static org.hyperledger.besu.ethereum.goquorum.GoQuorumPrivateStateUtil.getPrivateWorldStateAtBlock;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
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
import org.hyperledger.besu.ethereum.worldstate.GoQuorumMutablePrivateAndPublicWorldStateUpdater;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/*
 * Used to process transactions for eth_call and eth_estimateGas.
 *
 * The processing won't affect the world state, it is used to execute read operations on the
 * blockchain or to estimate the transaction gas cost.
 */
public class TransactionSimulator {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  // Dummy signature for transactions to not fail being processed.
  private static final SECPSignature FAKE_SIGNATURE =
      SIGNATURE_ALGORITHM
          .get()
          .createSignature(
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              (byte) 0);

  // TODO: Identify a better default from account to use, such as the registered
  // coinbase or an account currently unlocked by the client.
  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final Optional<PrivacyParameters> maybePrivacyParameters;

  public TransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.maybePrivacyParameters = Optional.empty();
  }

  public TransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final PrivacyParameters privacyParameters) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.maybePrivacyParameters = Optional.of(privacyParameters);
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

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    final MutableWorldState publicWorldState =
        worldStateArchive.getMutable(header.getStateRoot(), header.getHash(), false).orElse(null);

    if (publicWorldState == null) {
      return Optional.empty();
    }
    final WorldUpdater updater = getEffectiveWorldStateUpdater(header, publicWorldState);

    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;
    final Account sender = publicWorldState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;
    final long gasLimit =
        callParams.getGasLimit() >= 0 ? callParams.getGasLimit() : header.getGasLimit();
    final Wei gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final Bytes payload = callParams.getPayload() != null ? callParams.getPayload() : Bytes.EMPTY;

    if (transactionValidationParams.isAllowExceedingBalance()) {
      updater.getOrCreate(senderAddress).getMutable().setBalance(Wei.of(UInt256.MAX_VALUE));
    }

    final Transaction.Builder transactionBuilder =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
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

  // return combined private/public world state updater if GoQuorum mode, otherwise the public state
  private WorldUpdater getEffectiveWorldStateUpdater(
      final BlockHeader header, final MutableWorldState publicWorldState) {

    if (maybePrivacyParameters.isPresent()
        && maybePrivacyParameters.get().getGoQuorumPrivacyParameters().isPresent()) {

      final MutableWorldState privateWorldState =
          getPrivateWorldStateAtBlock(
              maybePrivacyParameters.get().getGoQuorumPrivacyParameters(), header);
      return new GoQuorumMutablePrivateAndPublicWorldStateUpdater(
          publicWorldState.updater(), privateWorldState.updater());
    }
    return publicWorldState.updater();
  }

  public Optional<Boolean> doesAddressExistAtHead(final Address address) {
    final BlockHeader header = blockchain.getChainHeadHeader();
    final MutableWorldState worldState =
        worldStateArchive.getMutable(header.getStateRoot(), header.getHash(), false).orElse(null);

    return doesAddressExist(worldState, address, header);
  }

  public Optional<Boolean> doesAddressExist(
      final MutableWorldState worldState, final Address address, final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    if (worldState == null) {
      return Optional.empty();
    }

    return Optional.of(worldState.get(address) != null);
  }
}
