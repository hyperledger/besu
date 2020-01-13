/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.crosschain.viewtxcallArgs;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.common.CrosschainAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.crosschain.viewtxcallArgs.generated.BarArgsCtrt;
import org.hyperledger.besu.tests.acceptance.crosschain.viewtxcallArgs.generated.FooArgsCtrt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.CrosschainContext;
import org.web3j.tx.CrosschainContextGenerator;

/*
 * Two contracts - BarArgsCtrt and FooArgsCtrt are deployed on blockchains 1 and 2 respectively. Many tests are created
 * to check crosschain transactions happening between views, pure functions and transactions. Nesting the calls
 * are also checked. The goal is to check the crosschain transactions with complex parameters.
 */

public class CrosschainArgsCall extends CrosschainAcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();
  private BarArgsCtrt barCtrt;
  private FooArgsCtrt fooCtrt;

  @Before
  public void setUp() throws Exception {

    setUpCoordinationChain();
    setUpBlockchain1();
    setUpBlockchain2();

    // Deploying BarArgsCtrt on BlockChain1, and FooArgsCtrt on BlockChain2
    barCtrt =
        nodeOnBlockchain1.execute(
            contractTransactions.createLockableSmartContract(
                BarArgsCtrt.class, this.transactionManagerBlockchain1));
    fooCtrt =
        nodeOnBlockchain2.execute(
            contractTransactions.createLockableSmartContract(
                FooArgsCtrt.class, this.transactionManagerBlockchain2));
    LOG.info("Why is payload empty?");

    // Making nodeOnBlockChain1 a multichain node
    addMultichainNode(nodeOnBlockchain1, nodeOnBlockchain2);

    barCtrt.setProperties(nodeOnBlockchain2.getChainId(), fooCtrt.getContractAddress()).send();
    // Calling FooCtrt.setPropertiesForBar, a regular intrachain function call
    fooCtrt
        .setPropertiesForBar(nodeOnBlockchain1.getChainId(), barCtrt.getContractAddress())
        .send();
  }

  @After
  public void closeDown() throws Exception {
    this.cluster.close();
    this.clusterBc1.close();
    this.clusterBc2.close();
  }

  @Test
  public void doCCViewCall() throws Exception {
    final byte[] BYTEARRAY_PARAM =
        javax.xml.bind.DatatypeConverter.parseHexBinary(
            "0000000000000000000000000000000000000000000000000000000000000100");
    final String STR_PARAM = "magic";

    CallSimulator sim = new CallSimulator();
    sim.bar(BYTEARRAY_PARAM, STR_PARAM, true);

    CrosschainContextGenerator ctxGenerator =
        new CrosschainContextGenerator(nodeOnBlockchain1.getChainId());
    CrosschainContext subordTxCtx =
        ctxGenerator.createCrosschainContext(
            nodeOnBlockchain1.getChainId(), barCtrt.getContractAddress());
    byte[] subordTrans =
        fooCtrt.foo_AsSignedCrosschainSubordinateView(sim.arg, sim.a, sim.barstr, subordTxCtx);
    byte[][] subordTxAndViews = new byte[][] {subordTrans};
    CrosschainContext origTxCtx = ctxGenerator.createCrosschainContext(subordTxAndViews);

    TransactionReceipt txReceipt =
        barCtrt.bar_AsCrosschainTransaction(sim.a, sim.barstr, Boolean.TRUE, origTxCtx).send();
    if (!txReceipt.isStatusOK()) {
      LOG.info("txReceipt details " + txReceipt.toString());
      throw new Error(txReceipt.getStatus());
    }

    waitForUnlock(barCtrt.getContractAddress(), nodeOnBlockchain1);
    assertThat(barCtrt.flag().send().longValue()).isEqualTo(264);
  }

  /*
   * Currently failing due to an issue with Web3j/codegen in encoding static arrays.
   */
  @Ignore
  public void doCCTxCall() throws Exception {
    CallSimulator sim = new CallSimulator();
    sim.barUpdateState();

    CrosschainContextGenerator ctxGenerator =
        new CrosschainContextGenerator(nodeOnBlockchain1.getChainId());
    CrosschainContext subordTxCtx =
        ctxGenerator.createCrosschainContext(
            nodeOnBlockchain1.getChainId(), barCtrt.getContractAddress());
    byte[] subordTrans =
        fooCtrt.updateState_AsSignedCrosschainSubordinateTransaction(
            sim.magicNumArr, sim.str, subordTxCtx);
    byte[][] subordTxAndViews = new byte[][] {subordTrans};
    CrosschainContext origTxCtx = ctxGenerator.createCrosschainContext(subordTxAndViews);

    TransactionReceipt txReceipt = barCtrt.barUpdateState_AsCrosschainTransaction(origTxCtx).send();
    if (!txReceipt.isStatusOK()) {
      LOG.info("txReceipt details " + txReceipt.toString());
      throw new Error(txReceipt.getStatus());
    }

    waitForUnlock(fooCtrt.getContractAddress(), this.nodeOnBlockchain2);
    assertThat(fooCtrt.fooFlag().send().longValue()).isEqualTo(261);
  }
}
