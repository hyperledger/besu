package tech.pegasys.pantheon.util;

public class ExceptionUtils {

  private ExceptionUtils() {}

  /**
   * Returns the root cause of an exception
   *
   * @param throwable the throwable whose root cause we want to find
   * @return The root cause
   */
  public static Throwable rootCause(final Throwable throwable) {
    Throwable cause = throwable;

    while (cause != null) {
      if (cause.getCause() == null) {
        break;
      }

      cause = cause.getCause();
    }

    return cause;
  }
}
