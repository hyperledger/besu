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
package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;

public final class StatusMessage extends AbstractMessageData {

  private EthStatus status;

  public StatusMessage(final BytesValue data) {
    super(data);
  }

  public static StatusMessage create(
      final int protocolVersion,
      final BigInteger networkId,
      final UInt256 totalDifficulty,
      final Hash bestHash,
      final Hash genesisHash) {
    final EthStatus status =
        new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    status.writeTo(out);

    return new StatusMessage(out.encoded());
  }

  public static StatusMessage readFrom(final MessageData message) {
    if (message instanceof StatusMessage) {
      return (StatusMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.STATUS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a StatusMessage.", code));
    }
    return new StatusMessage(message.getData());
  }

  @Override
  public int getCode() {
    return EthPV62.STATUS;
  }

  /** @return The eth protocol version the associated node is running. */
  public int protocolVersion() {
    return status().protocolVersion;
  }

  /** @return The id of the network the associated node is participating in. */
  public BigInteger networkId() {
    return status().networkId;
  }

  /** @return The total difficulty of the head of the associated node's local blockchain. */
  public UInt256 totalDifficulty() {
    return status().totalDifficulty;
  }

  /** @return The hash of the head of the associated node's local blockchian. */
  public Hash bestHash() {
    return status().bestHash;
  }

  /**
   * @return The hash of the genesis block of the network the associated node is participating in.
   */
  public Bytes32 genesisHash() {
    return status().genesisHash;
  }

  private EthStatus status() {
    if (status == null) {
      final RLPInput input = RLP.input(data);
      status = EthStatus.readFrom(input);
    }
    return status;
  }

  private static class EthStatus {
    private final int protocolVersion;
    private final BigInteger networkId;
    private final UInt256 totalDifficulty;
    private final Hash bestHash;
    private final Hash genesisHash;

    EthStatus(
        final int protocolVersion,
        final BigInteger networkId,
        final UInt256 totalDifficulty,
        final Hash bestHash,
        final Hash genesisHash) {
      this.protocolVersion = protocolVersion;
      this.networkId = networkId;
      this.totalDifficulty = totalDifficulty;
      this.bestHash = bestHash;
      this.genesisHash = genesisHash;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();

      out.writeIntScalar(protocolVersion);
      out.writeBigIntegerScalar(networkId);
      out.writeUInt256Scalar(totalDifficulty);
      out.writeBytesValue(bestHash);
      out.writeBytesValue(genesisHash);

      out.endList();
    }

    public static EthStatus readFrom(final RLPInput in) {
      in.enterList();

      final int protocolVersion = in.readIntScalar();
      final BigInteger networkId = in.readBigIntegerScalar();
      final UInt256 totalDifficulty = in.readUInt256Scalar();
      final Hash bestHash = Hash.wrap(in.readBytes32());
      final Hash genesisHash = Hash.wrap(in.readBytes32());

      in.leaveList();

      return new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash);
    }
  }
}
