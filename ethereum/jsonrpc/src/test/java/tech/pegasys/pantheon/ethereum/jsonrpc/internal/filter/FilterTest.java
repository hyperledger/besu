package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

public class FilterTest {

  @Test
  public void filterJustCreatedShouldNotBeExpired() {
    BlockFilter filter = new BlockFilter("foo");

    assertThat(filter.isExpired()).isFalse();
  }

  @Test
  public void isExpiredShouldReturnTrueForExpiredFilter() {
    BlockFilter filter = new BlockFilter("foo");
    filter.setExpireTime(Instant.now().minusSeconds(1));

    assertThat(filter.isExpired()).isTrue();
  }

  @Test
  public void resetExpireDateShouldIncrementExpireDate() {
    BlockFilter filter = new BlockFilter("foo");
    filter.setExpireTime(Instant.now().minus(Duration.ofDays(1)));
    filter.resetExpireTime();

    assertThat(filter.getExpireTime())
        .isBeforeOrEqualTo(Instant.now().plus(Duration.ofMinutes(10)));
  }
}
