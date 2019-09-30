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
import org.hyperledger.besu.tests.web3j.generated.CrossContractReader;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.pantheon.response.privacy.PrivateTransactionReceipt;
import org.web3j.tx.exceptions.ContractCallException;

public class PrivateContractPublicStateAcceptanceTest extends PrivacyAcceptanceTestBase {
  private static final long POW_CHAIN_ID = 2018;

  private PrivacyNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "miner-node", privacyAccountResolver.resolve(0));
    privacyCluster.start(minerNode);
  }

  @Test
  public void mustAllowAccessToPublicStateFromPrivateTx() throws Exception {
    final EventEmitter publicEventEmitter =
        minerNode.getBesu().execute((contractTransactions.createSmartContract(EventEmitter.class)));

    final TransactionReceipt receipt = publicEventEmitter.store(BigInteger.valueOf(12)).send();
    assertThat(receipt).isNotNull();

    final CrossContractReader reader =
        minerNode
            .getBesu()
            .execute(
                privateContractTransactions.createSmartContract(
                    CrossContractReader.class,
                    minerNode.getTransactionSigningKey(),
                    POW_CHAIN_ID,
                    minerNode.getEnclaveKey()));

    assertThat(reader.read(publicEventEmitter.getContractAddress()).send())
        .isEqualTo(BigInteger.valueOf(12));
  }

  @Test(expected = ContractCallException.class)
  public void mustNotAllowAccessToPrivateStateFromPublicTx() throws Exception {
    final EventEmitter privateEventEmitter =
        minerNode
            .getBesu()
            .execute(
                (privateContractTransactions.createSmartContract(
                    EventEmitter.class,
                    minerNode.getTransactionSigningKey(),
                    POW_CHAIN_ID,
                    minerNode.getEnclaveKey())));

    final TransactionReceipt receipt = privateEventEmitter.store(BigInteger.valueOf(12)).send();
    assertThat(receipt).isNotNull();

    final CrossContractReader publicReader =
        minerNode
            .getBesu()
            .execute(contractTransactions.createSmartContract(CrossContractReader.class));

    publicReader.read(privateEventEmitter.getContractAddress()).send();
  }

  @Test
  public void privateContractMustNotBeAbleToCallPublicContractWhichChangesState() throws Exception {
    final CrossContractReader privateReader =
        minerNode
            .getBesu()
            .execute(
                privateContractTransactions.createSmartContract(
                    CrossContractReader.class,
                    minerNode.getTransactionSigningKey(),
                    POW_CHAIN_ID,
                    minerNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        minerNode
            .getBesu()
            .execute(contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.incrementRemote(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getOutput()).isEqualTo("0x");
  }

  @Test
  public void privateContractMustNotBeAbleToCallPublicContractWhichInstantiatesContract()
      throws Exception {
    final CrossContractReader privateReader =
        minerNode
            .getBesu()
            .execute(
                privateContractTransactions.createSmartContract(
                    CrossContractReader.class,
                    minerNode.getTransactionSigningKey(),
                    POW_CHAIN_ID,
                    minerNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        minerNode
            .getBesu()
            .execute(contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.deployRemote(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getLogs().size()).isEqualTo(0);
  }

  @Test
  public void privateContractMustNotBeAbleToCallSelfDetructOfPublicContract() throws Exception {
    final CrossContractReader privateReader =
        minerNode
            .getBesu()
            .execute(
                privateContractTransactions.createSmartContract(
                    CrossContractReader.class,
                    minerNode.getTransactionSigningKey(),
                    POW_CHAIN_ID,
                    minerNode.getEnclaveKey()));

    final CrossContractReader publicReader =
        minerNode
            .getBesu()
            .execute(contractTransactions.createSmartContract(CrossContractReader.class));

    final PrivateTransactionReceipt transactionReceipt =
        (PrivateTransactionReceipt)
            privateReader.remoteDestroy(publicReader.getContractAddress()).send();

    assertThat(transactionReceipt.getOutput()).isEqualTo("0x");
  }
}
