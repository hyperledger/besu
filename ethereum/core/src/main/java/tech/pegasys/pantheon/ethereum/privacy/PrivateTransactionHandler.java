/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.privacy;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendRequestLegacy;
import tech.pegasys.pantheon.enclave.types.SendRequestPantheon;
import tech.pegasys.pantheon.enclave.types.SendResponse;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateTransactionHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Enclave enclave;
  private final Address privacyPrecompileAddress;
  private final SECP256K1.KeyPair nodeKeyPair;
  private final Address signerAddress;
  private final String enclavePublicKey;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateTransactionValidator privateTransactionValidator;

  public PrivateTransactionHandler(final PrivacyParameters privacyParameters) {
    this(
        new Enclave(privacyParameters.getEnclaveUri()),
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress()),
        privacyParameters.getSigningKeyPair(),
        privacyParameters.getEnclavePublicKey(),
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getPrivateWorldStateArchive(),
        new PrivateTransactionValidator());
  }

  public PrivateTransactionHandler(
      final Enclave enclave,
      final Address privacyPrecompileAddress,
      final SECP256K1.KeyPair nodeKeyPair,
      final String enclavePublicKey,
      final PrivateStateStorage privateStateStorage,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateTransactionValidator privateTransactionValidator) {
    this.enclave = enclave;
    this.privacyPrecompileAddress = privacyPrecompileAddress;
    this.nodeKeyPair = nodeKeyPair;
    this.signerAddress = Util.publicKeyToAddress(nodeKeyPair.getPublicKey());
    this.enclavePublicKey = enclavePublicKey;
    this.privateStateStorage = privateStateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateTransactionValidator = privateTransactionValidator;
  }

  public String sendToOrion(final PrivateTransaction privateTransaction) throws Exception {
    final SendRequest sendRequest = createSendRequest(privateTransaction);
    final SendResponse sendResponse;

    try {
      LOG.trace("Storing private transaction in enclave");
      sendResponse = enclave.send(sendRequest);
      return sendResponse.getKey();
    } catch (Exception e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }
  }

  public String getPrivacyGroup(final String key, final PrivateTransaction privateTransaction)
      throws Exception {
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
    } catch (Exception e) {
      LOG.error("Failed to retrieve private transaction in enclave", e);
      throw e;
    }
  }

  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey,
      final PrivateTransaction privateTransaction,
      final Long nonce) {

    return Transaction.builder()
        .nonce(nonce)
        .gasPrice(privateTransaction.getGasPrice())
        .gasLimit(privateTransaction.getGasLimit())
        .to(privacyPrecompileAddress)
        .value(privateTransaction.getValue())
        .payload(BytesValues.fromBase64(transactionEnclaveKey))
        .sender(signerAddress)
        .signAndBuild(nodeKeyPair);
  }

  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyGroupId) {
    return privateTransactionValidator.validate(
        privateTransaction, getSenderNonce(privateTransaction.getSender(), privacyGroupId));
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    final String payload = BytesValues.asBase64String(bvrlp.encoded());

    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return new SendRequestPantheon(
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

  public long getSenderNonce(final Address sender, final String privacyGroupId) {
    return privateStateStorage
        .getPrivateAccountState(BytesValues.fromBase64(privacyGroupId))
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

  public Address getSignerAddress() {
    return signerAddress;
  }

  public String getEnclaveKey() {
    return enclavePublicKey;
  }
}
