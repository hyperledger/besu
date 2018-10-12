package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.util.uint.UInt256;

import org.junit.Test;

public class ConstantFieldValidationRuleTest {

  @Test
  public void ommersFieldValidatesCorrectly() {

    final ConstantFieldValidationRule<Hash> uut =
        new ConstantFieldValidationRule<>(
            "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH);

    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.ommersHash(Hash.EMPTY_LIST_HASH);
    BlockHeader header = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isTrue();

    blockHeaderBuilder.ommersHash(Hash.ZERO);
    header = blockHeaderBuilder.buildHeader();
    assertThat(uut.validate(header, null)).isFalse();
  }

  @Test
  public void difficultyFieldIsValidatedCorrectly() {
    final ConstantFieldValidationRule<UInt256> uut =
        new ConstantFieldValidationRule<>("Difficulty", BlockHeader::getDifficulty, UInt256.ONE);

    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();
    blockHeaderBuilder.difficulty(UInt256.ONE);
    BlockHeader header = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, null)).isTrue();

    blockHeaderBuilder.difficulty(UInt256.ZERO);
    header = blockHeaderBuilder.buildHeader();
    assertThat(uut.validate(header, null)).isFalse();
  }
}
