package org.hyperledger.besu.datatypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.tuweni.units.bigints.UInt64;
import org.hyperledger.besu.plugin.data.Quantity;

import java.math.BigInteger;
import java.util.Random;

public class PayloadIdentifier implements Quantity {

  private final UInt64 val;

  @JsonCreator
  public PayloadIdentifier(final String payloadId) {
    this(Long.decode(payloadId));
  }

  public PayloadIdentifier(final Long payloadId) {
    this.val = UInt64.valueOf(payloadId);
  }

  public static PayloadIdentifier random() {
    return new PayloadIdentifier(new Random().nextLong());
  }

  @Override
  public Number getValue() {
    return getAsBigInteger();
  }

  @Override
  public BigInteger getAsBigInteger() {
    return val.toBigInteger();
  }

  @Override
  public String toHexString() {
    return val.toHexString();
  }

  @Override
  public String toShortHexString() {
    return val.toShortHexString();
  }

  @JsonValue
  public String serialize() {
    return val.toShortHexString()
  }

}
