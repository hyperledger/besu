package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class WithdrawalEncoderTest {
  public static final String WITHDRAWAL_ZERO_CASE =
      "0xd8808094000000000000000000000000000000000000000080";
  public static final String WITHDRAWAL_MAX_VALUE =
      "0xf84888ffffffffffffffff88ffffffffffffffff94ffffffffffffffffffffffffffffffffffffffffa0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
  public static final Address MAX_ADDRESS =
      Address.fromHexString(Bytes.repeat((byte) 0xff, 20).toHexString());

  @Test
  void shouldEncodeWithdrawalForZeroCase() {
    final Withdrawal withdrawal = new Withdrawal(UInt64.ZERO, UInt64.ZERO, Address.ZERO, Wei.ZERO);
    final Bytes bytes = WithdrawalEncoder.encodeOpaqueBytes(withdrawal);
    assertThat(bytes.toHexString()).isEqualTo(WITHDRAWAL_ZERO_CASE);
  }

  @Test
  void shouldEncodeWithdrawalForMaxValues() {
    final Withdrawal withdrawal =
        new Withdrawal(UInt64.MAX_VALUE, UInt64.MAX_VALUE, MAX_ADDRESS, Wei.MAX_WEI);
    final Bytes bytes = WithdrawalEncoder.encodeOpaqueBytes(withdrawal);
    assertThat(bytes.toHexString()).isEqualTo(WITHDRAWAL_MAX_VALUE);
  }
}
