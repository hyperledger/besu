package org.hyperledger.besu.ethereum.core.encoding.registry;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Transaction;

public interface TransactionDecoder extends RlpDecoder<Transaction> {

  Transaction decodeOpaqueBytes(Bytes bytes);
}
