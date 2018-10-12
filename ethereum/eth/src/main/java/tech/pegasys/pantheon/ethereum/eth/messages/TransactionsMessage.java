package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Iterator;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;

public class TransactionsMessage extends AbstractMessageData {

  public static TransactionsMessage readFrom(final MessageData message) {
    if (message instanceof TransactionsMessage) {
      message.retain();
      return (TransactionsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.TRANSACTIONS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a TransactionsMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new TransactionsMessage(data);
  }

  public static TransactionsMessage create(final Iterable<Transaction> transactions) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    for (final Transaction transaction : transactions) {
      transaction.writeTo(tmp);
    }
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new TransactionsMessage(data);
  }

  private TransactionsMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.TRANSACTIONS;
  }

  public Iterator<Transaction> transactions(
      final Function<RLPInput, Transaction> transactionReader) {
    final byte[] transactions = new byte[data.readableBytes()];
    data.getBytes(0, transactions);
    return new BytesValueRLPInput(BytesValue.wrap(transactions), false)
        .readList(transactionReader)
        .iterator();
  }
}
