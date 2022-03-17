package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class RangeManagerTest {

  @Test
  public void testGenerateAllRangesWithSize1() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(1);
    Assertions.assertThat(ranges.size()).isEqualTo(1);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }

  @Test
  public void testGenerateAllRangesWithSize3() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0x5555555555555555555555555555555555555555555555555555555555555555"));
    expectedResult.put(
        Bytes32.fromHexString("0x5555555555555555555555555555555555555555555555555555555555555556"),
        Bytes32.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab"));
    expectedResult.put(
        Bytes32.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges = RangeManager.generateAllRanges(3);
    Assertions.assertThat(ranges.size()).isEqualTo(3);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }

  @Test
  public void testGenerateRangesWithSize3() {
    final Map<Bytes32, Bytes32> expectedResult = new HashMap<>();
    expectedResult.put(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        Bytes32.fromHexString(
            "0x5555555555555555555555555555555555555555555555555555555555555555"));
    expectedResult.put(
        Bytes32.fromHexString("0x5555555555555555555555555555555555555555555555555555555555555556"),
        Bytes32.fromHexString(
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab"));
    expectedResult.put(
        Bytes32.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaac"),
        Bytes32.fromHexString(
            "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    final Map<Bytes32, Bytes32> ranges =
        RangeManager.generateRanges(
            Bytes32.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"),
            Bytes32.fromHexString(
                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
            3);
    Assertions.assertThat(ranges.size()).isEqualTo(3);
    Assertions.assertThat(ranges).isEqualTo(expectedResult);
  }
}
