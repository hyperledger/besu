package tech.pegasys.pantheon.ethereum.jsonrpc.internal;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcRequestException;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Objects;

public class JsonRpcRequest {

  private JsonRpcRequestId id;
  private final String method;
  private final Object[] params;
  private final String version;
  private boolean isNotification = true;

  @JsonCreator
  public JsonRpcRequest(
      @JsonProperty("jsonrpc") final String version,
      @JsonProperty("method") final String method,
      @JsonProperty("params") final Object[] params) {
    this.version = version;
    this.method = method;
    this.params = params;
    if (method == null) {
      throw new InvalidJsonRpcRequestException("Field 'method' is required");
    }
  }

  @JsonGetter("id")
  public Object getId() {
    return id == null ? null : id.getValue();
  }

  @JsonGetter("method")
  public String getMethod() {
    return method;
  }

  @JsonGetter("jsonrpc")
  public String getVersion() {
    return version;
  }

  @JsonInclude(Include.NON_NULL)
  @JsonGetter("params")
  public Object[] getParams() {
    return params;
  }

  @JsonIgnore
  public boolean isNotification() {
    return isNotification;
  }

  @JsonIgnore
  public int getParamLength() {
    return params.length;
  }

  @JsonSetter("id")
  protected void setId(final JsonRpcRequestId id) {
    // If an id is explicitly set, its not a notification
    isNotification = false;
    this.id = id;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcRequest that = (JsonRpcRequest) o;
    return isNotification == that.isNotification
        && Objects.equal(id, that.id)
        && Objects.equal(method, that.method)
        && Arrays.equals(params, that.params)
        && Objects.equal(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, method, Arrays.hashCode(params), version, isNotification);
  }
}
