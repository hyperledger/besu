package org.hyperledger.besu.ethereum.api.util;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class DomainObjectDecodeUtils {
  private static final Logger LOG = LogManager.getLogger();

  public static Transaction decodeRawTransaction(final String rlp)
      throws InvalidJsonRpcRequestException {
    try {
      return Transaction.readFrom(RLP.input(Bytes.fromHexString(rlp)));
    } catch (final IllegalArgumentException | RLPException e) {
      LOG.debug(e);
      throw new InvalidJsonRpcRequestException("Invalid raw transaction hex", e);
    }
  }
}
