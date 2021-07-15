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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Restriction;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.mockito.Mock;

public class BaseEeaSendRawTransaction {

  final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  final Transaction PUBLIC_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(1),
          21000L,
          Optional.of(
              Address.wrap(Bytes.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))),
          Wei.ZERO,
          SIGNATURE_ALGORITHM
              .get()
              .createSignature(
                  new BigInteger(
                      "32886959230931919120748662916110619501838190146643992583529828535682419954515"),
                  new BigInteger(
                      "14473701025599600909210599917245952381483216609124029382871721729679842002948"),
                  Byte.parseByte("0")),
          Bytes.fromHexString("0x"),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());

  final JsonRpcRequestContext validPrivateForTransactionRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest(
              "2.0", "eea_sendRawTransaction", new String[] {validPrivateForTransaction()}));

  final JsonRpcRequestContext validPrivacyGroupTransactionRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest(
              "2.0",
              "eea_sendRawTransaction",
              new String[] {validPrivatePrivacyGroupTransaction(Restriction.RESTRICTED)}));

  final JsonRpcRequestContext validUnrestrictedPrivacyGroupTransactionRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest(
              "2.0",
              "eea_sendRawTransaction",
              new String[] {validPrivatePrivacyGroupTransaction(Restriction.UNRESTRICTED)}));

  final JsonRpcRequestContext validUnsuportedPrivacyGroupTransactionRequest =
      new JsonRpcRequestContext(
          new JsonRpcRequest(
              "2.0",
              "eea_sendRawTransaction",
              new String[] {validPrivatePrivacyGroupTransaction(Restriction.UNSUPPORTED)}));

  @Mock TransactionPool transactionPool;
  @Mock PrivacyController privacyController;

  private String validPrivateForTransaction() {
    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privateFor(
                List.of(
                    Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="),
                    Bytes.fromBase64String("QTFhVnRNeExDVUhtQlZIWG9aenpCZ1BiVy93ajVheER=")))
            .restriction(Restriction.RESTRICTED);
    return rlpEncodeTransaction(privateTransactionBuilder);
  }

  private String validPrivatePrivacyGroupTransaction(final Restriction restriction) {
    final PrivateTransaction.Builder privateTransactionBuilder =
        PrivateTransaction.builder()
            .nonce(0)
            .gasPrice(Wei.of(1))
            .gasLimit(21000)
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .to(Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87"))
            .chainId(BigInteger.ONE)
            .privateFrom(Bytes.fromBase64String("S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXp="))
            .privacyGroupId(Bytes.fromBase64String("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="))
            .restriction(restriction);
    return rlpEncodeTransaction(privateTransactionBuilder);
  }

  private String rlpEncodeTransaction(final PrivateTransaction.Builder privateTransactionBuilder) {
    final KeyPair keyPair =
        SIGNATURE_ALGORITHM
            .get()
            .createKeyPair(
                SIGNATURE_ALGORITHM
                    .get()
                    .createPrivateKey(
                        new BigInteger(
                            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
                            16)));

    final PrivateTransaction privateTransaction = privateTransactionBuilder.signAndBuild(keyPair);
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    return bvrlp.encoded().toHexString();
  }
}
