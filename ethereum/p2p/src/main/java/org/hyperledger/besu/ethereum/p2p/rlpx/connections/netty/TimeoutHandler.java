/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

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
  public interface OnTimeoutCallback {
    void invoke();
  }
}
