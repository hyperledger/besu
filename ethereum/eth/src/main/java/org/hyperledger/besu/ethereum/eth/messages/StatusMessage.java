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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.ForkId;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public final class StatusMessage extends AbstractMessageData {

  private EthStatus status;

  public StatusMessage(final Bytes data) {
    super(data);
  }

  public static StatusMessage create(
      final int protocolVersion,
      final BigInteger networkId,
      final Difficulty totalDifficulty,
      final Hash bestHash,
      final Hash genesisHash) {
    final EthStatus status =
        new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    status.writeTo(out);

    return new StatusMessage(out.encoded());
  }

  public static StatusMessage create(
      final int protocolVersion,
      final BigInteger networkId,
      final Difficulty totalDifficulty,
      final Hash bestHash,
      final Hash genesisHash,
      final ForkId forkId) {
    final EthStatus status =
        new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash, forkId);
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

  /**
   * Return The eth protocol version the associated node is running.
   *
   * @return The eth protocol version the associated node is running.
   */
  public int protocolVersion() {
    return status().protocolVersion;
  }

  /**
   * Return The id of the network the associated node is participating in.
   *
   * @return The id of the network the associated node is participating in.
   */
  public BigInteger networkId() {
    return status().networkId;
  }

  /**
   * Return The total difficulty of the head of the associated node's local blockchain.
   *
   * @return The total difficulty of the head of the associated node's local blockchain.
   */
  public Difficulty totalDifficulty() {
    return status().totalDifficulty;
  }

  /**
   * Return The hash of the head of the associated node's local blockchian.
   *
   * @return The hash of the head of the associated node's local blockchian.
   */
  public Hash bestHash() {
    return status().bestHash;
  }

  /**
   * Return The hash of the genesis block of the network the associated node is participating in.
   *
   * @return The hash of the genesis block of the network the associated node is participating in.
   */
  public Bytes32 genesisHash() {
    return status().genesisHash;
  }

  /**
   * Return The fork id of the network the associated node is participating in.
   *
   * @return The fork id of the network the associated node is participating in.
   */
  public ForkId forkId() {
    return status().forkId;
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
    private final Difficulty totalDifficulty;
    private final Hash bestHash;
    private final Hash genesisHash;
    private final ForkId forkId;

    EthStatus(
        final int protocolVersion,
        final BigInteger networkId,
        final Difficulty totalDifficulty,
        final Hash bestHash,
        final Hash genesisHash) {
      this.protocolVersion = protocolVersion;
      this.networkId = networkId;
      this.totalDifficulty = totalDifficulty;
      this.bestHash = bestHash;
      this.genesisHash = genesisHash;
      this.forkId = null;
    }

    EthStatus(
        final int protocolVersion,
        final BigInteger networkId,
        final Difficulty totalDifficulty,
        final Hash bestHash,
        final Hash genesisHash,
        final ForkId forkHash) {
      this.protocolVersion = protocolVersion;
      this.networkId = networkId;
      this.totalDifficulty = totalDifficulty;
      this.bestHash = bestHash;
      this.genesisHash = genesisHash;
      this.forkId = forkHash;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();

      out.writeIntScalar(protocolVersion);
      out.writeBigIntegerScalar(networkId);
      out.writeUInt256Scalar(totalDifficulty);
      out.writeBytes(bestHash);
      out.writeBytes(genesisHash);
      if (forkId != null) {
        forkId.writeTo(out);
      }
      out.endList();
    }

    public static EthStatus readFrom(final RLPInput in) {
      in.enterList();

      final int protocolVersion = in.readIntScalar();
      final BigInteger networkId = in.readBigIntegerScalar();
      final Difficulty totalDifficulty = Difficulty.of(in.readUInt256Scalar());
      final Hash bestHash = Hash.wrap(in.readBytes32());
      final Hash genesisHash = Hash.wrap(in.readBytes32());
      final ForkId forkId;
      if (in.nextIsList()) {
        forkId = ForkId.readFrom(in);
      } else {
        forkId = null;
      }
      in.leaveList();

      return new EthStatus(
          protocolVersion, networkId, totalDifficulty, bestHash, genesisHash, forkId);
    }

    @Override
    public String toString() {
      return "EthStatus{"
          + "protocolVersion="
          + protocolVersion
          + ", networkId="
          + networkId
          + ", totalDifficulty="
          + totalDifficulty
          + ", bestHash="
          + bestHash
          + ", genesisHash="
          + genesisHash
          + ", forkId="
          + forkId
          + '}';
    }
  }

  @Override
  public String toString() {
    return status().toString();
  }
}
