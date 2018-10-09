package net.consensys.pantheon.ethereum.jsonrpc.websocket.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

public class WebSocketRpcRequest extends JsonRpcRequest {

  private String connectionId;

  @JsonCreator
  public WebSocketRpcRequest(
      @JsonProperty("jsonrpc") final String version,
      @JsonProperty("method") final String method,
      @JsonProperty("params") final Object[] params,
      @JsonProperty("connectionId") final String connectionId) {
    super(version, method, params);
    this.connectionId = connectionId;
  }

  @JsonSetter("connectionId")
  public void setConnectionId(final String connectionId) {
    this.connectionId = connectionId;
  }

  @JsonGetter("connectionId")
  public String getConnectionId() {
    return this.connectionId;
  }
}
