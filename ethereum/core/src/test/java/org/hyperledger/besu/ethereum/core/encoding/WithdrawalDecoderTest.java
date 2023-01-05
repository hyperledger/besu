package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Withdrawal;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class WithdrawalDecoderTest {

  @Test
  void shouldDecodeWithdrawalForZeroCase() {
    Withdrawal withdrawal =
        WithdrawalDecoder.decodeOpaqueBytes(
            Bytes.fromHexString(WithdrawalEncoderTest.WITHDRAWAL_ZERO_CASE));
    assertThat(withdrawal.getIndex()).isEqualTo(UInt64.ZERO);
    assertThat(withdrawal.getValidatorIndex()).isEqualTo(UInt64.ZERO);
    assertThat(withdrawal.getAddress()).isEqualTo(Address.ZERO);
    assertThat(withdrawal.getAmount()).isEqualTo(Wei.ZERO);
  }

  @Test
  void shouldDecodeWithdrawalForMaxValue() {
    Withdrawal withdrawal =
        WithdrawalDecoder.decodeOpaqueBytes(
            Bytes.fromHexString(WithdrawalEncoderTest.WITHDRAWAL_MAX_VALUE));
    assertThat(withdrawal.getIndex()).isEqualTo(UInt64.MAX_VALUE);
    assertThat(withdrawal.getValidatorIndex()).isEqualTo(UInt64.MAX_VALUE);
    assertThat(withdrawal.getAddress()).isEqualTo(WithdrawalEncoderTest.MAX_ADDRESS);
    assertThat(withdrawal.getAmount()).isEqualTo(Wei.MAX_WEI);
  }
}
