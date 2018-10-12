package tech.pegasys.pantheon.ethereum.rlp;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"in"})
public class InvalidRLPRefTestCaseSpec {

  /** The rlp data to analyze. */
  private final BytesValue rlp;

  @JsonCreator
  public InvalidRLPRefTestCaseSpec(@JsonProperty("out") final String out) {
    this.rlp = BytesValue.fromHexStringLenient(out);
  }

  public BytesValue getRLP() {
    return rlp;
  }
}
