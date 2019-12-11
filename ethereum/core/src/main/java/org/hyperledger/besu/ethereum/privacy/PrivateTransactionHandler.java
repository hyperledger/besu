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
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateTransactionHandler {

  private static final Logger LOG = LogManager.getLogger();
  private static final BytesValue GET_PARTICIPANTS_CALL_PAYLOAD = BytesValue.fromHexString("0x0b0235be");

  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;
  private final PrivateMarkerTransactionFactory privateMarkerTransactionFactory;
  private final PrivateNonceProvider privateNonceProvider;
  private final PrivateTransactionSimulator privateTransactionSimulator;

  public PrivateTransactionHandler(
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateTransactionSimulator privateTransactionSimulator) {
    this.enclave = enclave;
    this.privateTransactionValidator = privateTransactionValidator;
    this.privateMarkerTransactionFactory = privateMarkerTransactionFactory;
    this.privateNonceProvider = privateNonceProvider;
    this.privateTransactionSimulator = privateTransactionSimulator;
  }

  public String sendToOrion(final PrivateTransaction privateTransaction) {
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

  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return privateMarkerTransactionFactory.create(transactionEnclaveKey, privateTransaction);
  }

  public ValidationResult<TransactionValidator.TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyGroupId) {
    return privateTransactionValidator.validate(
        privateTransaction,
        privateNonceProvider.getNonce(
            privateTransaction.getSender(), BytesValues.fromBase64(privacyGroupId)));
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    final String payload = BytesValues.asBase64String(bvrlp.encoded());

    final List<String> privateFor = resolvePrivateFor(privateTransaction);

    // FIXME: orion should accept empty privateFor
    if (privateFor.isEmpty()) {
      privateFor.add(BytesValues.asBase64String(privateTransaction.getPrivateFrom()));
    }

    return new SendRequestLegacy(
        payload, BytesValues.asBase64String(privateTransaction.getPrivateFrom()), privateFor);
  }

  private List<String> resolvePrivateFor(final PrivateTransaction privateTransaction) {
    boolean isLegacyTransaction = privateTransaction.getPrivateFor().isPresent();
    if (isLegacyTransaction) {
      return privateTransaction.getPrivateFor().get().stream()
          .map(BytesValues::asBase64String)
          .collect(Collectors.toList());
    } else if (isGroupCreationTransaction(privateTransaction.getPayload())) {
      final List<String> newAndExistingParticipants =
          getParticipantsFromParameter(privateTransaction.getPayload());
      newAndExistingParticipants.addAll(getExistingParticipants(privateTransaction));
      return newAndExistingParticipants;
    } else {
      return getExistingParticipants(privateTransaction);
    }
  }

  private List<String> getExistingParticipants(final PrivateTransaction privateTransaction) {
    // get the privateFor list from the management contract
    final Optional<PrivateTransactionSimulatorResult> privateTransactionSimulatorResultOptional =
        privateTransactionSimulator.process(
            buildGetParticipantsCallParams(privateTransaction.getPrivateFrom()), privateTransaction.getPrivacyGroupId().get());

    if (privateTransactionSimulatorResultOptional.isPresent()
        && privateTransactionSimulatorResultOptional.get().isSuccessful()) {
      final RLPInput rlpInput = RLP.input(privateTransactionSimulatorResultOptional.get().getOutput());
      if (rlpInput.nextIsList()) {
        return rlpInput
                .readList(input -> BytesValues.asBase64String(input.readBytesValue()));
      } else {
        return Collections.emptyList();
      }

    } else {
      // if the management contract does not exist this will prompt
      // Orion to resolve the privateFor
      return Collections.emptyList();
    }
  }

  private List<String> getParticipantsFromParameter(final BytesValue input) {
    final BytesValue parameters = BytesValue.fromHexString(input.getHexString().substring(input.getHexString().indexOf("0032") + 4));
    final RLPInput rlpParameters = RLP.input(parameters);
    rlpParameters.enterList();
    // the first parameter is the requestors enclave key
    rlpParameters.skipNext();
    // the second parameter is the list of participants
    return rlpParameters.readList(i -> BytesValues.asBase64String(i.readBytesValue()));
  }

  private boolean isGroupCreationTransaction(final BytesValue input) {
    return input.toUnprefixedString().contains("f744b089");
  }

  private PrivacyCallParameter buildGetParticipantsCallParams(final BytesValue enclavePublicKey) {
    return new PrivacyCallParameter(
        Address.ZERO,
        Address.PRIVACY_PROXY,
        3000000,
        Wei.of(1000),
        Wei.ZERO,
        GET_PARTICIPANTS_CALL_PAYLOAD.concat(enclavePublicKey),
        enclavePublicKey);
  }
}
