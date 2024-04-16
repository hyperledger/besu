package org.hyperledger.besu.ethereum.mainnet;

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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;

public class ValidatorExitsValidatorTestFixtures {

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  static ValidateExitTestParameter blockWithExitsAndExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setExitsRoot(BodyValidation.exitsRoot(maybeExits.get()))
            .setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block with exits and exits_root", block, maybeExits);
  }

  static ValidateExitTestParameter blockWithoutExitsWithExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.empty();

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExitsRoot(Hash.EMPTY).setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter(
        "Block with exits_root but without exits", block, maybeExits);
  }

  static ValidateExitTestParameter blockWithExitsWithoutExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter(
        "Block with exits but without exits_root", block, maybeExits);
  }

  static ValidateExitTestParameter blockWithoutExitsAndExitsRoot() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.empty();

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block without exits and exits_root", block, maybeExits);
  }

  static ValidateExitTestParameter blockWithExitsRootMismatch() {
    final Optional<List<ValidatorExit>> maybeExits = Optional.of(List.of(createExit()));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setExitsRoot(Hash.EMPTY).setExits(maybeExits);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ValidateExitTestParameter("Block with exits_root mismatch", block, maybeExits);
  }

  static ValidateExitTestParameter blockWithExitsMismatch() {
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

  static ValidateExitTestParameter blockWithMoreThanMaximumExits() {
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

  static ValidatorExit createExit() {
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
