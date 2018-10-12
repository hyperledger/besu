package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;

public interface JsonRpcMethod {

  /**
   * Standardised JSON-RPC method name.
   *
   * @return identification of the JSON-RPC method.
   */
  String getName();

  /**
   * Applies the method to given request.
   *
   * @param request input data for the JSON-RPC method.
   * @return output from applying the JSON-RPC method to the input.
   */
  JsonRpcResponse response(JsonRpcRequest request);
}
