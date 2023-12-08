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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.tests.web3j.generated.RevertReason.FUNC_REVERTWITHREVERTREASON;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.besu.tests.web3j.generated.RevertReason;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.utils.Restriction;

public class PrivCallAcceptanceTest extends ParameterizedEnclaveTestBase {

  private static final int VALUE = 1024;

  private PrivacyNode minerNode;

  public void setUp(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {

    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE.resolve(enclaveEncryptorType),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(minerNode);
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("params")
  public void mustReturnCorrectValue(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);

    final String privacyGroupId =
        minerNode.execute(
            createPrivacyGroup(restriction, "myGroupName", "my group description", minerNode));

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                restriction,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), minerNode.getAddress().toString())
        .verify(eventEmitter);

    final Request<Object, EthCall> priv_call =
        privCall(privacyGroupId, eventEmitter, false, false, false);

    EthCall resp = priv_call.send();

    String value = resp.getValue();
    assertThat(new BigInteger(value.substring(2), 16)).isEqualByComparingTo(BigInteger.ZERO);

    final TransactionReceipt receipt = eventEmitter.store(BigInteger.valueOf(VALUE)).send();
    assertThat(receipt).isNotNull();

    resp = priv_call.send();
    value = resp.getValue();
    assertThat(new BigInteger(value.substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(VALUE));
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("enclaveParameters")
  public void mustRevertWithRevertReason(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);

    final String privacyGroupId =
        minerNode.execute(
            createPrivacyGroup(restriction, "myGroupName", "my group description", minerNode));

    final RevertReason revertReasonContract =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                RevertReason.class,
                minerNode.getTransactionSigningKey(),
                restriction,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            revertReasonContract.getContractAddress(), minerNode.getAddress().toString())
        .verify(revertReasonContract);

    final Request<Object, EthCall> priv_call =
        privCall(privacyGroupId, revertReasonContract, false, false, true);

    EthCall resp = priv_call.send();
    assertThat(resp.getRevertReason()).isEqualTo("Execution reverted: RevertReason");

    byte[] bytes = Hex.decode(resp.getError().getData().substring(3, 203));
    String revertMessage =
        new String(Arrays.copyOfRange(bytes, 4, bytes.length), Charset.defaultCharset()).trim();
    assertThat(revertMessage).isEqualTo("RevertReason");
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("enclaveParameters")
  public void shouldReturnEmptyResultWithNonExistingPrivacyGroup(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);

    final String privacyGroupId =
        minerNode.execute(
            createPrivacyGroup(restriction, "myGroupName", "my group description", minerNode));

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                restriction,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), minerNode.getAddress().toString())
        .verify(eventEmitter);

    final String invalidPrivacyGroup = constructInvalidString(privacyGroupId);
    final Request<Object, EthCall> privCall =
        privCall(invalidPrivacyGroup, eventEmitter, false, false, false);

    final EthCall result = privCall.send();

    assertThat(result.getResult()).isEqualTo("0x");
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("enclaveParameters")
  public void mustNotSucceedWithWronglyEncodedFunction(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);

    final String privacyGroupId =
        minerNode.execute(
            createPrivacyGroup(restriction, "myGroupName", "my group description", minerNode));

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                restriction,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), minerNode.getAddress().toString())
        .verify(eventEmitter);

    final Request<Object, EthCall> priv_call =
        privCall(privacyGroupId, eventEmitter, true, false, false);

    final String errorMessage = priv_call.send().getError().getMessage();
    assertThat(errorMessage).isEqualTo("Private transaction failed");
  }

  @ParameterizedTest(name = "{0} tx with {1} enclave and {2} encryptor type")
  @MethodSource("enclaveParameters")
  public void mustReturn0xUsingInvalidContractAddress(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws Exception {
    setUp(restriction, enclaveType, enclaveEncryptorType);

    final String privacyGroupId =
        minerNode.execute(
            createPrivacyGroup(restriction, "myGroupName", "my group description", minerNode));

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                restriction,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), minerNode.getAddress().toString())
        .verify(eventEmitter);

    final Request<Object, EthCall> priv_call =
        privCall(privacyGroupId, eventEmitter, false, true, false);

    final EthCall result = priv_call.send();

    assertThat(result).isNotNull();
    assertThat(result.getResult()).isEqualTo("0x");
  }

  @Nonnull
  private String constructInvalidString(final String privacyGroupId) {
    final char[] chars = privacyGroupId.toCharArray();
    if (chars[3] == '0') {
      chars[3] = '1';
    } else {
      chars[3] = '0';
    }
    return String.valueOf(chars);
  }

  @Nonnull
  private Request<Object, EthCall> privCall(
      final String privacyGroupId,
      final Contract contract,
      final boolean useInvalidParameters,
      final boolean useInvalidContractAddress,
      final boolean useRevertFunction) {

    final Uint256 invalid = new Uint256(BigInteger.TEN);

    @SuppressWarnings("rawtypes")
    final List<Type> inputParameters =
        useInvalidParameters ? Arrays.asList(invalid) : Collections.emptyList();

    final Function function =
        useRevertFunction
            ? new Function(
                FUNC_REVERTWITHREVERTREASON,
                inputParameters,
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}))
            : new Function(
                "value",
                inputParameters,
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));

    final String encoded = FunctionEncoder.encode(function);

    final HttpService httpService =
        new HttpService(
            "http://"
                + minerNode.getBesu().getHostName()
                + ":"
                + minerNode.getBesu().getJsonRpcPort().get());

    final String validContractAddress = contract.getContractAddress();
    final String invalidContractAddress = constructInvalidString(validContractAddress);
    final String contractAddress =
        useInvalidContractAddress ? invalidContractAddress : validContractAddress;

    final Transaction transaction =
        Transaction.createEthCallTransaction(null, contractAddress, encoded);

    return new Request<>(
        "priv_call",
        Arrays.asList(privacyGroupId, transaction, "latest"),
        httpService,
        EthCall.class);
  }
}
