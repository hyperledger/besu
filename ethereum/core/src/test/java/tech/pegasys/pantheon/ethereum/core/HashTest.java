package tech.pegasys.pantheon.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class HashTest {

  @Test
  public void shouldGetExpectedValueForEmptyHash() {
    assertThat(Hash.EMPTY)
        .isEqualTo(
            Hash.fromHexString("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"));
  }
}
