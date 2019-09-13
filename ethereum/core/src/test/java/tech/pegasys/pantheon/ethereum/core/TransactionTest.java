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
package tech.pegasys.pantheon.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.vm.ReferenceTestProtocolSchedules;
import tech.pegasys.pantheon.testutil.JsonTestParameters;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TransactionTest {

  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

  private static TransactionValidator transactionValidator(final String name) {
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
        // Blacklist tests that expect transactions with large gasLimits to properly decode
        .blacklist(
            "TransactionWithGasLimitOverflow(2|63)", "TransactionWithGasLimitxPriceOverflow$")
        // Nonce is tracked with type long, large valued nonces can't currently be decoded
        .blacklist("TransactionWithHighNonce256")
        .generator((name, spec, collector) -> collector.add(name, spec, true))
        .generate(TEST_CONFIG_FILE_DIR_PATH);
  }

  public TransactionTest(
      final String name, final TransactionTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
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
  public void constantinopleFix() {
    milestone("ConstantinopleFix");
  }

  public void milestone(final String milestone) {

    final TransactionTestCaseSpec.Expectation expected = spec.expectation(milestone);

    try {
      final BytesValue rlp = spec.getRlp();

      // Test transaction deserialization (will throw an exception if it fails).
      final Transaction transaction = Transaction.readFrom(RLP.input(rlp));
      if (!transactionValidator(milestone).validate(transaction).isValid()) {
        throw new RuntimeException(String.format("Transaction is invalid %s", transaction));
      }

      // Test rlp encoding
      final BytesValue actualRlp = RLP.encode(transaction::writeTo);
      assertThat(expected.isSucceeds()).isTrue();

      assertThat(actualRlp).isEqualTo(rlp);

      assertThat(transaction.getSender()).isEqualTo(expected.getSender());
      assertThat(transaction.hash()).isEqualTo(expected.getHash());
    } catch (final Exception e) {
      assertThat(expected.isSucceeds()).isFalse();
    }
  }
}
