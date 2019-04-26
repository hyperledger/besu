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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder;

import java.io.IOException;

import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivateAcceptanceTestBase extends AcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final Eea eea;
  protected final PrivateTransactions privateTransactions;
  protected static PrivateTransactionBuilder.Builder privateTransactionBuilder;
  protected final PrivateTransactionVerifier privateTransactionVerifier;

  public PrivateAcceptanceTestBase() {
    final EeaTransactions eeaTransactions = new EeaTransactions();

    privateTransactions = new PrivateTransactions();
    eea = new Eea(eeaTransactions);
    privateTransactionBuilder = PrivateTransactionBuilder.builder();
    privateTransactionVerifier = new PrivateTransactionVerifier(eea, transactions);
  }

  protected static OrionTestHarness createEnclave(
      final String pubKey, final String privKey, final String... othernode) throws Exception {
    return OrionTestHarnessFactory.create(privacy.newFolder().toPath(), pubKey, privKey, othernode);
  }

  protected static PrivacyParameters getPrivacyParameters(final OrionTestHarness testHarness)
      throws IOException {
    return new PrivacyParameters.Builder()
        .setEnabled(true)
        .setEnclaveUrl(testHarness.clientUrl())
        .setEnclavePublicKeyUsingFile(testHarness.getConfig().publicKeys().get(0).toFile())
        .setDataDir(privacy.newFolder().toPath())
        .build();
  }
}
