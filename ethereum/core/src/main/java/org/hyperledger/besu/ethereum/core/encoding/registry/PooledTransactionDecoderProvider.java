package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

public class PooledTransactionDecoderProvider {

  private PooledTransactionDecoderProvider(){}

  public static Transaction readFrom(final RLPInput rlpInput){
   return getDecoder().readFrom(rlpInput);
  }

  public static Transaction readFrom(final Bytes bytes){
    return getDecoder().readFrom(bytes);
  }

  public static Transaction decodeOpaqueBytes(final Bytes bytes){
    return getDecoder().decodeOpaqueBytes(bytes);
  }

  private static
  TransactionDecoder getDecoder(){
    return DecoderRegistry.getInstance().getPooledTransactionDecoder();
  }
}
