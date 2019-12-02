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
package org.hyperledger.besu.tests.acceptance.crosschain.viewcall;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.common.CrosschainAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.crosschain.viewcall.generated.BarCtrt;
import org.hyperledger.besu.tests.acceptance.crosschain.viewcall.generated.FooCtrt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.besu.response.crosschain.CrossIsLockedResponse;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.CrosschainContext;
import org.web3j.tx.CrosschainContextGenerator;

/*
 * This test makes a simple crosschain  view call. Two contracts - BarCtrt and FooCtrt are deployed on blockchains 1
 * and 2 respectively. FooCtrt has a simple view function called foo() that does nothing but returns 1. BarCtrt has a
 * function called bar() and a flag that is set to 0 while deploying (thanks to the constructor). bar() updates the
 * flag with the return value of foo(). The test checks if the flag is set to 1 after the crosschain view call.
 */
public class CrosschainViewCall extends CrosschainAcceptanceTestBase {
  private final Logger LOG = LogManager.getLogger();
  private BarCtrt barCtrt;
  private FooCtrt fooCtrt;

  @Before
  public void setUp() throws Exception {

    setUpCoordiantionChain();
    setUpBlockchain1();
    setUpBlockchain2();

    // Making nodeOnBlockChain1 a multichain node
    addMultichainNode(nodeOnBlockchain1, nodeOnBlockchain2);

    // Deploying BarCtrt on BlockChain1 and FooCtrt on BlockChain2
    barCtrt =
        nodeOnBlockchain1.execute(
            contractTransactions.createLockableSmartContract(
                BarCtrt.class, this.transactionManagerBlockchain1));
    fooCtrt =
        nodeOnBlockchain2.execute(
            contractTransactions.createLockableSmartContract(
                FooCtrt.class, this.transactionManagerBlockchain2));

    // Calling a BooCtrt.setProperties, a regular intrachain function call
    barCtrt.setProperties(nodeOnBlockchain2.getChainId(), fooCtrt.getContractAddress()).send();
  }

  @After
  public void closeDown() throws Exception {
    this.cluster.close();
    this.clusterBc1.close();
    this.clusterBc2.close();
  }

  @Test
  public void doCCViewCall() throws Exception {
    CrosschainContextGenerator ctxGenerator =
        new CrosschainContextGenerator(nodeOnBlockchain1.getChainId());
    CrosschainContext subordTxCtx =
        ctxGenerator.createCrosschainContext(
            nodeOnBlockchain1.getChainId(), barCtrt.getContractAddress());
    byte[] subordTrans = fooCtrt.foo_AsSignedCrosschainSubordinateView(subordTxCtx);
    byte[][] subordTxAndViews = new byte[][] {subordTrans};
    CrosschainContext origTxCtx = ctxGenerator.createCrosschainContext(subordTxAndViews);

    TransactionReceipt txReceipt = barCtrt.bar_AsCrosschainTransaction(origTxCtx).send();
    if (!txReceipt.isStatusOK()) {
      LOG.info("txReceipt details " + txReceipt.toString());
      throw new Error(txReceipt.getStatus());
    }

    CrossIsLockedResponse isLockedObj =
        this.nodeOnBlockchain1
            .getJsonRpc()
            .crossIsLocked(barCtrt.getContractAddress(), DefaultBlockParameter.valueOf("latest"))
            .send();
    ;
    while (isLockedObj.isLocked()) {
      Thread.sleep(100);
      isLockedObj =
          this.nodeOnBlockchain1
              .getJsonRpc()
              .crossIsLocked(barCtrt.getContractAddress(), DefaultBlockParameter.valueOf("latest"))
              .send();
    }
    assertThat(barCtrt.flag().send().longValue()).isEqualTo(1);
  }
}
