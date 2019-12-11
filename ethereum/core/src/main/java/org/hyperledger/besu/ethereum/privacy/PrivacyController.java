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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.CreatePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.DeletePrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.FindPrivacyGroupRequest;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.PrivacyGroup.Type;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendRequestBesu;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

public class PrivacyController {

  private static final Logger LOG = LogManager.getLogger();

  private final Enclave enclave;
  private final String enclavePublicKey;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateTransactionValidator privateTransactionValidator;
  private final PrivateMarkerTransactionFactory privateMarkerTransactionFactory;

  public PrivacyController(
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {
    this(
        privacyParameters.getEnclave(),
        privacyParameters.getEnclavePublicKey(),
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getPrivateWorldStateArchive(),
        new PrivateTransactionValidator(chainId),
        privateMarkerTransactionFactory);
  }

  public PrivacyController(
      final Enclave enclave,
      final String enclavePublicKey,
      final PrivateStateStorage privateStateStorage,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {
    this.enclave = enclave;
    this.enclavePublicKey = enclavePublicKey;
    this.privateStateStorage = privateStateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateTransactionValidator = privateTransactionValidator;
    this.privateMarkerTransactionFactory = privateMarkerTransactionFactory;
  }

  public PrivacyTransactionResponse sendTransaction(final PrivateTransaction privateTransaction) {
    final SendRequest sendRequest = createSendRequest(privateTransaction);
    final SendResponse sendResponse;

    try {
      LOG.trace("Storing private transaction in enclave");
      sendResponse = enclave.send(sendRequest);
      final String enclaveKey = sendResponse.getKey();
      final String privacyGroup = getPrivacyGroup(enclaveKey, privateTransaction);
      return new PrivacyTransactionResponse(enclaveKey, privacyGroup);
    } catch (Exception e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }
  }

  public ReceiveResponse retrieveTransaction(final String enclaveKey) {
    final ReceiveRequest receiveRequest = new ReceiveRequest(enclaveKey, enclavePublicKey);
    return enclave.receive(receiveRequest);
  }

  public PrivacyGroup createPrivacyGroup(
      final String[] addresses, final String name, final String description) {
    final CreatePrivacyGroupRequest createPrivacyGroupRequest =
        new CreatePrivacyGroupRequest(addresses, enclavePublicKey, name, description);
    return enclave.createPrivacyGroup(createPrivacyGroupRequest);
  }

  public String deletePrivacyGroup(final String privacyGroupId) {
    DeletePrivacyGroupRequest deletePrivacyGroupRequest =
        new DeletePrivacyGroupRequest(privacyGroupId, enclavePublicKey);
    return enclave.deletePrivacyGroup(deletePrivacyGroupRequest);
  }

  public PrivacyGroup[] findPrivacyGroup(final String[] addresses) {
    return enclave.findPrivacyGroup(new FindPrivacyGroupRequest(addresses));
  }

  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return privateMarkerTransactionFactory.create(transactionEnclaveKey, privateTransaction);
  }

  public ValidationResult<TransactionValidator.TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyGroupId) {
    return privateTransactionValidator.validate(
        privateTransaction, determineNonce(privateTransaction.getSender(), privacyGroupId));
  }

  public long determineNonce(
      final String privateFrom, final String[] privateFor, final Address address) {
    final String[] groupMembers = Arrays.append(privateFor, privateFrom);

    final FindPrivacyGroupRequest request = new FindPrivacyGroupRequest(groupMembers);
    final List<PrivacyGroup> matchingGroups = Lists.newArrayList(enclave.findPrivacyGroup(request));

    final List<PrivacyGroup> legacyGroups =
        matchingGroups.stream()
            .filter(group -> group.getType() == Type.LEGACY)
            .collect(Collectors.toList());

    if (legacyGroups.size() == 0) {
      // the legacy group does not exist yet
      return 0;
    }

    if (legacyGroups.size() != 1) {
      throw new RuntimeException(
          String.format(
              "Found invalid number of privacy groups (%d), expected 1.", legacyGroups.size()));
    }

    final String privacyGroupId = legacyGroups.get(0).getPrivacyGroupId();

    return determineNonce(address, privacyGroupId);
  }

  public long determineNonce(final Address sender, final String privacyGroupId) {
    return privateStateStorage
        .getLatestStateRoot(BytesValues.fromBase64(privacyGroupId))
        .map(
            lastRootHash ->
                privateWorldStateArchive
                    .getMutable(lastRootHash)
                    .map(
                        worldState -> {
                          final Account maybePrivateSender = worldState.get(sender);

                          if (maybePrivateSender != null) {
                            return maybePrivateSender.getNonce();
                          }
                          // account has not interacted in this private state
                          return Account.DEFAULT_NONCE;
                        })
                    // private state does not exist
                    .orElse(Account.DEFAULT_NONCE))
        .orElse(
            // private state does not exist
            Account.DEFAULT_NONCE);
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    final String payload = BytesValues.asBase64String(bvrlp.encoded());

    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return new SendRequestBesu(
          payload,
          enclavePublicKey,
          BytesValues.asBase64String(privateTransaction.getPrivacyGroupId().get()));
    } else {
      final List<String> privateFor =
          privateTransaction.getPrivateFor().get().stream()
              .map(BytesValues::asBase64String)
              .collect(Collectors.toList());

      // FIXME: orion should accept empty privateFor
      if (privateFor.isEmpty()) {
        privateFor.add(BytesValues.asBase64String(privateTransaction.getPrivateFrom()));
      }

      return new SendRequestLegacy(
          payload, BytesValues.asBase64String(privateTransaction.getPrivateFrom()), privateFor);
    }
  }

  private String getPrivacyGroup(final String key, final PrivateTransaction privateTransaction) {
    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return BytesValues.asBase64String(privateTransaction.getPrivacyGroupId().get());
    }
    final ReceiveRequest receiveRequest =
        new ReceiveRequest(key, BytesValues.asBase64String(privateTransaction.getPrivateFrom()));
    LOG.debug(
        "Getting privacy group for {}",
        BytesValues.asBase64String(privateTransaction.getPrivateFrom()));
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(receiveRequest);
      return receiveResponse.getPrivacyGroupId();
    } catch (final RuntimeException e) {
      LOG.error("Failed to retrieve private transaction in enclave", e);
      throw e;
    }
  }
}
