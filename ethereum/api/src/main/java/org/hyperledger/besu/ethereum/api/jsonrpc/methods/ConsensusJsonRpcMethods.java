package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ConsensusAssembleBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ConsensusFinalizeBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ConsensusNewBlock;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ConsensusSetHead;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Map;

public class ConsensusJsonRpcMethods extends ApiGroupJsonRpcMethods {
  @Override
  protected RpcApi getApiGroup() {
    return RpcApis.CONSENSUS;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    return mapOf(
        new ConsensusAssembleBlock(),
        new ConsensusFinalizeBlock(),
        new ConsensusNewBlock(),
        new ConsensusSetHead());
  }
}
