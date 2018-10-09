package net.consensys.pantheon.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

public class ValidationResultTest {

  private final Runnable action = mock(Runnable.class);

  @Test
  public void validResulthouldBeValid() {
    assertThat(ValidationResult.valid().isValid()).isTrue();
  }

  @Test
  public void invalidResultsShouldBeInvalid() {
    assertThat(ValidationResult.invalid("foo").isValid()).isFalse();
  }

  @Test
  public void shouldRunIfValidActionWhenValid() {
    ValidationResult.valid().ifValid(action);

    verify(action).run();
  }

  @Test
  public void shouldNotRunIfValidActionWhenInvalid() {
    ValidationResult.invalid("foo").ifValid(action);

    verifyZeroInteractions(action);
  }

  @Test
  public void eitherShouldReturnWhenValidSupplierWhenValid() {
    assertThat(
            ValidationResult.valid()
                .either(
                    () -> Boolean.TRUE,
                    error -> {
                      throw new IllegalStateException(
                          "Should not have executed whenInvalid function");
                    }))
        .isTrue();
  }

  @Test
  public void eitherShouldUseWhenInvalidFunctionWhenInvalid() {
    final ValidationResult<String> result = ValidationResult.invalid("foo");
    assertThat(
            result.<Boolean>either(
                () -> {
                  throw new IllegalStateException("Should not have executed whenInvalid function");
                },
                error -> Boolean.TRUE))
        .isTrue();
  }
}
