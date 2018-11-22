/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.ibftmessage;

import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.function.Function;

public abstract class AbstractIbftMessage extends AbstractMessageData {
  protected AbstractIbftMessage(final BytesValue data) {
    super(data);
  }

  public abstract IbftSignedMessageData<?> decode();

  protected static <T extends AbstractIbftMessage> T fromMessage(
      final MessageData message,
      final int messageCode,
      final Class<T> clazz,
      final Function<BytesValue, T> constructor) {
    if (clazz.isInstance(message)) {
      @SuppressWarnings("unchecked")
      T castMessage = (T) message;
      return castMessage;
    }
    final int code = message.getCode();
    if (code != messageCode) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a %s", code, clazz.getSimpleName()));
    }

    return constructor.apply(message.getData());
  }
}
