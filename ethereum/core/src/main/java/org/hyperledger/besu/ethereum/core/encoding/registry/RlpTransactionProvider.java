package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class RlpTransactionProvider {
  private RlpTransactionProvider(){}

  public static Transaction readFrom(final RLPInput rlpInput){
   return getDecoder().readFrom(rlpInput);
  }

  public static Transaction readFrom(final Bytes bytes){
    return getDecoder().readFrom(bytes);
  }

  public static Transaction decodeOpaqueBytes(final Bytes bytes){
    return getDecoder().decodeOpaqueBytes(bytes);
  }

  public static void writeTo(Transaction transaction, RLPOutput output) {
    getEncoder().writeTo(transaction, output);
  }

  public static Bytes encodeOpaqueBytes(Transaction transaction){
    return getEncoder().encodeOpaqueBytes(transaction);
  }

  private static
  TransactionDecoder getDecoder(){
    return RlpRegistry.getInstance().getTransactionDecoder();
  }

  private static
  TransactionEncoder getEncoder(){
    return RlpRegistry.getInstance().getTransactionEncoder();
  }
}
