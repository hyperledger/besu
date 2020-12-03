package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import static java.lang.Boolean.FALSE;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.deserializer.GasDeserializer;
import org.hyperledger.besu.ethereum.core.deserializer.HexStringDeserializer;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;

public class JsonCallParameter extends CallParameter {

  private final boolean strict;

  @JsonCreator
  public JsonCallParameter(
      @JsonProperty("from") final Address from,
      @JsonProperty("to") final Address to,
      @JsonDeserialize(using = GasDeserializer.class) @JsonProperty("gas") final Gas gasLimit,
      @JsonProperty("gasPrice") final Wei gasPrice,
      @JsonProperty("gasPremium") final Wei gasPremium,
      @JsonProperty("feeCap") final Wei feeCap,
      @JsonProperty("value") final Wei value,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("data")
          final Bytes payload,
      @JsonProperty("strict") final Boolean strict) {
    super(
        from,
        to,
        gasLimit != null ? gasLimit.toLong() : -1,
        gasPrice,
        Optional.ofNullable(gasPremium),
        Optional.ofNullable(feeCap),
        value,
        payload);
    this.strict = Optional.ofNullable(strict).orElse(FALSE);
  }

  public boolean isStrict() {
    return strict;
  }
}
