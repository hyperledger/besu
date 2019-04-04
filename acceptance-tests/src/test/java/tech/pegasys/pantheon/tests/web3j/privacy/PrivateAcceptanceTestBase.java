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

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateTransactionVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionFactory;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivateAcceptanceTestBase extends AcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final Eea eea;
  protected final PrivateTransactionFactory privateTx;
  protected final PrivateTransactionVerifier privateTransactionVerifier;

  PrivateAcceptanceTestBase() {
    final EeaTransactions eeaTransactions = new EeaTransactions();
    eea = new Eea(eeaTransactions);
    privateTx = new PrivateTransactionFactory();
    privateTransactionVerifier = new PrivateTransactionVerifier(eea, transactions);
  }

  static OrionTestHarness createEnclave(
      final String pubKey, final String privKey, final String... othernode) throws Exception {
    return OrionTestHarnessFactory.create(privacy.newFolder().toPath(), pubKey, privKey, othernode);
  }

  String getDeployEventEmitter() {
    Address from = Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    BytesValue privateFrom =
        BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
    PrivateTransaction pTx =
        privateTx.createContractTransaction(0, from, privateFrom, Lists.newArrayList(), keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getExecuteStoreFunc() {
    Address to = Address.fromHexString("0x99a3e1c0368cb56aeea8fc8cf5068175d0de7ac1");
    Address from = Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    BytesValue privateFrom =
        BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
    PrivateTransaction pTx =
        privateTx.storeFunctionTransaction(1, to, from, privateFrom, Lists.newArrayList(), keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getExecuteGetFunc() {
    Address to = Address.fromHexString("0x99a3e1c0368cb56aeea8fc8cf5068175d0de7ac1");
    Address from = Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    BytesValue privateFrom =
        BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
    PrivateTransaction pTx =
        privateTx.getFunctionTransaction(2, to, from, privateFrom, Lists.newArrayList(), keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getDeployEventEmitterCluster() {
    Address from = Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");
    BytesValue privateFrom =
        BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8));
    List<BytesValue> privateFor =
        Lists.newArrayList(
            BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8)));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
    PrivateTransaction pTx =
        privateTx.createContractTransaction(0, from, privateFrom, privateFor, keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getExecuteStoreFuncCluster(final long nonce) {
    Address to = Address.fromHexString("0x2f351161a80d74047316899342eedc606b13f9f8");
    Address from = Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
    BytesValue privateFrom =
        BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8));
    List<BytesValue> privateFor =
        Lists.newArrayList(
            BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3", 16)));
    PrivateTransaction pTx =
        privateTx.storeFunctionTransaction(nonce, to, from, privateFrom, privateFor, keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getExecuteGetFuncCluster(final long nonce) {
    Address to = Address.fromHexString("0x2f351161a80d74047316899342eedc606b13f9f8");
    Address from = Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
    BytesValue privateFrom =
        BytesValue.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=".getBytes(UTF_8));
    List<BytesValue> privateFor =
        Lists.newArrayList(
            BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3", 16)));
    PrivateTransaction pTx =
        privateTx.getFunctionTransaction(nonce, to, from, privateFrom, privateFor, keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  String getExecuteGetFuncClusterNode3() {
    Address to = Address.fromHexString("0x2f351161a80d74047316899342eedc606b13f9f8");
    Address from = Address.fromHexString("0xf17f52151EbEF6C7334FAD080c5704D77216b732");
    BytesValue privateFrom =
        BytesValue.wrap("k2zXEin4Ip/qBGlRkJejnGWdP9cjkK+DAvKNW31L2C8=".getBytes(UTF_8));
    List<BytesValue> privateFor =
        Lists.newArrayList(
            BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)));
    SECP256K1.KeyPair keypair =
        SECP256K1.KeyPair.create(
            SECP256K1.PrivateKey.create(
                new BigInteger(
                    "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f", 16)));
    PrivateTransaction pTx =
        privateTx.getFunctionTransaction(0, to, from, privateFrom, privateFor, keypair);
    return RLP.encode(pTx::writeTo).toString();
  }

  static PrivacyParameters getPrivacyParams(final OrionTestHarness testHarness) throws IOException {
    final PrivacyParameters privacyParameters = new PrivacyParameters();
    privacyParameters.setEnabled(true);
    privacyParameters.setUrl(testHarness.clientUrl());
    privacyParameters.setPrivacyAddress(Address.PRIVACY);
    privacyParameters.setEnclavePublicKeyUsingFile(
        testHarness.getConfig().publicKeys().get(0).toFile());
    privacyParameters.enablePrivateDB(privacy.newFolder().toPath());
    return privacyParameters;
  }
}
