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
package org.hyperledger.besu.tests.acceptance.crosschain.getinfo;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.common.CrosschainAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated.Ctrt1;
import org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated.Ctrt2;
import org.hyperledger.besu.tests.acceptance.crosschain.getinfo.generated.Ctrt3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.CrosschainContext;
import org.web3j.tx.CrosschainContextGenerator;

/*
 * Three contracts - Ctrt1, Ctrt2 and Ctrt3 are deployed on three separate blockchains.
 * Ctrt1.callCtrt2 calls a transaction function Ctrt2.callCtrt3, which calls a view
 * Ctrt3.viewfn. Various getInfo methods are checked in the tests.
 */

public class GetInfoTest extends CrosschainAcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  private Ctrt1 ctrt1;
  private Ctrt2 ctrt2;
  private Ctrt3 ctrt3;

  @Before
  public void setUp() throws Exception {

    setUpCoordiantionChain();
    setUpBlockchain1();
    setUpBlockchain2();
    setUpBlockchain3();

    // Deploying the contracts
    ctrt1 =
        nodeOnBlockchain1.execute(
            contractTransactions.createLockableSmartContract(
                Ctrt1.class, this.transactionManagerBlockchain1));
    ctrt2 =
        nodeOnBlockchain2.execute(
            contractTransactions.createLockableSmartContract(
                Ctrt2.class, this.transactionManagerBlockchain2));
    ctrt3 =
        nodeOnBlockchain3.execute(
            contractTransactions.createLockableSmartContract(
                Ctrt3.class, this.transactionManagerBlockchain3));

    // Making nodeOnBlockChain1 a 3-chain node and nodeOnBlockChain2 a 2-chain node
    addMultichainNode(nodeOnBlockchain1, nodeOnBlockchain2);
    addMultichainNode(nodeOnBlockchain1, nodeOnBlockchain3);
    addMultichainNode(nodeOnBlockchain2, nodeOnBlockchain3);

    // Setting up the environment
    ctrt1.setCtrt2ChainId(nodeOnBlockchain2.getChainId()).send();
    ctrt1.setCtrt2(ctrt2.getContractAddress()).send();
    ctrt2.setCtrt3ChainId(nodeOnBlockchain3.getChainId()).send();
    ctrt2.setCtrt3(ctrt3.getContractAddress()).send();
  }

  @Test
  public void getInfoTest() throws Exception {
    // Constructing the crosschain transaction
    CrosschainContextGenerator ctxGen =
        new CrosschainContextGenerator(nodeOnBlockchain1.getChainId());
    CrosschainContext ctx1 =
        ctxGen.createCrosschainContext(nodeOnBlockchain2.getChainId(), ctrt2.getContractAddress());
    byte[] subT1 = ctrt3.txfn_AsSignedCrosschainSubordinateTransaction(ctx1);
    byte[][] subTxV1 = new byte[][] {subT1};

    CrosschainContext ctx2 =
        ctxGen.createCrosschainContext(
            nodeOnBlockchain1.getChainId(), ctrt1.getContractAddress(), subTxV1);
    byte[] subTx2 = ctrt2.callCtrt3_AsSignedCrosschainSubordinateTransaction(ctx2);

    CrosschainContext ctx3 =
        ctxGen.createCrosschainContext(nodeOnBlockchain1.getChainId(), ctrt1.getContractAddress());
    byte[] subV2 = ctrt2.viewfn_AsSignedCrosschainSubordinateView(ctx3);
    byte[][] subTxV2 = new byte[][] {subTx2, subV2};

    // Executing the crosschain transaction
    CrosschainContext ctx = ctxGen.createCrosschainContext(subTxV2);
    TransactionReceipt txReceipt = ctrt1.callCtrt2_AsCrosschainTransaction(ctx).send();
    if (!txReceipt.isStatusOK()) {
      LOG.info("txReceipt details " + txReceipt.toString());
    }

    // Checking the GetInfo methods return values
    long chain1Id = nodeOnBlockchain1.getChainId().longValue();
    long chain2Id = nodeOnBlockchain2.getChainId().longValue();
    long chain3Id = nodeOnBlockchain3.getChainId().longValue();
    long cbcId = nodeOnCoordinationBlockchain.getChainId().longValue();

    // TODO Tests related to TxType are not happening correctly. Need to fix.
    waitForUnlock(ctrt1.getContractAddress(), nodeOnBlockchain1);
    assertThat(ctrt1.myChainId().send().longValue()).isEqualTo(chain1Id);
    assertThat(ctrt1.coordChainId().send().longValue()).isEqualTo(cbcId);
    assertThat(ctrt1.coordCtrtAddr().send()).isEqualTo(coordContract.getContractAddress());
    // assertThat(ctrt1.fromChainId().send().longValue()).isEqualTo(chain1Id);
    // assertThat(ctrt1.origChainId().send().longValue()).isEqualTo(chain1Id);
    // assertThat(ctrt1.fromAddr().send()).isEqualTo(BENEFACTOR_ONE.getAddress());
    // assertThat(ctrt1.viewTxType().send().longValue()).isEqualTo(2);
    // assertThat(ctrt1.consTxType().send().intValue()).isEqualTo(5);
    // assertThat(ctrt1.myTxType().send().longValue()).isEqualTo(0);

    waitForUnlock(ctrt2.getContractAddress(), nodeOnBlockchain2);
    assertThat(ctrt2.myChainId().send().longValue()).isEqualTo(chain2Id);
    assertThat(ctrt2.coordChainId().send().longValue()).isEqualTo(cbcId);
    assertThat(ctrt2.fromChainId().send().longValue()).isEqualTo(chain1Id);
    assertThat(ctrt2.origChainId().send().longValue()).isEqualTo(chain1Id);
    assertThat(ctrt2.myTxType().send().longValue()).isEqualTo(0);
    assertThat(ctrt2.coordCtrtAddr().send()).isEqualTo(coordContract.getContractAddress());
    assertThat(ctrt2.fromAddr().send()).isEqualTo(ctrt1.getContractAddress());
    assertThat(ctrt2.txId().send().longValue())
        .isEqualTo(ctx2.getCrosschainTransactionId().longValue());
    // assertThat(ctrt2.consTxType().send().intValue()).isEqualTo(5);
    // assertThat(ctrt2.myTxType().send().longValue()).isEqualTo(1);

    waitForUnlock(ctrt3.getContractAddress(), nodeOnBlockchain3);
    assertThat(ctrt3.myChainId().send().longValue()).isEqualTo(chain3Id);
    assertThat(ctrt3.coordChainId().send().longValue()).isEqualTo(cbcId);
    assertThat(ctrt3.origChainId().send().longValue()).isEqualTo(chain1Id);
    assertThat(ctrt3.fromChainId().send().longValue()).isEqualTo(chain2Id);
    assertThat(ctrt2.coordCtrtAddr().send()).isEqualTo(coordContract.getContractAddress());
    assertThat(ctrt3.fromAddr().send()).isEqualTo(ctrt2.getContractAddress());
    assertThat(ctrt3.txId().send().longValue())
        .isEqualTo(ctx1.getCrosschainTransactionId().longValue());
    // assertThat(ctrt3.consTxType().send().intValue()).isEqualTo(5);
    // assertThat(ctrt3.myTxType().send().longValue()).isEqualTo(2);
  }

  @After
  public void closeDown() throws Exception {
    this.cluster.close();
    this.clusterBc1.close();
    this.clusterBc2.close();
    this.clusterBc3.close();
  }
}
