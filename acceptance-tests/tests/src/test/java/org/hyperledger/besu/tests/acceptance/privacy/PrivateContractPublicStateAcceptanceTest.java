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
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.CrossContractReader;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.exceptions.ContractCallException;
import org.web3j.utils.Restriction;

public class PrivateContractPublicStateAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode transactionNode;

  public PrivateContractPublicStateAcceptanceTest(
      final Restriction restriction, final EnclaveType enclaveType) throws IOException {
    super(restriction, enclaveType);
    final Network containerNetwork = Network.newNetwork();

    final PrivacyNode minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-miner-node",
            privacyAccountResolver.resolve(0),
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            restriction == UNRESTRICTED);

    transactionNode =
        privacyBesu.createPrivateTransactionEnabledNode(
            restriction + "-transaction-node",
            privacyAccountResolver.resolve(1),
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(minerNode, transactionNode);
  }

  @Test
  @Ignore("Web3j needs to be updated https://github.com/hyperledger/besu/issues/2098")
  public void mustAllowAccessToPublicStateFromPrivateTx() throws Exception {
    final EventEmitter publicEventEmitter =
        transactionNode.execute(contractTransactions.createSmartContract(EventEmitter.class));

    final TransactionReceipt receipt = publicEventEmitter.store(BigInteger.valueOf(12)).send();
    assertThat(receipt).isNotNull();

    final CrossContractReader reader =
        transactionNode.execute(
            privateContractTransactions.createSmartContract(
                CrossContractReader.class,
                transactionNode.getTransactionSigningKey(),
                restriction,
                transactionNode.getEnclaveKey()));

    final RemoteFunctionCall<BigInteger> remoteFunctionCall =
        reader.read(publicEventEmitter.getContractAddress());
    final BigInteger result = remoteFunctionCall.send();

    assertThat(result).isEqualTo(BigInteger.valueOf(12));
  }

  @Test(expected = ContractCallException.class)
  public void mustNotAllowAccessToPrivateStateFromPublicTx() throws Exception {
    final EventEmitter privateEventEmitter =
        transactionNode.execute(
            (privateContractTransactions.createSmartContract(
                EventEmitter.class,
                transactionNode.getTransactionSigningKey(),
                restriction,
                transactionNode.getEnclaveKey())));

    final TransactionReceipt receipt = privateEventEmitter.store(BigInteger.valueOf(12)).send();
    assertThat(receipt).isNotNull();

    final CrossContractReader publicReader =
        transactionNode.execute(
            contractTransactions.createSmartContract(CrossContractReader.class));

    publicReader.read(privateEventEmitter.getContractAddress()).send();
  }

  @Test
  public void privateContractMustNotBeAbleToCallPublicContractWhichChangesState() throws Exception {
    final CrossContractReader privateReader =
        transactionNode.execute(
            privateContractTransactions.createSmartContract(
                CrossContractReader.class,
                transactionNode.getTransactionSigningKey(),
                restriction,
                transactionNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        transactionNode.execute(
            contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.incrementRemote(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getOutput()).isEqualTo("0x");
  }

  @Test
  public void privateContractMustNotBeAbleToCallPublicContractWhichInstantiatesContract()
      throws Exception {
    final CrossContractReader privateReader =
        transactionNode.execute(
            privateContractTransactions.createSmartContract(
                CrossContractReader.class,
                transactionNode.getTransactionSigningKey(),
                restriction,
                transactionNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        transactionNode.execute(
            contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.deployRemote(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getLogs().size()).isEqualTo(0);
  }

  @Test
  public void privateContractMustNotBeAbleToCallSelfDestructOnPublicContract() throws Exception {
    final CrossContractReader privateReader =
        transactionNode.execute(
            privateContractTransactions.createSmartContract(
                CrossContractReader.class,
                transactionNode.getTransactionSigningKey(),
                restriction,
                transactionNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        transactionNode
            .getBesu()
            .execute(contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.remoteDestroy(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getOutput()).isEqualTo("0x");
  }
}
