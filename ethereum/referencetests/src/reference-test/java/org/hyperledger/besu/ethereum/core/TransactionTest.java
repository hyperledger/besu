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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.HomesteadGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
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

  private final String name;
  private final TransactionTestCaseSpec spec;

  private static final String TEST_CONFIG_FILE_DIR_PATH = "TransactionTests/";

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(TransactionTestCaseSpec.class)
        .generator((name, spec, collector) -> collector.add(name, spec, true))
        .generate(TEST_CONFIG_FILE_DIR_PATH);
  }

  public TransactionTest(
      final String name, final TransactionTestCaseSpec spec, final boolean runTest) {
    this.name = name;
    this.spec = spec;
    assumeTrue("Test " + name + " was ignored", runTest);
  }

  @Test
  public void frontier() {
    milestone("Frontier", new FrontierGasCalculator(), Optional.empty());
  }

  @Test
  public void homestead() {
    milestone("Homestead", new HomesteadGasCalculator(), Optional.empty());
  }

  @Test
  public void eIP150() {
    milestone("EIP150", new TangerineWhistleGasCalculator(), Optional.empty());
  }

  @Test
  public void eIP158() {
    milestone("EIP158", new SpuriousDragonGasCalculator(), Optional.empty());
  }

  @Test
  public void byzantium() {
    milestone("Byzantium", new ByzantiumGasCalculator(), Optional.empty());
  }

  @Test
  public void constantinople() {
    milestone("Constantinople", new ConstantinopleGasCalculator(), Optional.empty());
  }

  @Test
  public void petersburg() {
    milestone("ConstantinopleFix", new PetersburgGasCalculator(), Optional.empty());
  }

  @Test
  public void istanbul() {
    milestone("Istanbul", new IstanbulGasCalculator(), Optional.empty());
  }

  @Test
  public void berlin() {
    milestone("Berlin", new BerlinGasCalculator(), Optional.empty());
  }

  @Test
  public void london() {
    milestone("London", new LondonGasCalculator(), Optional.of(Wei.of(0)));
  }

  public void milestone(
      final String milestone, final GasCalculator gasCalculator, final Optional<Wei> baseFee) {

    final TransactionTestCaseSpec.Expectation expected = spec.expectation(milestone);

    try {
      Bytes rlp = spec.getRlp();

      // non-frontier transactions need to be opaque for parsing to work
      if (rlp.get(0) > 0) {
        final BytesValueRLPOutput output = new BytesValueRLPOutput();
        output.writeBytes(rlp);
        rlp = output.encoded();
      }

      // Test transaction deserialization (will throw an exception if it fails).
      final Transaction transaction = Transaction.readFrom(RLP.input(rlp));
      final ValidationResult<TransactionInvalidReason> validation =
          transactionValidator(milestone)
              .validate(transaction, baseFee, TransactionValidationParams.processingBlock());
      if (!validation.isValid()) {
        throw new RuntimeException(
            String.format(
                "Transaction is invalid %s - %s", validation.getInvalidReason(), transaction));
      }

      // Test rlp encoding
      final Bytes actualRlp = RLP.encode(transaction::writeTo);
      assertThat(expected.isSucceeds())
          .withFailMessage("Transaction " + name + "/" + milestone + " was supposed to be invalid")
          .isTrue();

      assertThat(actualRlp).isEqualTo(rlp);

      assertThat(transaction.getSender()).isEqualTo(expected.getSender());
      assertThat(transaction.getHash()).isEqualTo(expected.getHash());
      final long intrinsicGasCost =
          gasCalculator.transactionIntrinsicGasCost(
                  transaction.getPayload(), transaction.isContractCreation())
              + (transaction.getAccessList().map(gasCalculator::accessListGasCost).orElse(0L));
      assertThat(intrinsicGasCost).isEqualTo(expected.getIntrinsicGas());
    } catch (final Exception e) {
      if (expected.isSucceeds()) {
        throw e;
      }
    }
  }
}
