package tech.pegasys.pantheon.ethereum.jsonrpc.internal.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Objects;

@JsonPropertyOrder({"jsonrpc", "id", "result"})
public class JsonRpcSuccessResponse implements JsonRpcResponse {

  private final Object id;
  private final Object result;

  public JsonRpcSuccessResponse(final Object id, final Object result) {
    this.id = id;
    this.result = result;
  }

  @JsonGetter("id")
  public Object getId() {
    return id;
  }

  @JsonGetter("result")
  public Object getResult() {
    return result;
  }

  @Override
  @JsonIgnore
  public JsonRpcResponseType getType() {
    return JsonRpcResponseType.SUCCESS;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcSuccessResponse that = (JsonRpcSuccessResponse) o;
    return Objects.equal(id, that.id) && Objects.equal(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, result);
  }
}
