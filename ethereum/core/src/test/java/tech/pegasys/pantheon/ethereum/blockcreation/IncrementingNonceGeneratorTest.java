package tech.pegasys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class IncrementingNonceGeneratorTest {

  @Test
  public void firstValueProvidedIsSuppliedAtConstruction() {
    final Long initialValue = 0L;
    final IncrementingNonceGenerator generator = new IncrementingNonceGenerator(initialValue);

    assertThat(generator.iterator().next()).isEqualTo(initialValue);
  }

  @Test
  public void rollOverFromMaxResetsToZero() {
    final Long initialValue = 0xFFFFFFFFFFFFFFFFL;
    final IncrementingNonceGenerator generator = new IncrementingNonceGenerator(initialValue);

    assertThat(generator.iterator().next()).isEqualTo(initialValue);
    final Long nextValue = generator.iterator().next();
    assertThat(Long.compareUnsigned(nextValue, 0)).isEqualTo(0);
  }
}
