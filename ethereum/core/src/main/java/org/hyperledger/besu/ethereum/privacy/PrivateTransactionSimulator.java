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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

public class PrivateTransactionSimulator {

  // Dummy signature for transactions to not fail being processed.
  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  // TODO: Identify a better default from account to use, such as the registered
  // coinbase or an account currently unlocked by the client.
  // see TransactionSimulator
  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private final Blockchain blockchain;
  private final WorldStateArchive publicWorldStateArchive;
  private final WorldStateArchive privateWorldStateArchive;
  private PrivateStateRootResolver privateStateRootResolver;
  private final ProtocolSchedule<?> protocolSchedule;

  public PrivateTransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive publicWorldStateArchive,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final ProtocolSchedule<?> protocolSchedule) {
    this.blockchain = blockchain;
    this.publicWorldStateArchive = publicWorldStateArchive;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateRootResolver = privateStateRootResolver;
    this.protocolSchedule = protocolSchedule;
  }

  public Optional<PrivateTransactionSimulatorResult> process(
      final CallParameter callParameter, final BytesValue privacyGroupId) {
    return process(callParameter, privacyGroupId, blockchain.getChainHeadHeader());
  }

  public Optional<PrivateTransactionSimulatorResult> process(
      final CallParameter callParameter,
      final BytesValue privacyGroupId,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    final MutableWorldState publicWorldState =
        publicWorldStateArchive.getMutable(header.getStateRoot()).orElse(null);
    final MutableWorldState privateWorldState =
        privateWorldStateArchive
            .getMutable(
                privateStateRootResolver.resolveLastStateRoot(privacyGroupId, header.getHash()))
            .orElse(null);
    if (publicWorldState == null || privateWorldState == null) {
      return Optional.empty();
    }

    final Address senderAddress =
        callParameter.getFrom() != null ? callParameter.getFrom() : DEFAULT_FROM;
    final Account sender = privateWorldState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;
    final long gasLimit =
        callParameter.getGasLimit() >= 0 ? callParameter.getGasLimit() : header.getGasLimit();
    final Wei gasPrice =
        callParameter.getGasPrice() != null ? callParameter.getGasPrice() : Wei.ZERO;
    final Wei value = callParameter.getValue() != null ? callParameter.getValue() : Wei.ZERO;
    final BytesValue payload =
        callParameter.getPayload() != null ? callParameter.getPayload() : BytesValue.EMPTY;

    final PrivateTransaction privateTransaction =
        PrivateTransaction.builder()
            .nonce(nonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .to(callParameter.getTo())
            .sender(senderAddress)
            .value(value)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .build();

    final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
    final PrivateTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockNumber(header.getNumber()).getPrivateTransactionProcessor();

    final PrivateTransactionProcessor.Result result =
        transactionProcessor.processTransaction(
            blockchain,
            publicWorldState.updater(),
            privateWorldState.updater(),
            header,
            privateTransaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            OperationTracer.NO_TRACING,
            new BlockHashLookup(header, blockchain),
            privacyGroupId);

    return Optional.of(
        new PrivateTransactionSimulatorResult(
            privateTransaction, result, privateWorldState.rootHash()));
  }
}
