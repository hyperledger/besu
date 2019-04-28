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
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendResponse;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateTransactionHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Enclave enclave;
  private final Address privacyPrecompileAddress;
  private final SECP256K1.KeyPair nodeKeyPair;

  public PrivateTransactionHandler(final PrivacyParameters privacyParameters) {
    this(
        new Enclave(privacyParameters.getEnclaveUri()),
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress()),
        privacyParameters.getSigningKeyPair());
  }

  public PrivateTransactionHandler(
      final Enclave enclave,
      final Address privacyPrecompileAddress,
      final SECP256K1.KeyPair nodeKeyPair) {
    this.enclave = enclave;
    this.privacyPrecompileAddress = privacyPrecompileAddress;
    this.nodeKeyPair = nodeKeyPair;
  }

  public Transaction handle(
      final PrivateTransaction privateTransaction, final Supplier<Long> nonceSupplier)
      throws IOException {
    LOG.trace("Handling private transaction {}", privateTransaction.toString());
    final SendRequest sendRequest = createSendRequest(privateTransaction);
    final SendResponse sendResponse;
    try {
      LOG.trace("Storing private transaction in enclave");
      sendResponse = enclave.send(sendRequest);
    } catch (IOException e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }

    return createPrivacyMarkerTransactionWithNonce(
        sendResponse.getKey(), privateTransaction, nonceSupplier.get());
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final List<String> privateFor =
        privateTransaction.getPrivateFor().stream()
            .map(BytesValues::asString)
            .collect(Collectors.toList());

    // FIXME: Orion should concatenate to and from - not it pantheon
    if (privateFor.isEmpty()) {
      privateFor.add(BytesValues.asString(privateTransaction.getPrivateFrom()));
    }

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);

    return new SendRequest(
        Base64.getEncoder().encodeToString(bvrlp.encoded().extractArray()),
        BytesValues.asString(privateTransaction.getPrivateFrom()),
        privateFor);
  }

  private Transaction createPrivacyMarkerTransactionWithNonce(
      final String transactionEnclaveKey,
      final PrivateTransaction privateTransaction,
      final Long nonce) {

    return Transaction.builder()
        .nonce(nonce)
        .gasPrice(privateTransaction.getGasPrice())
        .gasLimit(privateTransaction.getGasLimit())
        .to(privacyPrecompileAddress)
        .value(privateTransaction.getValue())
        .payload(BytesValue.wrap(transactionEnclaveKey.getBytes(Charsets.UTF_8)))
        .sender(privateTransaction.getSender())
        .signAndBuild(nodeKeyPair);
  }
}
