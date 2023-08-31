package org.hyperledger.besu.ethereum.core.encoding;

import static org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.writeSignatureAndV;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.Encoder;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class FrontierTransactionEncoder implements Encoder {
  @Override
  public void encode(final Transaction transaction, final RLPOutput out,
      final DecodingContext context) {
    out.startList();
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getGasPrice().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeSignatureAndV(transaction, out);
    out.endList();
  }
}
