package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.utils.ByteBufUtils;
import net.consensys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.ethereum.rlp.RLPOutput;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import io.netty.buffer.ByteBuf;

public final class StatusMessage extends AbstractMessageData {

  private EthStatus status;

  public StatusMessage(final ByteBuf data) {
    super(data);
  }

  public static StatusMessage create(
      final int protocolVersion,
      final int networkId,
      final UInt256 totalDifficulty,
      final Hash bestHash,
      final Hash genesisHash) {
    final EthStatus status =
        new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    status.writeTo(out);
    final ByteBuf data = ByteBufUtils.fromRLPOutput(out);

    return new StatusMessage(data);
  }

  public static StatusMessage readFrom(final MessageData message) {
    if (message instanceof StatusMessage) {
      message.retain();
      return (StatusMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.STATUS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a StatusMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new StatusMessage(data);
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
  public int networkId() {
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
      final RLPInput input = RLP.input(BytesValue.wrap(ByteBufUtils.toByteArray(data)));
      status = EthStatus.readFrom(input);
    }
    return status;
  }

  private static class EthStatus {
    private final int protocolVersion;
    private final int networkId;
    private final UInt256 totalDifficulty;
    private final Hash bestHash;
    private final Hash genesisHash;

    public EthStatus(
        final int protocolVersion,
        final int networkId,
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
      out.writeIntScalar(networkId);
      out.writeUInt256Scalar(totalDifficulty);
      out.writeBytesValue(bestHash);
      out.writeBytesValue(genesisHash);

      out.endList();
    }

    public static EthStatus readFrom(final RLPInput in) {
      in.enterList();

      final int protocolVersion = in.readIntScalar();
      final int networkId = in.readIntScalar();
      final UInt256 totalDifficulty = in.readUInt256Scalar();
      final Hash bestHash = Hash.wrap(in.readBytes32());
      final Hash genesisHash = Hash.wrap(in.readBytes32());

      in.leaveList();

      return new EthStatus(protocolVersion, networkId, totalDifficulty, bestHash, genesisHash);
    }
  }
}
