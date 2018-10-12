package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class ExtraDataMaxLengthValidationRuleTest {

  @Test
  public void sufficientlySmallExtraDataBlockValidateSuccessfully() {
    final ExtraDataMaxLengthValidationRule uut = new ExtraDataMaxLengthValidationRule(1);
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.extraData(BytesValue.wrap(new byte[1]));

    final BlockHeader header = builder.buildHeader();

    // Note: The parentHeader is not required for this validator.
    assertThat(uut.validate(header, null)).isTrue();
  }

  @Test
  public void tooLargeExtraDataCausesValidationFailure() {
    final ExtraDataMaxLengthValidationRule uut = new ExtraDataMaxLengthValidationRule(1);
    final BlockHeaderTestFixture builder = new BlockHeaderTestFixture();
    builder.extraData(BytesValue.wrap(new byte[2]));

    final BlockHeader header = builder.buildHeader();

    // Note: The parentHeader is not required for this validator.
    assertThat(uut.validate(header, null)).isFalse();
  }
}
