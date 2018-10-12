package tech.pegasys.pantheon.ethereum.jsonrpc.internal.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;

@JsonPropertyOrder({"jsonrpc", "id", "error"})
public class JsonRpcErrorResponse implements JsonRpcResponse {

  private final Object id;
  private final JsonRpcError error;

  public JsonRpcErrorResponse(final Object id, final JsonRpcError error) {
    this.id = id;
    this.error = error;
  }

  @JsonGetter("id")
  public Object getId() {
    return id;
  }

  @JsonGetter("error")
  public JsonRpcError getError() {
    return error;
  }

  @Override
  @JsonIgnore
  public JsonRpcResponseType getType() {
    return JsonRpcResponseType.ERROR;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcErrorResponse that = (JsonRpcErrorResponse) o;
    return Objects.equal(id, that.id) && error == that.error;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, error);
  }
}
