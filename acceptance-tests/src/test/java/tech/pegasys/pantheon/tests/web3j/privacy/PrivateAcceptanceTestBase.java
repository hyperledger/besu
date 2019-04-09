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
import java.util.List;

import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivateAcceptanceTestBase extends AcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final Eea eea;
  protected static PrivateTransactionFactory privateTx;
  protected final PrivateTransactionVerifier privateTransactionVerifier;

  PrivateAcceptanceTestBase() {
    final EeaTransactions eeaTransactions = new EeaTransactions();
    eea = new Eea(eeaTransactions);
    privateTx = new PrivateTransactionFactory();
    privateTransactionVerifier = new PrivateTransactionVerifier(eea, transactions);
  }

  public static PrivateAcceptanceTestBase.Builder builder() {
    return new PrivateAcceptanceTestBase.Builder();
  }

  static OrionTestHarness createEnclave(
      final String pubKey, final String privKey, final String... othernode) throws Exception {
    return OrionTestHarnessFactory.create(privacy.newFolder().toPath(), pubKey, privKey, othernode);
  }

  enum TransactionType {
    CREATE_CONTRACT,
    STORE,
    GET
  }

  public static class Builder {
    long nonce;
    Address from;
    Address to;
    BytesValue privateFrom;
    List<BytesValue> privateFor;
    SECP256K1.KeyPair keyPair;

    public Builder nonce(final long nonce) {
      this.nonce = nonce;
      return this;
    }

    public Builder from(final Address from) {
      this.from = from;
      return this;
    }

    public Builder to(final Address to) {
      this.to = to;
      return this;
    }

    public Builder privateFrom(final BytesValue privateFrom) {
      this.privateFrom = privateFrom;
      return this;
    }

    public Builder privateFor(final List<BytesValue> privateFor) {
      this.privateFor = privateFor;
      return this;
    }

    public Builder keyPair(final SECP256K1.KeyPair keyPair) {
      this.keyPair = keyPair;
      return this;
    }

    public String build(final TransactionType type) {
      PrivateTransaction pTx;
      switch (type) {
        case CREATE_CONTRACT:
          pTx = privateTx.createContractTransaction(nonce, from, privateFrom, privateFor, keyPair);
          break;
        case STORE:
          pTx =
              privateTx.storeFunctionTransaction(nonce, to, from, privateFrom, privateFor, keyPair);
          break;
        case GET:
          pTx = privateTx.getFunctionTransaction(nonce, to, from, privateFrom, privateFor, keyPair);
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + type);
      }
      return RLP.encode(pTx::writeTo).toString();
    }
  }

  static PrivacyParameters getPrivacyParams(final OrionTestHarness testHarness) throws IOException {
    return new PrivacyParameters.Builder()
        .setEnabled(true)
        .setEnclaveUrl(testHarness.clientUrl())
        .setEnclavePublicKeyUsingFile(testHarness.getConfig().publicKeys().get(0).toFile())
        .setDataDir(privacy.newFolder().toPath())
        .build();
  }
}
