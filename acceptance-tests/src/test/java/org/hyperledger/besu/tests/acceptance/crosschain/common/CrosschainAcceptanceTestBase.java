/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.common;

import org.hyperledger.besu.tests.acceptance.crosschain.generated.CrosschainCoordinationV1;
import org.hyperledger.besu.tests.acceptance.crosschain.generated.VotingAlgMajorityWhoVoted;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;

import java.math.BigInteger;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.JsonRpc2_0Besu;
import org.web3j.tx.CrosschainTransactionManager;

public abstract class CrosschainAcceptanceTestBase extends AcceptanceTestBase {
  public static final int VOTING_TIME_OUT = 2;
  public static final long CROSSCHAIN_TRANSACTION_TIMEOUT = 10;

  protected Cluster clusterCoordinationBlockchain;
  protected BesuNode nodeOnCoordinationBlockchain;
  protected CrosschainCoordinationV1 coordContract;

  protected Cluster clusterBc1;
  protected BesuNode nodeOnBlockchain1;
  protected CrosschainTransactionManager transactionManagerBlockchain1;
  protected long BLOCKCHAIN1_SLEEP_DURATION = 2000;
  protected int BLOCKCHAIN1_RETRY_ATTEMPTS = 3;

  protected Cluster clusterBc2;
  protected BesuNode nodeOnBlockchain2;
  protected CrosschainTransactionManager transactionManagerBlockchain2;

  public void setUpCoordiantionChain() throws Exception {
    nodeOnCoordinationBlockchain =
        besu.createCrosschainCoordinationBlockchainIbft2Node("coord-node");
    this.clusterCoordinationBlockchain = new Cluster(this.net);
    this.clusterCoordinationBlockchain.start(nodeOnCoordinationBlockchain);

    final VotingAlgMajorityWhoVoted votingContract =
        nodeOnCoordinationBlockchain.execute(
            contractTransactions.createSmartContract(VotingAlgMajorityWhoVoted.class));
    this.coordContract =
        nodeOnCoordinationBlockchain.execute(
            contractTransactions.createSmartContract(
                CrosschainCoordinationV1.class,
                votingContract.getContractAddress(),
                BigInteger.valueOf(VOTING_TIME_OUT)));
  }

  public void setUpBlockchain1() throws Exception {
    this.nodeOnBlockchain1 = besu.createCrosschainBlockchain1Ibft2Node("bc1-node");
    this.clusterBc1 = new Cluster(this.net);
    this.clusterBc1.start(nodeOnBlockchain1);

    JsonRpc2_0Besu blockchain1Web3j = this.nodeOnBlockchain1.getJsonRpc();
    final Credentials BENEFACTOR_ONE = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    JsonRpc2_0Besu coordinationWeb3j = this.nodeOnCoordinationBlockchain.getJsonRpc();

    this.transactionManagerBlockchain1 =
        new CrosschainTransactionManager(
            blockchain1Web3j,
            BENEFACTOR_ONE,
            this.nodeOnBlockchain1.getChainId(),
            BLOCKCHAIN1_RETRY_ATTEMPTS,
            BLOCKCHAIN1_SLEEP_DURATION,
            coordinationWeb3j,
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress(),
            CROSSCHAIN_TRANSACTION_TIMEOUT);
  }

  public void setUpBlockchain2() throws Exception {
    this.nodeOnBlockchain2 = besu.createCrosschainBlockchain2Ibft2Node("bc2-node");
    this.clusterBc2 = new Cluster(this.net);
    this.clusterBc2.start(nodeOnBlockchain2);

    JsonRpc2_0Besu blockchain2Web3j = this.nodeOnBlockchain2.getJsonRpc();
    final Credentials BENEFACTOR_ONE = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    JsonRpc2_0Besu coordinationWeb3j = this.nodeOnCoordinationBlockchain.getJsonRpc();

    this.transactionManagerBlockchain2 =
        new CrosschainTransactionManager(
            blockchain2Web3j,
            BENEFACTOR_ONE,
            this.nodeOnBlockchain2.getChainId(),
            BLOCKCHAIN1_RETRY_ATTEMPTS,
            BLOCKCHAIN1_SLEEP_DURATION,
            coordinationWeb3j,
            this.nodeOnCoordinationBlockchain.getChainId(),
            this.coordContract.getContractAddress(),
            CROSSCHAIN_TRANSACTION_TIMEOUT);
  }

  public void addMultichainNode(final BesuNode node, final BesuNode nodeToAdd) {
    String ipAddress = nodeToAdd.jsonRpcListenHost1();
    int port = nodeToAdd.getJsonRpcSocketPort1().intValue();
    String ipAddressAndPort = ipAddress + ":" + port;
    BigInteger chainId = nodeToAdd.getChainId();

    node.execute(crossTransactions.getAddMultichainNode(chainId, ipAddressAndPort));
  }
}
