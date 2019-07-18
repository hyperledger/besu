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
package tech.pegasys.pantheon.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;

import org.junit.Before;
import org.junit.Test;

public class PrivateTransactionValidatorTest {

  private PrivateTransactionValidator validator;

  @Before
  public void before() {
    validator = new PrivateTransactionValidator();
  }

  @Test
  public void transactionWithNonceLowerThanAccountNonceShouldReturnLowNonceError() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 2L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void transactionWithNonceGreaterThanAccountNonceShouldReturnIncorrectNonceError() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(3L), 2L);

    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  @Test
  public void transactionWithNonceMatchingThanAccountNonceShouldReturnValidTransactionResult() {
    ValidationResult<TransactionInvalidReason> validationResult =
        validator.validate(privateTransactionWithNonce(1L), 1L);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
  }

  private static PrivateTransaction privateTransactionWithNonce(final long nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
        .value(Wei.ZERO)
        .payload(BytesValue.fromHexString("0x"))
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(2018))
        .restriction(Restriction.RESTRICTED)
        .build();
  }
}
