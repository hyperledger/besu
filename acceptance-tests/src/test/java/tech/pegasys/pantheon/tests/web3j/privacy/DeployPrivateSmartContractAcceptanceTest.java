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
package tech.pegasys.pantheon.tests.web3j.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes.PrivateTransactionReceipt;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class DeployPrivateSmartContractAcceptanceTest extends AcceptanceTestBase {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  // Contract address is generated from sender address and transaction nonce
  private static final Address CONTRACT_ADDRESS =
      Address.fromHexString("0x42699a7612a82f1d9c36148af9c77354759b210b");

  private static final Address SENDER =
      Address.fromHexString(
          Credentials.create("8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63")
              .getAddress());

  private static final PrivateTransaction DEPLOY_CONTRACT =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f60003960"
                      + "00f3fe60806040526004361060485763ffffffff7c01000000"
                      + "00000000000000000000000000000000000000000000000000"
                      + "60003504166360fe47b18114604d5780636d4ce63c14607557"
                      + "5b600080fd5b348015605857600080fd5b5060736004803603"
                      + "6020811015606d57600080fd5b50356099565b005b34801560"
                      + "8057600080fd5b506087609e565b6040805191825251908190"
                      + "0360200190f35b600055565b6000549056fea165627a7a7230"
                      + "5820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6"
                      + "daa4f6b2f003d1b0180029"))
          .sender(SENDER)
          .chainId(2018)
          .privateFrom(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))))
          .restriction(BytesValue.wrap("unrestricted".getBytes(UTF_8)))
          .signAndBuild(
              SECP256K1.KeyPair.create(
                  SECP256K1.PrivateKey.create(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
                          16))));

  private static final PrivateTransaction FUNCTION_CALL =
      PrivateTransaction.builder()
          .nonce(1)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(CONTRACT_ADDRESS)
          .value(Wei.ZERO)
          .payload(
              BytesValue.fromHexString(
                  "0xcccdda2cf2895862749f1c69aa9f55cf481ea82500e4eabb4e2578b36636979b"
                      + "0000000000000000000000000000000000000000000000000000000000000000"))
          .sender(SENDER)
          .chainId(2018)
          .privateFrom(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8))))
          .restriction(BytesValue.wrap("unrestricted".getBytes(UTF_8)))
          .signAndBuild(
              SECP256K1.KeyPair.create(
                  SECP256K1.PrivateKey.create(
                      new BigInteger(
                          "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
                          16))));

  private PantheonNode minerNode;

  private OrionTestHarness testHarness;

  @Before
  public void setUp() throws Exception {
    testHarness = OrionTestHarness.create(folder.newFolder().toPath());

    final PrivacyParameters privacyParameters = new PrivacyParameters();
    privacyParameters.setUrl(testHarness.clientUrl());
    privacyParameters.setPrivacyAddress(Address.PRIVACY);
    privacyParameters.setPublicKeyUsingFile(testHarness.getConfig().publicKeys().get(0).toFile());
    privacyParameters.enablePrivateDB(folder.newFolder("private").toPath());

    minerNode = pantheon.createPrivateTransactionEnabledMinerNode("miner-node", privacyParameters);
    cluster.start(minerNode);
  }

  @After
  public void tearDownOnce() {
    testHarness.getOrion().stop();
  }

  @Test
  public void deployingMustGiveValidReceipt() {

    final String signedRawDeployTransaction = toRlp(DEPLOY_CONTRACT);

    final String transactionHash =
        minerNode.execute(transactions.createPrivateRawTransaction(signedRawDeployTransaction));

    minerNode.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode, 2));

    waitFor(() -> minerNode.verify(eth.expectSuccessfulTransactionReceipt(transactionHash)));

    TransactionReceipt txReceipt =
        minerNode.execute(transactions.getTransactionReceipt(transactionHash)).get();

    assertEquals(Address.DEFAULT_PRIVACY.toString(), txReceipt.getTo());

    PrivateTransactionReceipt privateTxReceipt =
        minerNode.execute(
            transactions.getPrivateTransactionReceipt(
                transactionHash, "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));

    assertEquals(CONTRACT_ADDRESS.toString(), privateTxReceipt.getContractAddress());

    final String signedRawFunctionTransaction = toRlp(FUNCTION_CALL);

    final String transactionHash1 =
        minerNode.execute(transactions.createPrivateRawTransaction(signedRawFunctionTransaction));

    minerNode.waitUntil(wait.chainHeadHasProgressedByAtLeast(minerNode, 2));

    waitFor(() -> minerNode.verify(eth.expectSuccessfulTransactionReceipt(transactionHash1)));

    TransactionReceipt txReceipt2 =
        minerNode.execute(transactions.getTransactionReceipt(transactionHash)).get();

    // TODO: fire function call from minerNode and from a non-privy node

  }

  private String toRlp(final PrivateTransaction transaction) {
    BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
    transaction.writeTo(bvrlpo);
    return bvrlpo.encoded().toString();
  }
}
