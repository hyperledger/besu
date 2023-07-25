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
package org.hyperledger.besu.ethereum.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateTransactionDataFixture {

  public static final long DEFAULT_NONCE = 0;
  public static final Wei DEFAULT_GAS_PRICE = Wei.of(1000);
  public static final long DEFAULT_GAS_LIMIT = 3000000;
  public static final Wei DEFAULT_VALUE = Wei.of(0);
  public static final Address DEFAULT_SENDER =
      Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
  public static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(1337);

  public static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  public static final KeyPair KEY_PAIR =
      SIGNATURE_ALGORITHM
          .get()
          .createKeyPair(
              SIGNATURE_ALGORITHM
                  .get()
                  .createPrivateKey(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  public static final Bytes32 VALID_BASE64_ENCLAVE_KEY =
      Bytes32.wrap(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));

  public static final Bytes VALID_CONTRACT_DEPLOYMENT_PAYLOAD =
      Bytes.fromHexString(
          "0x608060405234801561001057600080fd5b5060d08061001f60003960"
              + "00f3fe60806040526004361060485763ffffffff7c01000000"
              + "00000000000000000000000000000000000000000000000000"
              + "60003504166360fe47b18114604d5780636d4ce63c14607557"
              + "5b600080fd5b348015605857600080fd5b5060736004803603"
              + "6020811015606d57600080fd5b50356099565b005b34801560"
              + "8057600080fd5b506087609e565b6040805191825251908190"
              + "0360200190f35b600055565b6000549056fea165627a7a7230"
              + "5820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6"
              + "daa4f6b2f003d1b0180029");
  public static final Address VALID_CONTRACT_DEPLOYMENT_ADDRESS =
      Address.fromHexString("0x0bac79b78b9866ef11c989ad21a7fcf15f7a18d7");

  public static Transaction privateMarkerTransaction() {
    return privateMarkerTransaction(VALID_BASE64_ENCLAVE_KEY, DEFAULT_PRIVACY);
  }

  public static Transaction privateMarkerTransactionOnchain() {
    return privateMarkerTransaction(VALID_BASE64_ENCLAVE_KEY, FLEXIBLE_PRIVACY);
  }

  public static Transaction privateMarkerTransactionOnchainAdd() {
    return privateMarkerTransaction(
        Bytes.concatenate(VALID_BASE64_ENCLAVE_KEY, VALID_BASE64_ENCLAVE_KEY), FLEXIBLE_PRIVACY);
  }

  private static Transaction privateMarkerTransaction(
      final Bytes transactionKey, final Address precompiledContractAddress) {
    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .nonce(DEFAULT_NONCE)
        .gasPrice(DEFAULT_GAS_PRICE)
        .gasLimit(DEFAULT_GAS_LIMIT)
        .to(precompiledContractAddress)
        .value(DEFAULT_VALUE)
        .payload(transactionKey)
        .sender(DEFAULT_SENDER)
        .chainId(DEFAULT_CHAIN_ID)
        .signAndBuild(KEY_PAIR);
  }

  public static PrivateTransaction privateTransactionLegacy() {
    return new PrivateTransactionTestFixture()
        .privateFor(Collections.singletonList(VALID_BASE64_ENCLAVE_KEY))
        .createTransaction(KEY_PAIR);
  }

  public static PrivateTransaction privateContractDeploymentTransactionLegacy() {
    return new PrivateTransactionTestFixture()
        .payload(VALID_CONTRACT_DEPLOYMENT_PAYLOAD)
        .privateFor(Collections.singletonList(VALID_BASE64_ENCLAVE_KEY))
        .createTransaction(KEY_PAIR);
  }

  public static PrivateTransaction privateTransactionBesu() {
    return new PrivateTransactionTestFixture()
        .privacyGroupId(VALID_BASE64_ENCLAVE_KEY)
        .createTransaction(KEY_PAIR);
  }

  public static VersionedPrivateTransaction versionedPrivateTransactionBesu() {
    return new PrivateTransactionTestFixture()
        .privacyGroupId(VALID_BASE64_ENCLAVE_KEY)
        .createVersionedPrivateTransaction((KEY_PAIR));
  }

  public static PrivateTransaction privateContractDeploymentTransactionBesu() {
    return new PrivateTransactionTestFixture()
        .payload(VALID_CONTRACT_DEPLOYMENT_PAYLOAD)
        .privacyGroupId(VALID_BASE64_ENCLAVE_KEY)
        .createTransaction(KEY_PAIR);
  }

  public static PrivateTransaction privateContractDeploymentTransactionBesu(
      final String privateFrom) {
    return new PrivateTransactionTestFixture()
        .payload(VALID_CONTRACT_DEPLOYMENT_PAYLOAD)
        .privacyGroupId(Bytes.fromBase64String(privateFrom))
        .createTransaction(KEY_PAIR);
  }

  public static ReceiveResponse generateReceiveResponse(
      final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(rlpOutput);
    return new ReceiveResponse(
        rlpOutput.encoded().toBase64String().getBytes(UTF_8),
        privateTransaction.getPrivacyGroupId().isPresent()
            ? privateTransaction.getPrivacyGroupId().get().toBase64String()
            : "",
        null);
  }

  public static ReceiveResponse generateVersionedReceiveResponse(
      final PrivateTransaction privateTransaction) {
    final VersionedPrivateTransaction versionedPrivateTransaction =
        new VersionedPrivateTransaction(privateTransaction, Bytes32.ZERO);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    versionedPrivateTransaction.writeTo(rlpOutput);
    return new ReceiveResponse(
        rlpOutput.encoded().toBase64String().getBytes(UTF_8),
        privateTransaction.getPrivacyGroupId().isPresent()
            ? privateTransaction.getPrivacyGroupId().get().toBase64String()
            : "",
        null);
  }

  public static ReceiveResponse generateAddToGroupReceiveResponse(
      final PrivateTransaction privateTransaction, final Transaction markerTransaction) {
    final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
        generateAddBlobResponse(privateTransaction, markerTransaction);
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    privateTransactionWithMetadataList.stream()
        .forEach(
            privateTransactionWithMetadata -> privateTransactionWithMetadata.writeTo(rlpOutput));
    rlpOutput.endList();
    return new ReceiveResponse(
        rlpOutput.encoded().toBase64String().getBytes(UTF_8),
        privateTransaction.getPrivacyGroupId().orElse(Bytes.EMPTY).toBase64String(),
        null);
  }

  public static List<PrivateTransactionWithMetadata> generateAddBlobResponse(
      final PrivateTransaction privateTransaction, final Transaction markerTransaction) {
    final PrivateTransactionWithMetadata privateTransactionWithMetadata =
        new PrivateTransactionWithMetadata(
            privateTransaction,
            new PrivateTransactionMetadata(markerTransaction.getHash(), Hash.ZERO));
    return Collections.singletonList(privateTransactionWithMetadata);
  }

  public static PrivateTransactionMetadata generatePrivateTransactionMetadata() {
    return new PrivateTransactionMetadata(Hash.hash(Bytes32.random()), Hash.hash(Bytes32.random()));
  }

  public static List<PrivateTransactionMetadata> generatePrivateTransactionMetadataList(
      final int length) {
    return IntStream.range(0, length)
        .mapToObj((i) -> generatePrivateTransactionMetadata())
        .collect(Collectors.toList());
  }

  public static PrivateBlockMetadata generatePrivateBlockMetadata(final int numberOfTransactions) {
    return new PrivateBlockMetadata(generatePrivateTransactionMetadataList(numberOfTransactions));
  }

  public static Bytes encodePrivateTransaction(
      final PrivateTransaction privateTransaction, final Optional<Bytes32> version) {
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    if (version.isEmpty()) {
      privateTransaction.writeTo(output);
    } else {
      final VersionedPrivateTransaction versionedPrivateTransaction =
          new VersionedPrivateTransaction(privateTransaction, Bytes32.ZERO);
      versionedPrivateTransaction.writeTo(output);
    }
    return output.encoded();
  }
}
