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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.CAN_EXECUTE_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.GET_VERSION_METHOD_SIGNATURE;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Restriction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/*
 This class is an abstraction on top of the privacy group management smart contract.

 It is possible to use it in two different ways that carry different
 lifetime expectations and call semantics:

 1. When constructed using `FlexiblePrivacyGroupContract(PrivateTransactionSimulator)`
    the object is expected to be long-lived. Methods can be supplied
    with block height or block hash parameters to select which block's
    state is queried.

 2. When using the alternative constructor, no height or hash
    parameter must be supplied to subsequent method calls. All methods
    operate on the state specified by the given MessageFrame. Only
    when constructed this way, the class can be used to query state
    that is not on a block boundary. Used this way, the object's lifetime is intended to be short.
*/
public class FlexiblePrivacyGroupContract {
  @FunctionalInterface
  public interface TransactionSimulator {
    Optional<TransactionProcessingResult> simulate(
        final String privacyGroupId,
        final Bytes callData,
        final Optional<Hash> blockHash,
        final Optional<Long> blockNumber);
  }

  final TransactionSimulator transactionSimulator;

  public FlexiblePrivacyGroupContract(
      final PrivateTransactionSimulator privateTransactionSimulator) {
    transactionSimulator =
        (privacyGroupId, callData, blockHash, blockNumber) -> {
          final CallParameter callParameter = buildCallParams(callData);
          if (blockHash.isPresent()) {
            return privateTransactionSimulator.process(
                privacyGroupId, callParameter, blockHash.get());
          } else if (blockNumber.isPresent()) {
            return privateTransactionSimulator.process(
                privacyGroupId, callParameter, blockNumber.get());
          } else {
            return privateTransactionSimulator.process(privacyGroupId, callParameter);
          }
        };
  }

  public FlexiblePrivacyGroupContract(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateTransactionProcessor privateTransactionProcessor) {
    transactionSimulator =
        (base64privacyGroupId, callData, blockHash, blockNumber) -> {
          assert !blockHash.isPresent();
          assert !blockNumber.isPresent();

          final Bytes privacyGroupId = Bytes.fromBase64String(base64privacyGroupId);
          final MutableWorldState localMutableState =
              privateWorldStateArchive.getMutable(disposablePrivateState.rootHash(), null).get();
          final WorldUpdater updater = localMutableState.updater();
          final PrivateTransaction privateTransaction =
              buildTransaction(privacyGroupId, privateWorldStateUpdater, callData);

          return Optional.of(
              privateTransactionProcessor.processTransaction(
                  messageFrame.getWorldUpdater(),
                  updater,
                  currentBlockHeader,
                  messageFrame.getContextVariable(PrivateStateUtils.KEY_TRANSACTION_HASH),
                  privateTransaction,
                  messageFrame.getMiningBeneficiary(),
                  OperationTracer.NO_TRACING,
                  messageFrame.getBlockHashLookup(),
                  privacyGroupId));
        };
  }

  public Optional<PrivacyGroup> getPrivacyGroupById(final String privacyGroupId) {
    return getPrivacyGroup(privacyGroupId, Optional.empty(), Optional.empty());
  }

  public Optional<PrivacyGroup> getPrivacyGroupByIdAndBlockNumber(
      final String privacyGroupId, final Optional<Long> blockNumber) {
    return getPrivacyGroup(privacyGroupId, Optional.empty(), blockNumber);
  }

  public Optional<PrivacyGroup> getPrivacyGroupByIdAndBlockHash(
      final String privacyGroupId, final Optional<Hash> blockHash) {
    return getPrivacyGroup(privacyGroupId, blockHash, Optional.empty());
  }

  private Optional<PrivacyGroup> getPrivacyGroup(
      final String privacyGroupId,
      final Optional<Hash> blockHash,
      final Optional<Long> blockNumber) {

    final Optional<TransactionProcessingResult> result =
        transactionSimulator.simulate(
            privacyGroupId, GET_PARTICIPANTS_METHOD_SIGNATURE, blockHash, blockNumber);
    return readPrivacyGroupFromResult(privacyGroupId, result);
  }

  private Optional<PrivacyGroup> readPrivacyGroupFromResult(
      final String privacyGroupId, final Optional<TransactionProcessingResult> result) {
    if (result.isEmpty()) {
      return Optional.empty();
    }

    if (!result.get().isSuccessful()) {
      return Optional.empty();
    }

    final RLPInput rlpInput = RLP.input(result.get().getOutput());
    if (rlpInput.nextSize() > 0) {
      final PrivacyGroup privacyGroup =
          new PrivacyGroup(
              privacyGroupId, PrivacyGroup.Type.FLEXIBLE, "", "", decodeList(rlpInput.raw()));
      return Optional.of(privacyGroup);
    } else {
      return Optional.empty();
    }
  }

  public Optional<Bytes32> getVersion(final String privacyGroupId, final Optional<Hash> blockHash) {
    final Optional<TransactionProcessingResult> result =
        transactionSimulator.simulate(
            privacyGroupId, GET_VERSION_METHOD_SIGNATURE, blockHash, Optional.empty());
    return result.map(TransactionProcessingResult::getOutput).map(Bytes32::wrap);
  }

  public Optional<Bytes32> getCanExecute(
      final String privacyGroupId, final Optional<Hash> blockHash) {
    final Optional<TransactionProcessingResult> result =
        transactionSimulator.simulate(
            privacyGroupId, CAN_EXECUTE_METHOD_SIGNATURE, blockHash, Optional.empty());
    return result.map(TransactionProcessingResult::getOutput).map(Bytes32::wrap);
  }

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

  private PrivateTransaction buildTransaction(
      final Bytes privacyGroupId,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes payload) {
    return PrivateTransaction.builder()
        .privateFrom(Bytes.EMPTY)
        .privacyGroupId(privacyGroupId)
        .restriction(Restriction.RESTRICTED)
        .nonce(
            privateWorldStateUpdater.getAccount(Address.ZERO) != null
                ? privateWorldStateUpdater.getAccount(Address.ZERO).getNonce()
                : 0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(FLEXIBLE_PRIVACY_PROXY)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .payload(payload)
        .signature(FAKE_SIGNATURE)
        .build();
  }

  private CallParameter buildCallParams(final Bytes methodCall) {
    return new CallParameter(
        Address.ZERO, FLEXIBLE_PRIVACY_PROXY, 3000000, Wei.of(1000), Wei.ZERO, methodCall);
  }

  public static List<String> decodeList(final Bytes rlpEncodedList) {
    final ArrayList<String> decodedElements = new ArrayList<>();
    // first 32 bytes is dynamic list offset
    if (rlpEncodedList.size() < 64) return decodedElements;
    // Bytes uses a byte[] for the content which can only have up to Integer.MAX_VALUE-5 elements
    final int lengthOfList =
        UInt256.fromBytes(rlpEncodedList.slice(32, 32)).toInt(); // length of list
    if (rlpEncodedList.size() < 64 + lengthOfList * 32) return decodedElements;

    for (int i = 0; i < lengthOfList; ++i) {
      decodedElements.add(
          Bytes.wrap(rlpEncodedList.slice(64 + (32 * i), 32)).toBase64String()); // participant
    }
    return decodedElements;
  }
}
