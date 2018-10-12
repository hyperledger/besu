package tech.pegasys.pantheon.util;

import java.util.function.Function;

public class Preconditions {
  private Preconditions() {}

  public static void checkGuard(
      final boolean condition,
      final Function<String, ? extends RuntimeException> exceptionGenerator,
      final String template,
      final Object... variables) {
    if (!condition) {
      throw exceptionGenerator.apply(String.format(template, variables));
    }
  }
}
