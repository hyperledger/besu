package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsAndExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsMismatch;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsRootMismatch;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithExitsWithoutExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithMoreThanMaximumExits;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithoutExitsAndExitsRoot;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.blockWithoutExitsWithExitsRoot;

import org.hyperledger.besu.ethereum.core.ValidatorExit;
import org.hyperledger.besu.ethereum.mainnet.ValidatorExitsValidatorTestFixtures.ValidateExitTestParameter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PragueValidatorExitsValidatorTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateValidatorExitParameter")
  public void validateValidatorExitParameter(
      final String description,
      final Optional<List<ValidatorExit>> maybeExits,
      final boolean expectedValidity) {
    assertThat(new PragueValidatorExitsValidator().validateValidatorExitParameter(maybeExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateValidatorExitParameter() {
    return Stream.of(
        Arguments.of("Allowed exits - validating empty exits", Optional.empty(), false),
        Arguments.of("Allowed exits - validating present exits", Optional.of(List.of()), true));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateExitsInBlockParamsForPrague")
  public void validateExitsInBlock_WhenPrague(
      final ValidateExitTestParameter param, final boolean expectedValidity) {
    assertThat(
            new PragueValidatorExitsValidator()
                .validateExitsInBlock(param.block, param.expectedExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateExitsInBlockParamsForPrague() {
    return Stream.of(
        Arguments.of(blockWithExitsAndExitsRoot(), true),
        Arguments.of(blockWithExitsWithoutExitsRoot(), false),
        Arguments.of(blockWithoutExitsWithExitsRoot(), false),
        Arguments.of(blockWithoutExitsAndExitsRoot(), false),
        Arguments.of(blockWithExitsRootMismatch(), false),
        Arguments.of(blockWithExitsMismatch(), false),
        Arguments.of(blockWithMoreThanMaximumExits(), false));
  }

  @Test
  public void allowExitsShouldReturnTrue() {
    assertThat(new PragueValidatorExitsValidator().allowValidatorExits()).isTrue();
  }
}
