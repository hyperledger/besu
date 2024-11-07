package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class SignatureEncoder {

  static void writeSignatureAndV(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getV());
    writeSignature(transaction, out);
  }

  static void writeSignatureAndRecoveryId(final Transaction transaction, final RLPOutput out) {
    out.writeIntScalar(transaction.getSignature().getRecId());
    writeSignature(transaction, out);
  }

  static void writeSignature(final Transaction transaction, final RLPOutput out) {
    out.writeBigIntegerScalar(transaction.getSignature().getR());
    out.writeBigIntegerScalar(transaction.getSignature().getS());
  }
}
