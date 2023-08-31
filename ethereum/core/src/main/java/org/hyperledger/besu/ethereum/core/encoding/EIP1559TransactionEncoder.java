package org.hyperledger.besu.ethereum.core.encoding;

import static org.hyperledger.besu.ethereum.core.encoding.AccessListTransactionEncoder.writeAccessList;
import static org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.writeSignatureAndRecoveryId;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.Encoder;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class EIP1559TransactionEncoder implements Encoder {

  @Override
  public void encode(final Transaction transaction, final RLPOutput output,
      final DecodingContext context) {
    encodeEIP1559(transaction, output);
  }

  static void encodeEIP1559(final Transaction transaction, final RLPOutput out) {
    out.startList();
    out.writeBigIntegerScalar(transaction.getChainId().orElseThrow());
    out.writeLongScalar(transaction.getNonce());
    out.writeUInt256Scalar(transaction.getMaxPriorityFeePerGas().orElseThrow());
    out.writeUInt256Scalar(transaction.getMaxFeePerGas().orElseThrow());
    out.writeLongScalar(transaction.getGasLimit());
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    out.writeUInt256Scalar(transaction.getValue());
    out.writeBytes(transaction.getPayload());
    writeAccessList(out, transaction.getAccessList());
    writeSignatureAndRecoveryId(transaction, out);
    out.endList();
  }
}
