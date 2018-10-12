package tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
