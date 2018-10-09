package net.consensys.pantheon.tests.acceptance.dsl;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

public class WaitUtils {
  public static void waitFor(final ThrowingRunnable condition) {
    Awaitility.await().ignoreExceptions().atMost(30, TimeUnit.SECONDS).untilAsserted(condition);
  }
}
