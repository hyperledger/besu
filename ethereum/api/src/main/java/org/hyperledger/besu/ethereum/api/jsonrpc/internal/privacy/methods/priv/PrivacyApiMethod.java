package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;

public abstract class PrivacyApiMethod implements JsonRpcMethod {

  protected final PrivacyParameters privacyParameters;

  protected PrivacyApiMethod(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (privacyParameters.isEnabled()) {
      return doResponse(requestContext);
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.PRIVACY_NOT_ENABLED);
    }
  }

  public abstract JsonRpcResponse doResponse(final JsonRpcRequestContext request);
}
