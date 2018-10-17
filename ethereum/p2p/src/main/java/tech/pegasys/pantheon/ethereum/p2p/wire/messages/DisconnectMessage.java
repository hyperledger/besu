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
package tech.pegasys.pantheon.ethereum.p2p.wire.messages;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.pantheon.util.Preconditions.checkGuard;

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.utils.ByteBufUtils;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.WireProtocolException;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.stream.Stream;

import io.netty.buffer.ByteBuf;

public final class DisconnectMessage extends AbstractMessageData {

  private DisconnectMessage(final ByteBuf data) {
    super(data);
  }

  public static DisconnectMessage create(final DisconnectReason reason) {
    final Data data = new Data(reason);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    data.writeTo(out);
    final ByteBuf buf = ByteBufUtils.fromRLPOutput(out);

    return new DisconnectMessage(buf);
  }

  public static DisconnectMessage readFrom(final MessageData message) {
    if (message instanceof DisconnectMessage) {
      message.retain();
      return (DisconnectMessage) message;
    }
    final int code = message.getCode();
    if (code != WireMessageCodes.DISCONNECT) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a DisconnectMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new DisconnectMessage(data);
  }

  @Override
  public int getCode() {
    return WireMessageCodes.DISCONNECT;
  }

  public DisconnectReason getReason() {
    return Data.readFrom(ByteBufUtils.toRLPInput(data)).getReason();
  }

  @Override
  public String toString() {
    return "DisconnectMessage{" + "data=" + data + '}';
  }

  public static class Data {
    private final DisconnectReason reason;

    public Data(final DisconnectReason reason) {
      this.reason = reason;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      out.writeByte(reason.getValue());
      out.endList();
    }

    public static Data readFrom(final RLPInput in) {
      final int size = in.enterList();
      checkGuard(size == 1, WireProtocolException::new, "Expected list size 1, got: %s", size);
      final DisconnectReason reason = DisconnectReason.forCode(in.readByte());
      in.leaveList();

      return new Data(reason);
    }

    public DisconnectReason getReason() {
      return reason;
    }
  }

  /**
   * Reasons for disconnection, modelled as specified in the wire protocol DISCONNECT message.
   *
   * @see <a href="https://github.com/ethereum/wiki/wiki/%C3%90%CE%9EVp2p-Wire-Protocol">ÐΞVp2p Wire
   *     Protocol</a>
   */
  public static enum DisconnectReason {
    REQUESTED((byte) 0x00),
    TCP_SUBSYSTEM_ERROR((byte) 0x01),
    BREACH_OF_PROTOCOL((byte) 0x02),
    USELESS_PEER((byte) 0x03),
    TOO_MANY_PEERS((byte) 0x04),
    ALREADY_CONNECTED((byte) 0x05),
    INCOMPATIBLE_P2P_PROTOCOL_VERSION((byte) 0x06),
    NULL_NODE_ID((byte) 0x07),
    CLIENT_QUITTING((byte) 0x08),
    UNEXPECTED_ID((byte) 0x09),
    LOCAL_IDENTITY((byte) 0x0a),
    TIMEOUT((byte) 0x0b),
    SUBPROTOCOL_TRIGGERED((byte) 0x10);

    private static final DisconnectReason[] BY_ID;
    private final byte code;

    static {
      final int maxValue =
          Stream.of(DisconnectReason.values()).mapToInt(dr -> (int) dr.getValue()).max().getAsInt();
      BY_ID = new DisconnectReason[maxValue + 1];
      Stream.of(DisconnectReason.values()).forEach(dr -> BY_ID[dr.getValue()] = dr);
    }

    public static DisconnectReason forCode(final byte taintedCode) {
      final byte code = (byte) (taintedCode & 0xff);
      checkArgument(code < BY_ID.length, "unrecognized disconnect reason");
      final DisconnectReason reason = BY_ID[code];
      checkArgument(reason != null, "unrecognized disconnect reason");
      return reason;
    }

    DisconnectReason(final byte code) {
      this.code = code;
    }

    public byte getValue() {
      return code;
    }
  }
}
