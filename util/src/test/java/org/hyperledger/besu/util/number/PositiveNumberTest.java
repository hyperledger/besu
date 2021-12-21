package org.hyperledger.besu.util.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class PositiveNumberTest {

  @Test
  public void shouldBuildPositiveNumberFromString() {
    final String positiveNumberString = "1";
    final int positiveNumberInt = 1;
    PositiveNumber positiveNumber = PositiveNumber.fromString(positiveNumberString);
    assertThat(positiveNumber).isNotNull();
    assertThat(positiveNumber.getValue()).isEqualTo(positiveNumberInt);
  }

  @Test
  public void shouldThrowOnDecimalValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("0.1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNonPositiveValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("0"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNegativeValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString("-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnEmptyValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnNullValueFromString() {
    assertThatThrownBy(() -> PositiveNumber.fromString(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
