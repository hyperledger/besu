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

import org.hyperledger.besu.ethereum.p2p.rlpx.framing.Framer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.CapabilityMultiplexer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

final class MessageFramer extends MessageToByteEncoder<OutboundMessage> {

  private final CapabilityMultiplexer multiplexer;

  private final Framer framer;

  MessageFramer(final CapabilityMultiplexer multiplexer, final Framer framer) {
    this.multiplexer = multiplexer;
    this.framer = framer;
  }

  @Override
  protected void encode(
      final ChannelHandlerContext ctx, final OutboundMessage msg, final ByteBuf out) {
    framer.frame(multiplexer.multiplex(msg.getCapability(), msg.getData()), out);
  }
}
