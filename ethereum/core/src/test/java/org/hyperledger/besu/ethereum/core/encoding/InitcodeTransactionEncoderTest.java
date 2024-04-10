package org.hyperledger.besu.ethereum.core.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InitcodeTransactionEncoderTest {
  private static Stream<Arguments> provideTestVector() {
    Transaction.Builder base =
        Transaction.builder()
            .chainId(BigInteger.TEN)
            .nonce(22)
            .maxPriorityFeePerGas(Wei.of(2_000_000_000L))
            .maxFeePerGas(Wei.of(200_000_000_000L))
            .gasLimit(8_000_000)
            .to(Address.fromHexString("0xabcdef0987654321"))
            .value(Wei.ZERO)
            .payload(Bytes.fromHexString("0x87654321"))
            .signature(
                new SECP256K1()
                    .createSignature(
                        Bytes.fromHexString(
                                "0x2222222222222222222222222222222222222222222222222222222222222222")
                            .toUnsignedBigInteger(),
                        Bytes.fromHexString(
                                "0x2222222222222222222222222222222222222222222222222222222222222222")
                            .toUnsignedBigInteger(),
                        (byte) 0));

    Bytes[] maxCodes = new Bytes[256];
    Arrays.fill(maxCodes, Bytes.fromHexString("0xabcdef"));
    Bytes[] bigMaxCodes = new Bytes[256];
    Arrays.fill(bigMaxCodes, Bytes.repeat((byte) 0xef, 0xc000));

    return Stream.of(
        Arguments.of(
            "single",
            base.initcodes(List.of(Bytes.fromHexString("123456"))).build(),
            "0xf8750a168477359400852e90edd000837a120094000000000000000000000000abcdef0987654321808487654321c0c48312345680a02222222222222222222222222222222222222222222222222222222222222222a02222222222222222222222222222222222222222222222222222222222222222"),
        Arguments.of(
            "double",
            base.initcodes(List.of(Bytes.fromHexString("123456"), Bytes.fromHexString("123456")))
                .build(),
            "0xf8790a168477359400852e90edd000837a120094000000000000000000000000abcdef0987654321808487654321c0c8831234568312345680a02222222222222222222222222222222222222222222222222222222222222222a02222222222222222222222222222222222222222222222222222222222222222"),
        Arguments.of("max", base.initcodes(List.of(maxCodes)).build(), null),
        Arguments.of("bigmax", base.initcodes(List.of(bigMaxCodes)).build(), null),
        Arguments.of(
            "accessList",
            base.accessList(
                    List.of(AccessListEntry.createAccessListEntry(Address.ALTBN128_ADD, List.of())))
                .initcodes(List.of(bigMaxCodes))
                .build(),
            null));
  }

  @ParameterizedTest(name = "{index} {0}")
  @MethodSource("provideTestVector")
  void initcodeTransactionEncodes(
      final String ignoredName, final Transaction initcodeTransaction, final String expected) {
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    InitcodeTransactionEncoder.encode(initcodeTransaction, rlpOut);
    Bytes encoded = rlpOut.encoded();
    if (expected != null) {
      assertThat(encoded.toHexString()).isEqualTo(expected);
    }

    RLPInput rlpIn = new BytesValueRLPInput(encoded, false);
    Transaction roundTrip = InitcodeTransactionDecoder.decode(rlpIn);
    assertThat(roundTrip).isEqualTo(initcodeTransaction);
  }
}
