package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import io.vertx.core.Vertx;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

public abstract class AbstractEngineGetPayloadBodiesV2 extends ExecutionEngineJsonRpcMethod {
  protected static final int MAX_REQUEST_BLOCKS = 1024;

  protected AbstractEngineGetPayloadBodiesV2(Vertx vertx, ProtocolContext protocolContext,
      EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
  }

  protected int getMaxRequestBlocks() {
    return MAX_REQUEST_BLOCKS;
  }

  protected Optional<String> getBlockAccessList(final Blockchain blockchain, final Hash blockHash) {
    return blockchain
        .getBlockAccessList(blockHash)
        .map(AbstractEngineGetPayloadBodiesV2::encodeBlockAccessList);
  }

  protected static String encodeBlockAccessList(final BlockAccessList blockAccessList) {
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    blockAccessList.writeTo(output);
    return output.encoded().toHexString();
  }
}
