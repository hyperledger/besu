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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import org.junit.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.utils.Restriction;

public class PrivCallAcceptanceTest extends ParameterizedEnclaveTestBase {

  private static final int VALUE = 1024;

  private final PrivacyNode minerNode;

  public PrivCallAcceptanceTest(final Restriction restriction, final EnclaveType enclaveType)
      throws IOException {

    super(restriction, enclaveType);

    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(minerNode);
  }

  @Test
  public void mustReturnCorrectValue() throws Exception {

    final String privacyGroupId =
        minerNode.execute(createPrivacyGroup("myGroupName", "my group description", minerNode));

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

    final Request<Object, EthCall> priv_call = privCall(privacyGroupId, eventEmitter, false, false);

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

  @Test
  public void shouldReturnEmptyResultWithNonExistingPrivacyGroup() throws IOException {

    final String privacyGroupId =
        minerNode.execute(createPrivacyGroup("myGroupName", "my group description", minerNode));

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
        privCall(invalidPrivacyGroup, eventEmitter, false, false);

    final EthCall result = privCall.send();

    assertThat(result.getResult()).isEqualTo("0x");
  }

  @Test
  public void mustNotSucceedWithWronglyEncodedFunction() {

    final String privacyGroupId =
        minerNode.execute(createPrivacyGroup("myGroupName", "my group description", minerNode));

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

    final Request<Object, EthCall> priv_call = privCall(privacyGroupId, eventEmitter, true, false);

    assertThatExceptionOfType(ClientConnectionException.class)
        .isThrownBy(() -> priv_call.send())
        .withMessageContaining("Invalid params");
  }

  @Test
  public void mustReturn0xUsingInvalidContractAddress() throws IOException {

    final String privacyGroupId =
        minerNode.execute(createPrivacyGroup("myGroupName", "my group description", minerNode));

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

    final Request<Object, EthCall> priv_call = privCall(privacyGroupId, eventEmitter, false, true);

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
      final Contract eventEmitter,
      final boolean useInvalidParameters,
      final boolean useInvalidContractAddress) {

    final Uint256 invalid = new Uint256(BigInteger.TEN);

    @SuppressWarnings("rawtypes")
    final List<Type> inputParameters =
        useInvalidParameters ? Arrays.asList(invalid) : Collections.emptyList();

    final Function function =
        new Function(
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

    final String validContractAddress = eventEmitter.getContractAddress();
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
