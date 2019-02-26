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
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PrivateTransactionHandler {

  private final Enclave enclave;
  private final Address privacyPrecompileAddress;

  public PrivateTransactionHandler(final PrivacyParameters privacyParameters) {
    this(
        new Enclave(privacyParameters.getUrl()),
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress()));
  }

  public PrivateTransactionHandler(final Enclave enclave, final Address privacyPrecompileAddress) {
    this.enclave = enclave;
    this.privacyPrecompileAddress = privacyPrecompileAddress;
  }

  public Transaction handle(final PrivateTransaction privateTransaction) throws IOException {
    final SendRequest sendRequest = createSendRequest(privateTransaction);
    final SendResponse sendResponse;
    try {
      sendResponse = enclave.send(sendRequest);
    } catch (IOException e) {
      throw e;
    }

    return createPrivacyMarkerTransaction(sendResponse.getKey(), privateTransaction);
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final List<String> privateFor =
        privateTransaction.getPrivateFor().stream()
            .map(BytesValues::asString)
            .collect(Collectors.toList());

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);

    return new SendRequest(
        Base64.getEncoder().encodeToString(bvrlp.encoded().extractArray()),
        BytesValues.asString(privateTransaction.getPrivateFrom()),
        privateFor);
  }

  private Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {

    return new Transaction(
        privateTransaction.getNonce(),
        privateTransaction.getGasPrice(),
        privateTransaction.getGasLimit(),
        Optional.of(privacyPrecompileAddress),
        privateTransaction.getValue(),
        privateTransaction.getSignature(),
        BytesValue.wrap(transactionEnclaveKey.getBytes(Charset.defaultCharset())),
        privateTransaction.getSender(),
        privateTransaction.getChainId().getAsInt());
  }
}
