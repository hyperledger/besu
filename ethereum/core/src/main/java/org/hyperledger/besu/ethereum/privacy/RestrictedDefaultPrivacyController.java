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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.ONCHAIN_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.OnchainGroupManagement.GET_VERSION_METHOD_SIGNATURE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class RestrictedDefaultPrivacyController extends AbstractPrivacyController {

  private static final Logger LOG = LogManager.getLogger();

  final Enclave enclave;
  final PrivateTransactionLocator privateTransactionLocator;

  public RestrictedDefaultPrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader) {
    this(
        blockchain,
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getEnclave(),
        new PrivateTransactionValidator(chainId),
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privacyParameters.getPrivateStateRootResolver());
  }

  public RestrictedDefaultPrivacyController(
      final Blockchain blockchain,
      final PrivateStateStorage privateStateStorage,
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader,
      final PrivateStateRootResolver privateStateRootResolver) {
    super(
        blockchain,
        privateStateStorage,
        privateTransactionValidator,
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privateStateRootResolver);
    this.enclave = enclave;
    this.privateTransactionLocator =
        new PrivateTransactionLocator(blockchain, enclave, privateStateStorage);
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {
    return privateTransactionLocator.findByPmtHash(pmtHash, enclaveKey);
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    try {
      LOG.trace("Storing private transaction in enclave");
      final SendResponse sendResponse =
          sendRequest(privateTransaction, privacyUserId, maybePrivacyGroup);
      return sendResponse.getKey();
    } catch (final Exception e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }
  }

  ReceiveResponse retrieveTransaction(final String enclaveKey, final String privacyUserId) {
    return enclave.receive(enclaveKey, privacyUserId);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    return enclave.createPrivacyGroup(addresses, privacyUserId, name, description);
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    return enclave.deletePrivacyGroup(privacyGroupId, privacyUserId);
  }

  @Override
  public PrivacyGroup[] findPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    return enclave.findPrivacyGroup(addresses);
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    return Optional.ofNullable(enclave.retrievePrivacyGroup(privacyGroupId));
  }

  CallParameter buildCallParams(final Bytes methodCall) {
    return new CallParameter(
        Address.ZERO, ONCHAIN_PRIVACY_PROXY, 3000000, Wei.of(1000), Wei.ZERO, methodCall);
  }

  private SendResponse sendRequest(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();

    if (maybePrivacyGroup.isPresent()) {
      final PrivacyGroup privacyGroup = maybePrivacyGroup.get();
      if (privacyGroup.getType() == PrivacyGroup.Type.ONCHAIN) {
        // onchain privacy group
        final Optional<TransactionProcessingResult> version =
            privateTransactionSimulator.process(
                privateTransaction.getPrivacyGroupId().get().toBase64String(),
                buildCallParams(GET_VERSION_METHOD_SIGNATURE));
        new VersionedPrivateTransaction(privateTransaction, version).writeTo(rlpOutput);
        final List<String> onchainPrivateFor = privacyGroup.getMembers();
        return enclave.send(
            rlpOutput.encoded().toBase64String(),
            privateTransaction.getPrivateFrom().toBase64String(),
            onchainPrivateFor);
      } else if (privacyGroup.getType() == PrivacyGroup.Type.PANTHEON) {
        // offchain privacy group
        privateTransaction.writeTo(rlpOutput);
        return enclave.send(
            rlpOutput.encoded().toBase64String(),
            privacyUserId,
            privateTransaction.getPrivacyGroupId().get().toBase64String());
      } else {
        // this should not happen
        throw new RuntimeException();
      }
    }
    // legacy transaction
    final List<String> privateFor = resolveLegacyPrivateFor(privateTransaction);
    if (privateFor.isEmpty()) {
      privateFor.add(privateTransaction.getPrivateFrom().toBase64String());
    }
    privateTransaction.writeTo(rlpOutput);
    final String payload = rlpOutput.encoded().toBase64String();

    return enclave.send(payload, privateTransaction.getPrivateFrom().toBase64String(), privateFor);
  }

  private List<String> resolveLegacyPrivateFor(final PrivateTransaction privateTransaction) {
    final ArrayList<String> privateFor = new ArrayList<>();
    final boolean isLegacyTransaction = privateTransaction.getPrivateFor().isPresent();
    if (isLegacyTransaction) {
      privateFor.addAll(
          privateTransaction.getPrivateFor().get().stream()
              .map(Bytes::toBase64String)
              .collect(Collectors.toList()));
    }
    return privateFor;
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId) {
    final PrivacyGroup offchainPrivacyGroup = enclave.retrievePrivacyGroup(privacyGroupId);
    if (!offchainPrivacyGroup.getMembers().contains(privacyUserId)) {
      throw new RuntimeException("Privacy group must contain the enclave public key");
    }
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId);
  }
}
