package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;

import java.util.OptionalInt;
import java.util.Set;

public class EthProtocolVersion implements JsonRpcMethod {

  private final Integer highestEthVersion;

  public EthProtocolVersion(final Set<Capability> supportedCapabilities) {
    final OptionalInt version =
        supportedCapabilities
            .stream()
            .filter(cap -> EthProtocol.NAME.equals(cap.getName()))
            .mapToInt(Capability::getVersion)
            .max();
    highestEthVersion = version.isPresent() ? version.getAsInt() : null;
  }

  @Override
  public String getName() {
    return "eth_protocolVersion";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), highestEthVersion);
  }
}
