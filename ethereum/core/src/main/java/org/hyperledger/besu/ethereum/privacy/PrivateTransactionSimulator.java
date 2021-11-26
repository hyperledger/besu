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

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.Restriction;

import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/*
 * Used to process transactions for priv_call.
 *
 * The processing won't affect the private world state, it is used to execute read operations on the
 * blockchain.
 */
public class PrivateTransactionSimulator {

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

  private static final Address DEFAULT_FROM = Address.ZERO;

  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final PrivacyParameters privacyParameters;
  private final PrivateStateRootResolver privateStateRootResolver;

  public PrivateTransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final PrivacyParameters privacyParameters) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.privacyParameters = privacyParameters;
    this.privateStateRootResolver = privacyParameters.getPrivateStateRootResolver();
  }

  public Optional<TransactionProcessingResult> process(
      final String privacyGroupId, final CallParameter callParams) {
    final BlockHeader header = blockchain.getChainHeadHeader();
    return process(privacyGroupId, callParams, header);
  }

  public Optional<TransactionProcessingResult> process(
      final String privacyGroupId, final CallParameter callParams, final Hash blockHeaderHash) {
    final BlockHeader header = blockchain.getBlockHeader(blockHeaderHash).orElse(null);
    return process(privacyGroupId, callParams, header);
  }

  public Optional<TransactionProcessingResult> process(
      final String privacyGroupId, final CallParameter callParams, final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).orElse(null);
    return process(privacyGroupId, callParams, header);
  }

  private Optional<TransactionProcessingResult> process(
      final String privacyGroupIdString, final CallParameter callParams, final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }

    final MutableWorldState publicWorldState =
        worldStateArchive.getMutable(header.getStateRoot(), header.getHash()).orElse(null);
    if (publicWorldState == null) {
      return Optional.empty();
    }

    // get the last world state root hash or create a new one
    final Bytes32 privacyGroupId = Bytes32.wrap(Bytes.fromBase64String(privacyGroupIdString));
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, header.getHash());

    final MutableWorldState disposablePrivateState =
        privacyParameters
            .getPrivateWorldStateArchive()
            .getMutable(lastRootHash, header.getHash())
            .get();

    final PrivateTransaction transaction =
        getPrivateTransaction(callParams, header, privacyGroupId, disposablePrivateState);

    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());

    final PrivateTransactionProcessor privateTransactionProcessor =
        protocolSpec.getPrivateTransactionProcessor();

    final TransactionProcessingResult result =
        privateTransactionProcessor.processTransaction(
            publicWorldState.updater(),
            disposablePrivateState.updater(),
            header,
            Hash.ZERO, // Corresponding PMT hash not needed as this private transaction doesn't
            // exist
            transaction,
            protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            OperationTracer.NO_TRACING,
            new BlockHashLookup(header, blockchain),
            privacyGroupId);

    return Optional.of(result);
  }

  private PrivateTransaction getPrivateTransaction(
      final CallParameter callParams,
      final BlockHeader header,
      final Bytes privacyGroupId,
      final MutableWorldState disposablePrivateState) {
    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;
    final Account sender = disposablePrivateState.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;
    final long gasLimit =
        callParams.getGasLimit() >= 0 ? callParams.getGasLimit() : header.getGasLimit();
    final Wei gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final Bytes payload = callParams.getPayload() != null ? callParams.getPayload() : Bytes.EMPTY;

    return PrivateTransaction.builder()
        .privateFrom(Bytes.EMPTY)
        .privacyGroupId(privacyGroupId)
        .restriction(Restriction.RESTRICTED)
        .nonce(nonce)
        .gasPrice(gasPrice)
        .gasLimit(gasLimit)
        .to(callParams.getTo())
        .sender(senderAddress)
        .value(value)
        .payload(payload)
        .signature(FAKE_SIGNATURE)
        .build();
  }
}
