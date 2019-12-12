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
package org.hyperledger.besu.tests.web3j.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;

public class PrivCallAcceptanceTest extends PrivacyAcceptanceTestBase {

  private static final long POW_CHAIN_ID = 2018;
  private static final int VALUE = 1024;

  private PrivacyNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "miner-node", privacyAccountResolver.resolve(0));
    privacyCluster.start(minerNode);
  }

  @Test
  public void deployingAndDoPrivCallMust() throws Exception {

    final String privacyGroupId =
        minerNode.execute(
            privacyTransactions.createPrivacyGroup(
                "myGroupName", "my group description", minerNode));

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                POW_CHAIN_ID,
                minerNode.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), minerNode.getAddress().toString())
        .verify(eventEmitter);

    final Request<Object, EthCall> priv_call = privCall(privacyGroupId, eventEmitter);

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

  @NotNull
  private Request<Object, EthCall> privCall(
      final String privacyGroupId, final Contract eventEmitter) {

    final Function function =
        new Function(
            "value", List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));

    final String encoded = FunctionEncoder.encode(function);

    final HttpService httpService =
        new HttpService(
            "http://"
                + minerNode.getBesu().getHostName()
                + ":"
                + minerNode.getBesu().getJsonRpcSocketPort().get());

    final Transaction transaction =
        Transaction.createEthCallTransaction(null, eventEmitter.getContractAddress(), encoded);

    return new Request<>(
        "priv_call",
        Arrays.asList(privacyGroupId, transaction, "latest"),
        httpService,
        EthCall.class);
  }
}
