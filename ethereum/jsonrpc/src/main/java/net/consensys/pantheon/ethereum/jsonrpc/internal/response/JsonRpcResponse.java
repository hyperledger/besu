package net.consensys.pantheon.ethereum.jsonrpc.internal.response;

import com.fasterxml.jackson.annotation.JsonGetter;

public interface JsonRpcResponse {

  @JsonGetter("jsonrpc")
  default String getVersion() {
    return "2.0";
  }

  JsonRpcResponseType getType();
}
