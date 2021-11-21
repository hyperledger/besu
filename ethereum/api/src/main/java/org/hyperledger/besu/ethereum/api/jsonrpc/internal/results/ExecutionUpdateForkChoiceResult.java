package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import org.hyperledger.besu.datatypes.PayloadIdentifier;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ForkChoiceStatus;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"status", "payloadId"})
public class ExecutionUpdateForkChoiceResult {
  private final ForkChoiceStatus status;
  private final PayloadIdentifier payloadId;

  public ExecutionUpdateForkChoiceResult(
      final ForkChoiceStatus status, final PayloadIdentifier payloadId) {
    this.status = status;
    this.payloadId = payloadId;
  }

  @JsonGetter(value = "status")
  public String getStatus() {
    return status.name();
  }

  @JsonGetter(value = "payloadId")
  @JsonInclude(NON_NULL)
  public String getPayloadId() {
    return Optional.ofNullable(payloadId).map(PayloadIdentifier::toShortHexString).orElse(null);
  }
}
