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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.testhelpers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class NettyMocks {
  public static ChannelFuture channelFuture(final boolean completeImmediately) {
    ChannelFuture channelFuture = mock(ChannelFuture.class);
    when(channelFuture.addListener(any()))
        .then(
            (invocation) -> {
              if (completeImmediately) {
                GenericFutureListener<Future<?>> listener = invocation.getArgument(0);
                listener.operationComplete(mock(Future.class));
              }
              return channelFuture;
            });

    return channelFuture;
  }
}
