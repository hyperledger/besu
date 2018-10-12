package tech.pegasys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.ethereum.ProtocolContext;

import org.junit.Test;

/** Tests for {@link MainnetBlockHeaderValidator}. */
public final class MainnetBlockHeaderValidatorTest {

  @SuppressWarnings("unchecked")
  private final ProtocolContext<Void> protocolContext = mock(ProtocolContext.class);

  @Test
  public void validHeaderFrontier() throws Exception {
    final BlockHeaderValidator<Void> headerValidator =
        MainnetBlockHeaderValidator.create(MainnetDifficultyCalculators.FRONTIER);
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(300006),
                ValidationTestUtils.readHeader(300005),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void validHeaderHomestead() throws Exception {
    final BlockHeaderValidator<Void> headerValidator =
        MainnetBlockHeaderValidator.create(MainnetDifficultyCalculators.HOMESTEAD);
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(1200001),
                ValidationTestUtils.readHeader(1200000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void invalidParentHash() throws Exception {
    final BlockHeaderValidator<Void> headerValidator =
        MainnetBlockHeaderValidator.create(MainnetDifficultyCalculators.HOMESTEAD);
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(1200001),
                ValidationTestUtils.readHeader(4400000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void validHeaderByzantium() throws Exception {
    final BlockHeaderValidator<Void> headerValidator =
        MainnetBlockHeaderValidator.create(MainnetDifficultyCalculators.BYZANTIUM);
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(4400001),
                ValidationTestUtils.readHeader(4400000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }
}
