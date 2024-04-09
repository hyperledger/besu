package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes32;

public record SuffixStateDiff(byte suffix, Bytes32 currentValue, Bytes32 newValue) {

  @Override
  public String toString() {
    return "SuffixStateDiff{"
        + "suffix="
        + suffix
        + ", currentValue="
        + currentValue
        + ", new Value ="
        + newValue;
  }
}
