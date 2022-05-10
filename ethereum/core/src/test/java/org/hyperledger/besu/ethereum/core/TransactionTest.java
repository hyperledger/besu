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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TransactionTest {

  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

  private static MainnetTransactionValidator transactionValidator(final String name) {
    return REFERENCE_TEST_PROTOCOL_SCHEDULES
        .getByName(name)
        .getByBlockNumber(0)
        .getTransactionValidator();
  }

  private final TransactionTestCaseSpec spec;

  private static final String TEST_CONFIG_FILE_DIR_PATH = "TransactionTests/";

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(TransactionTestCaseSpec.class)
        // ignore tests that expect transactions with large gasLimits to properly decode
        .ignore("TransactionWithGasLimitOverflow(2|63)", "TransactionWithGasLimitxPriceOverflow$")
        // The test contains a nonce with 256 bits, which is longer than the spec allows,
        // but incorrectly is specified as a successful test
        .ignore("TransactionWithHighNonce256")
        .generator((name, spec, collector) -> collector.add(name, spec, true))
        .generate(TEST_CONFIG_FILE_DIR_PATH);
  }

  public TransactionTest(
      final String name, final TransactionTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test " + name + " was ignored", runTest);
  }

  @Test
  public void frontier() {
    milestone("Frontier");
  }

  @Test
  public void homestead() {
    milestone("Homestead");
  }

  @Test
  public void eIP150() {
    milestone("EIP150");
  }

  @Test
  public void eIP158() {
    milestone("EIP158");
  }

  @Test
  public void byzantium() {
    milestone("Byzantium");
  }

  @Test
  public void constantinople() {
    milestone("Constantinople");
  }

  @Test
  public void petersburg() {
    milestone("ConstantinopleFix");
  }

  public void milestone(final String milestone) {

    final TransactionTestCaseSpec.Expectation expected = spec.expectation(milestone);

    try {
      final Bytes rlp = spec.getRlp();

      // Test transaction deserialization (will throw an exception if it fails).
      final Transaction transaction = Transaction.readFrom(RLP.input(rlp));
      if (!transactionValidator(milestone)
          .validate(transaction, Optional.empty(), TransactionValidationParams.processingBlock())
          .isValid()) {
        throw new RuntimeException(String.format("Transaction is invalid %s", transaction));
      }

      // Test rlp encoding
      final Bytes actualRlp = RLP.encode(transaction::writeTo);
      assertThat(expected.isSucceeds()).isTrue();

      assertThat(actualRlp).isEqualTo(rlp);

      assertThat(transaction.getSender()).isEqualTo(expected.getSender());
      assertThat(transaction.getHash()).isEqualTo(expected.getHash());
    } catch (final Exception e) {
      assertThat(expected.isSucceeds()).isFalse();
    }
  }
}
