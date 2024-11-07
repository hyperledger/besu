package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class TransactionEncoderProvider{
  private TransactionEncoderProvider(){}

  public static void writeTo(Transaction transaction, RLPOutput output) {
    getEncoder().writeTo(transaction, output);
  }

  public static Bytes encodeOpaqueBytes(Transaction transaction){
    return getEncoder().encodeOpaqueBytes(transaction);
  }

  private static
  TransactionEncoder getEncoder(){
    return RLPRegistry.getInstance().getTransactionEncoder();
  }
}
