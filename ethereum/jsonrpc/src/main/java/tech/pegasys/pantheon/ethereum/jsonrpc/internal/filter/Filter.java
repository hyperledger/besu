package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import java.time.Duration;
import java.time.Instant;

import com.google.common.annotations.VisibleForTesting;

abstract class Filter {

  private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofMinutes(10);

  private final String id;
  private Instant expireTime;

  Filter(final String id) {
    this.id = id;
    resetExpireTime();
  }

  String getId() {
    return id;
  }

  void resetExpireTime() {
    this.expireTime = Instant.now().plus(DEFAULT_EXPIRE_DURATION);
  }

  boolean isExpired() {
    return Instant.now().isAfter(expireTime);
  }

  @VisibleForTesting
  void setExpireTime(final Instant expireTime) {
    this.expireTime = expireTime;
  }

  @VisibleForTesting
  Instant getExpireTime() {
    return expireTime;
  }
}
