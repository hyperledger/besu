package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;

public class JsonRpcErrorResponseException extends RuntimeException {

  private final JsonRpcError jsonRpcError;

  public JsonRpcErrorResponseException(final JsonRpcError onchainPrivacyGroupIdNotAvailable) {
    super();
    this.jsonRpcError = onchainPrivacyGroupIdNotAvailable;
  }

  public JsonRpcError getJsonRpcError() {
    return jsonRpcError;
  }
}
