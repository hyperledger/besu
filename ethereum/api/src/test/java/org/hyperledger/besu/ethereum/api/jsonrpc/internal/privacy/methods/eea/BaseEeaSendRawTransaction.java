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
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.BerlinGasCalculator;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.plugin.data.Restriction;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.mockito.Mock;

public class BaseEeaSendRawTransaction {

  final String MOCK_ORION_KEY = "bW9ja2tleQ==";

  final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM_SUPPLIER =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  final KeyPair keyPair =
      SIGNATURE_ALGORITHM_SUPPLIER
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM_SUPPLIER
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  @Mock BlockchainQueries blockchainQueries;

  final PrivateMarkerTransactionFactory privateMarkerTransactionFactory =
      new FixedKeySigningPrivateMarkerTransactionFactory(keyPair);

  final GasCalculator gasCalculator = new BerlinGasCalculator();

  final Transaction PUBLIC_ONCHAIN_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(10),
          53112L,
          Optional.of(Address.ONCHAIN_PRIVACY),
          Wei.ZERO,
          SIGNATURE_ALGORITHM_SUPPLIER
              .get()
              .createSignature(
                  new BigInteger(
                      "50758026553589882805766983929273855101221905798412082519492083555127819310349"),
                  new BigInteger(
                      "9530450080360267411370951841468649653272877590170696539371969233272943560187"),
                  Byte.parseByte("0")),
          Bytes.fromBase64String(MOCK_ORION_KEY),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());

  final Transaction PUBLIC_PLUGIN_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(10),
          53112L,
          Optional.of(Address.PLUGIN_PRIVACY),
          Wei.ZERO,
          SIGNATURE_ALGORITHM_SUPPLIER
              .get()
              .createSignature(
                  new BigInteger(
                      "65616314945166426659201091304539863078210238223429712354301511970648788041537"),
                  new BigInteger(
                      "34139684103235825171704381364252972544895368328418359844709930806514113448401"),
                  Byte.parseByte("1")),
          Bytes.fromBase64String(MOCK_ORION_KEY),
          Address.wrap(Bytes.fromHexString("0x8411b12666f68ef74cace3615c9d5a377729d03f")),
          Optional.empty());

  final Transaction PUBLIC_OFF_CHAIN_TRANSACTION =
      new Transaction(
          0L,
          Wei.of(10),
          53112L,
          Optional.of(Address.DEFAULT_PRIVACY),
          Wei.ZERO,
          SIGNATURE_ALGORITHM_SUPPLIER
              .get()
              .createSignature(
                  new BigInteger(
                      "53595641940080695160110221582574037191723037145759311497025720763411433093184"),
                  new BigInteger(
                      "24054190246794487145514519423479743885622463911750079416987334020216197302717"),
                  Byte.parseByte("1")),
          Bytes.fromBase64String(MOCK_ORION_KEY),
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
    final PrivateTransaction privateTransaction = privateTransactionBuilder.signAndBuild(keyPair);
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    return bvrlp.encoded().toHexString();
  }
}
