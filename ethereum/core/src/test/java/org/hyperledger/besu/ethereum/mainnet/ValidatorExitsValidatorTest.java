package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.ValidatorExitContractHelper.MAX_EXITS_PER_BLOCK;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.ValidatorExit;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ValidatorExitsValidatorTest {

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("paramsForValidateValidatorExitParameter")
  public void validateValidatorExitParameter(
      final String description,
      final ValidatorExitsValidator validator,
      final Optional<List<ValidatorExit>> maybeExits,
      final boolean expectedValidity) {
    assertThat(validator.validateValidatorExitParameter(maybeExits)).isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> paramsForValidateValidatorExitParameter() {
    final ValidatorExitsValidator.ProhibitedExits prohibitedExitsValidator =
        new ValidatorExitsValidator.ProhibitedExits();
    final ValidatorExitsValidator.AllowedExits allowedExitsValidator =
        new ValidatorExitsValidator.AllowedExits();

    return Stream.of(
        Arguments.of(
            "Prohibited exits - validating empty exits",
            prohibitedExitsValidator,
            Optional.empty(),
            true),
        Arguments.of(
            "Prohibited exits - validating present exits",
            prohibitedExitsValidator,
            Optional.of(List.of()),
            false),
        Arguments.of(
            "Allowed exits - validating empty exits",
            allowedExitsValidator,
            Optional.empty(),
            false),
        Arguments.of(
            "Allowed exits - validating present exits",
            allowedExitsValidator,
            Optional.of(List.of()),
            true));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateExitsInBlockParamsForProhibited")
  public void validateExitsInBlock_WhenProhibited(
      final ValidateExitTestParameter param, final boolean expectedValidity) {
    assertThat(
            new ValidatorExitsValidator.ProhibitedExits()
                .validateExitsInBlock(param.block, param.expectedExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateExitsInBlockParamsForProhibited() {
    return Stream.of(
        Arguments.of(blockWithExitsAndExitsRoot(), false),
        Arguments.of(blockWithExitsWithoutExitsRoot(), false),
        Arguments.of(blockWithoutExitsWithExitsRoot(), false),
        Arguments.of(blockWithoutExitsAndExitsRoot(), true));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validateExitsInBlockParamsForAllowed")
  public void validateExitsInBlock_WhenAllowed(
      final ValidateExitTestParameter param, final boolean expectedValidity) {
    assertThat(
            new ValidatorExitsValidator.AllowedExits()
                .validateExitsInBlock(param.block, param.expectedExits))
        .isEqualTo(expectedValidity);
  }

  private static Stream<Arguments> validateExitsInBlockParamsForAllowed() {
    return Stream.of(
        Arguments.of(blockWithExitsAndExitsRoot(), true),
        Arguments.of(blockWithExitsWithoutExitsRoot(), false),
        Arguments.of(blockWithoutExitsWithExitsRoot(), false),
        Arguments.of(blockWithoutExitsAndExitsRoot(), false),
        Arguments.of(blockWithExitsRootMismatch(), false),
        Arguments.of(blockWithExitsMismatch(), false),
        Arguments.of(blockWithMoreThanMaximumExits(), false));
  }

  private static ValidateExitTestParameter blockWithExitsAndExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setExitsRoot(BodyValidation.exitsRoot(maybeExits.get()))
            .setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block with exits and exits_root", block, maybeExits);
  }

  private static ValidateExitTestParameter blockWithoutExitsWithExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.empty();

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExitsRoot(Hash.EMPTY).setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter(
        "Block with exits_root but without exits", block, maybeExits);
  }

  private static ValidateExitTestParameter blockWithExitsWithoutExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter(
        "Block with exits but without exits_root", block, maybeExits);
  }

  private static ValidateExitTestParameter blockWithoutExitsAndExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.empty();

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block without exits and exits_root", block, maybeExits);
  }

  private static ValidateExitTestParameter blockWithExitsRootMismatch() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExitsRoot(Hash.EMPTY).setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block with exits_root mismatch", block, maybeExits);
  }

  private static ValidateExitTestParameter blockWithExitsMismatch() {
    final Optional<List<ValidatorExit>> maybeExits =
        Optional.of(List.of(createExit(), createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setExitsRoot(BodyValidation.exitsRoot(maybeExits.get()))
            .setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter(
        "Block with exits mismatch", block, maybeExits, List.of(createExit()));
  }

  private static ValidateExitTestParameter blockWithMoreThanMaximumExits() {
    final List<ValidatorExit> validatorExits =
        IntStream.range(0, MAX_EXITS_PER_BLOCK + 1).mapToObj(__ -> createExit()).toList();
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(validatorExits);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setExitsRoot(BodyValidation.exitsRoot(maybeExits.get()))
            .setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block with more than maximum exits", block, maybeExits);
  }

  private static ValidatorExit createExit() {
    return new ValidatorExit(
        Address.extract(Bytes32.random()), BLSPublicKey.wrap(Bytes48.random()));
  }

  static class ValidateExitTestParameter {

    String description;
    Block block;
    Optional<List<ValidatorExit>> maybeExits;
    List<ValidatorExit> expectedExits;

    public ValidateExitTestParameter(
        final String description,
        final Block block,
        final Optional<List<ValidatorExit>> maybeExits) {
      this(description, block, maybeExits, maybeExits.orElseGet(List::of));
    }

    public ValidateExitTestParameter(
        final String description,
        final Block block,
        final Optional<List<ValidatorExit>> maybeExits,
        final List<ValidatorExit> expectedExits) {
      this.description = description;
      this.block = block;
      this.maybeExits = maybeExits;
      this.expectedExits = expectedExits;
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
