package org.hyperledger.besu.datatypes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GWeiTest {

  @Test
  void getAsWei() {
    final GWei oneGwei = GWei.of(1);
    final Wei wei = oneGwei.getAsWei();
    assertThat(wei.getAsBigInteger()).isEqualTo(1_000_000_000L);
  }
}
