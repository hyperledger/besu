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

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateContractVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaTransactions;

import java.io.IOException;
import java.nio.charset.Charset;

import com.google.common.io.Resources;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivateAcceptanceTestBase extends AcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final Eea eea;
  final PrivateContractVerifier privateContractVerifier;

  PrivateAcceptanceTestBase() {
    final EeaTransactions eeaTransactions = new EeaTransactions();
    eea = new Eea(eeaTransactions);
    privateContractVerifier = new PrivateContractVerifier(eea, transactions);
  }

  static OrionTestHarness createEnclave(
      final String pubKey, final String privKey, final String... othernode) throws Exception {
    return OrionTestHarnessFactory.create(privacy.newFolder().toPath(), pubKey, privKey, othernode);
  }

  String getDeploySimpleStorage() throws IOException {
    return loadRawTransaction("privacy/single-instance/deployPrivateSmartContractRLP.txt");
  }

  String getExecuteStoreFunc() throws IOException {
    return loadRawTransaction("privacy/single-instance/executeStoreFuncRLP.txt");
  }

  String getExecuteGetFunc() throws IOException {
    return loadRawTransaction("privacy/single-instance/executeGetFuncRLP.txt");
  }

  static PrivacyParameters getPrivacyParams(final OrionTestHarness testHarness) throws IOException {
    final PrivacyParameters privacyParameters = new PrivacyParameters();
    privacyParameters.setEnabled(true);
    privacyParameters.setUrl(testHarness.clientUrl());
    privacyParameters.setPrivacyAddress(Address.PRIVACY);
    privacyParameters.setPublicKeyUsingFile(testHarness.getConfig().publicKeys().get(0).toFile());
    privacyParameters.enablePrivateDB(privacy.newFolder("private").toPath());
    return privacyParameters;
  }

  private String loadRawTransaction(final String path) throws IOException {
    return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
  }
}
