/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.p2p.plain;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.p2p.rlpx.framing.Framer;
import org.hyperledger.besu.ethereum.p2p.rlpx.framing.FramingException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlainFramer extends Framer {
  private static final Logger LOG = LoggerFactory.getLogger(PlainFramer.class);

  public PlainFramer() {
    LOG.trace("Initialising PlainFramer");
  }

  @Override
  public synchronized MessageData deframe(final ByteBuf buf) throws FramingException {
    LOG.trace("Deframing Message");
    if (buf == null || !buf.isReadable()) {
      return null;
    }
    PlainMessage message = MessageHandler.parseMessage(buf);
    checkState(
        MessageType.DATA.equals(message.getMessageType()), "unexpected message: needs to be data");
    return new RawMessage(message.getCode(), message.getData());
  }

  @Override
  public synchronized void frame(final MessageData message, final ByteBuf output) {
    LOG.trace("Framing Message");
    output.writeBytes(
        MessageHandler.buildMessage(MessageType.DATA, message.getCode(), message.getData())
            .toArray());
  }
}
