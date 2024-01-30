package org.hyperledger.besu.util.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

public class LogUtilTest {

  static final String exceptionMessage = "test message";
  static final String contextMessage = "context message";

  @Test
  public void testMessageSummarizeStackTrace() {
    Throwable throwable = new Throwable(exceptionMessage);
    String namespace = "org.hyperledger.besu";
    String result = LogUtil.summarizeStackTrace(contextMessage, throwable, namespace);
    var lines =
        assertFoundInTrace(result, LogUtil.BESU_NAMESPACE, contextMessage, exceptionMessage, 1);
    assertThat(lines.size()).isEqualTo(3);
  }

  @Test
  public void testSummarizeStackTraceToArbitrary() {
    Throwable throwable = new Throwable("test message");
    String namespace = "java.lang";
    String result = LogUtil.summarizeStackTrace(contextMessage, throwable, namespace);
    var lines = assertFoundInTrace(result, namespace, contextMessage, exceptionMessage, 1);
    assertThat(lines.size()).isGreaterThan(3);
  }

  @Test
  public void testSummarizeLambdaStackTrace() {
    String result = "";
    try {
      Supplier<Object> lambda = () -> besuStackHelper(null);
      assertThat(lambda.get()).isNotNull();
    } catch (Exception e) {
      String namespace = "org.hyperledger.besu";
      result = LogUtil.summarizeStackTrace(contextMessage, e, namespace);
    }
    var lines =
        assertFoundInTrace(
            result, LogUtil.BESU_NAMESPACE, contextMessage, "NullPointerException", 1);
    assertThat(lines).hasSize(5);
  }

  @Test
  public void assertSummarizeStopsAtFirstBesu() {
    String result = "";
    try {
      Supplier<Object> lambda = () -> besuStackHelper((null));
      Supplier<Object> lambda2 = lambda::get;
      assertThat(lambda2.get()).isNotNull();
    } catch (Exception e) {
      result = LogUtil.summarizeBesuStackTrace("besuSummary", e);
    }
    var lines =
        assertFoundInTrace(
            result, LogUtil.BESU_NAMESPACE, "besuSummary", "NullPointerException", 1);
    assertThat(lines).hasSize(5);
    assertThat(lines.get(0)).contains("besuSummary");
  }

  private List<String> assertFoundInTrace(
      final String result,
      final String namespace,
      final String ctxMessage,
      final String excMessage,
      final int times) {
    System.out.println(result);
    assertThat(result).containsOnlyOnce(ctxMessage);
    assertThat(result).containsOnlyOnce(excMessage);

    List<String> lines = Arrays.asList(result.split("\n"));
    assertThat(
            lines.stream()
                .filter(s -> s.contains("at: "))
                .filter(s -> s.contains(namespace))
                .count())
        .isEqualTo(times);
    return lines;
  }

  private Optional<Object> besuStackHelper(final Object o) {
    return Optional.of(o);
  }
}
