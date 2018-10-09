package net.consensys.pantheon.ethereum.p2p.netty;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;

/**
 * This handler will close the associated connection if the given condition is not met within the
 * timeout window.
 */
public class TimeoutHandler<C extends Channel> extends ChannelInitializer<C> {
  private final Supplier<Boolean> condition;
  private final int timeoutInSeconds;
  private final OnTimeoutCallback callback;

  public TimeoutHandler(
      final Supplier<Boolean> condition,
      final int timeoutInSeconds,
      final OnTimeoutCallback callback) {
    this.condition = condition;
    this.timeoutInSeconds = timeoutInSeconds;
    this.callback = callback;
  }

  @Override
  protected void initChannel(final C ch) throws Exception {
    ch.eventLoop()
        .schedule(
            () -> {
              if (!condition.get()) {
                callback.invoke();
                ch.close();
              }
            },
            timeoutInSeconds,
            TimeUnit.SECONDS);
  }

  @FunctionalInterface
  interface OnTimeoutCallback {
    void invoke();
  }
}
