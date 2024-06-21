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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public final class DisconnectMessage extends AbstractMessageData {

  private DisconnectMessage(final Bytes data) {
    super(data);
  }

  public static DisconnectMessage create(final DisconnectReason reason) {
    final Data data = new Data(reason);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    data.writeTo(out);

    return new DisconnectMessage(out.encoded());
  }

  public static DisconnectMessage readFrom(final MessageData message) {
    if (message instanceof DisconnectMessage) {
      return (DisconnectMessage) message;
    }
    final int code = message.getCode();
    if (code != WireMessageCodes.DISCONNECT) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a DisconnectMessage.", code));
    }
    return new DisconnectMessage(message.getData());
  }

  @Override
  public int getCode() {
    return WireMessageCodes.DISCONNECT;
  }

  public DisconnectReason getReason() {
    return Data.readFrom(RLP.input(data)).getReason();
  }

  public static class Data {
    private final DisconnectReason reason;

    public Data(final DisconnectReason reason) {
      this.reason = reason;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      out.writeBytes(reason.getValue());
      out.endList();
    }

    public static Data readFrom(final RLPInput in) {
      Bytes reasonData = Bytes.EMPTY;
      if (in.nextIsList()) {
        in.enterList();
        reasonData = in.readBytes();
        in.leaveList();
      } else if (in.nextSize() == 1) {
        reasonData = in.readBytes();
      }

      // Disconnect reason should be at most 1 byte, otherwise, just return UNKNOWN
      final DisconnectReason reason =
          reasonData.size() == 1
              ? DisconnectReason.forCode(reasonData.get(0))
              : DisconnectReason.UNKNOWN;

      return new Data(reason);
    }

    public DisconnectReason getReason() {
      return reason;
    }
  }

  /**
   * Reasons for disconnection, modelled as specified in the wire protocol DISCONNECT message.
   *
   * @see <a href="https://github.com/ethereum/devp2p/blob/master/rlpx.md#disconnect-0x01">RLPx
   *     Transport Protocol</a>
   */
  public enum DisconnectReason {
    UNKNOWN(null),
    REQUESTED((byte) 0x00),
    TCP_SUBSYSTEM_ERROR((byte) 0x01),

    BREACH_OF_PROTOCOL((byte) 0x02),
    BREACH_OF_PROTOCOL_RECEIVED_OTHER_MESSAGE_BEFORE_STATUS(
        (byte) 0x02, "Message other than status received first"),
    BREACH_OF_PROTOCOL_UNSOLICITED_MESSAGE_RECEIVED((byte) 0x02, "Unsolicited message received"),
    BREACH_OF_PROTOCOL_MALFORMED_MESSAGE_RECEIVED((byte) 0x02, "Malformed message received"),
    BREACH_OF_PROTOCOL_NON_SEQUENTIAL_HEADERS((byte) 0x02, "Non-sequential headers received"),
    BREACH_OF_PROTOCOL_INVALID_BLOCK((byte) 0x02, "Invalid block detected"),
    BREACH_OF_PROTOCOL_INVALID_HEADERS((byte) 0x02, "Invalid headers detected"),
    BREACH_OF_PROTOCOL_INVALID_MESSAGE_CODE_FOR_PROTOCOL(
        (byte) 0x02, "Invalid message code for specified protocol"),
    BREACH_OF_PROTOCOL_MESSAGE_RECEIVED_BEFORE_HELLO_EXCHANGE(
        (byte) 0x02, "A message was received before hello's exchanged"),
    BREACH_OF_PROTOCOL_INVALID_MESSAGE_RECEIVED_CAUGHT_EXCEPTION(
        (byte) 0x02, "An exception was caught decoding message"),
    USELESS_PEER((byte) 0x03),
    USELESS_PEER_USELESS_RESPONSES((byte) 0x03, "Useless responses: exceeded threshold"),
    USELESS_PEER_TRAILING_PEER((byte) 0x03, "Trailing peer requirement"),
    USELESS_PEER_NO_SHARED_CAPABILITIES((byte) 0x03, "No shared capabilities"),
    USELESS_PEER_WORLD_STATE_NOT_AVAILABLE((byte) 0x03, "World state not available"),
    USELESS_PEER_MISMATCHED_PIVOT_BLOCK((byte) 0x03, "Mismatched pivot block"),
    USELESS_PEER_FAILED_TO_RETRIEVE_CHAIN_STATE(
        (byte) 0x03, "Failed to retrieve header for chain state"),
    USELESS_PEER_CANNOT_CONFIRM_PIVOT_BLOCK((byte) 0x03, "Peer failed to confirm pivot block"),
    USELESS_PEER_BY_REPUTATION((byte) 0x03, "Lowest reputation score"),
    USELESS_PEER_BY_CHAIN_COMPARATOR((byte) 0x03, "Lowest by chain height comparator"),
    TOO_MANY_PEERS((byte) 0x04),
    ALREADY_CONNECTED((byte) 0x05),
    INCOMPATIBLE_P2P_PROTOCOL_VERSION((byte) 0x06),
    NULL_NODE_ID((byte) 0x07),
    CLIENT_QUITTING((byte) 0x08),
    UNEXPECTED_ID((byte) 0x09),
    LOCAL_IDENTITY((byte) 0x0a),
    TIMEOUT((byte) 0x0b),
    SUBPROTOCOL_TRIGGERED((byte) 0x10),
    SUBPROTOCOL_TRIGGERED_MISMATCHED_NETWORK((byte) 0x10, "Mismatched network id"),
    SUBPROTOCOL_TRIGGERED_MISMATCHED_FORKID((byte) 0x10, "Mismatched fork id"),
    SUBPROTOCOL_TRIGGERED_MISMATCHED_GENESIS_HASH((byte) 0x10, "Mismatched genesis hash"),
    SUBPROTOCOL_TRIGGERED_UNPARSABLE_STATUS((byte) 0x10, "Unparsable status message"),
    SUBPROTOCOL_TRIGGERED_POW_DIFFICULTY((byte) 0x10, "Peer has difficulty greater than POS TTD"),
    SUBPROTOCOL_TRIGGERED_POW_BLOCKS((byte) 0x10, "Peer sent blocks after POS transition");

    private static final DisconnectReason[] BY_ID;
    private final Optional<Byte> code;
    private final Optional<String> message;

    static {
      final int maxValue =
          Stream.of(DisconnectReason.values())
              .filter(r -> r.code.isPresent())
              .mapToInt(r -> (int) r.code.get())
              .max()
              .getAsInt();
      BY_ID = new DisconnectReason[maxValue + 1];
      Stream.of(DisconnectReason.values())
          .filter(r -> r.code.isPresent() && r.message.isEmpty())
          .forEach(r -> BY_ID[r.code.get()] = r);
    }

    public static DisconnectReason forCode(final Byte code) {
      if (code == null || code >= BY_ID.length || code < 0 || BY_ID[code] == null) {
        // Be permissive and just return unknown if the disconnect reason is bad
        return UNKNOWN;
      }
      return BY_ID[code];
    }

    public static DisconnectReason forCode(final Bytes codeBytes) {
      if (codeBytes == null || codeBytes.isEmpty()) {
        return UNKNOWN;
      } else {
        return forCode(codeBytes.get(0));
      }
    }

    DisconnectReason(final Byte code) {
      this.code = Optional.ofNullable(code);
      this.message = Optional.empty();
    }

    DisconnectReason(final Byte code, final String message) {
      this.code = Optional.ofNullable(code);
      this.message = Optional.of(message);
    }

    public Bytes getValue() {
      return code.map(Bytes::of).orElse(Bytes.EMPTY);
    }

    public String getMessage() {
      return message.orElse("");
    }

    @Override
    public String toString() {
      return getValue().toString() + " " + name() + " " + getMessage();
    }
  }
}
