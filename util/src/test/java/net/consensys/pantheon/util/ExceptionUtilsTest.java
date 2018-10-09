package net.consensys.pantheon.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;

import org.junit.Test;

public class ExceptionUtilsTest {

  @Test
  public void rootCauseReturnsTopLevelCause() {
    final Exception rootCause = new IllegalStateException("test");
    assertThat(ExceptionUtils.rootCause(rootCause)).isEqualTo(rootCause);
  }

  @Test
  public void rootCauseReturnsNestedCause() {
    final Exception rootCause = new IllegalStateException("test");
    final Throwable exception = new CompletionException(new CompletionException(rootCause));
    assertThat(ExceptionUtils.rootCause(exception)).isEqualTo(rootCause);
  }

  @Test
  public void rootCauseHandleNullInput() {
    final Exception rootCause = null;
    assertThat(ExceptionUtils.rootCause(rootCause)).isNull();
  }
}
