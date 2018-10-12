package net.consensys.pantheon.ethereum.trie;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.crypto.Hash;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.Random;

import org.junit.Test;

public class CompactEncodingTest {

  @Test
  public void bytesToPath() {
    final BytesValue path = CompactEncoding.bytesToPath(BytesValue.of(0xab, 0xcd, 0xff));
    assertThat(path).isEqualTo(BytesValue.of(0xa, 0xb, 0xc, 0xd, 0xf, 0xf, 0x10));
  }

  @Test
  public void shouldRoundTripFromBytesToPathAndBack() {
    final Random random = new Random(282943948928429484L);
    for (int i = 0; i < 1000; i++) {
      final Bytes32 bytes = Hash.keccak256(UInt256.of(Math.abs(random.nextInt())).getBytes());
      final BytesValue path = CompactEncoding.bytesToPath(bytes);
      assertThat(CompactEncoding.pathToBytes(path)).isEqualTo(bytes);
    }
  }

  @Test
  public void encodePath() {
    assertThat(CompactEncoding.encode(BytesValue.of(0x01, 0x02, 0x03, 0x04, 0x05)))
        .isEqualTo(BytesValue.of(0x11, 0x23, 0x45));
    assertThat(CompactEncoding.encode(BytesValue.of(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)))
        .isEqualTo(BytesValue.of(0x00, 0x01, 0x23, 0x45));
    assertThat(CompactEncoding.encode(BytesValue.of(0x00, 0x0f, 0x01, 0x0c, 0x0b, 0x08, 0x10)))
        .isEqualTo(BytesValue.of(0x20, 0x0f, 0x1c, 0xb8));
    assertThat(CompactEncoding.encode(BytesValue.of(0x0f, 0x01, 0x0c, 0x0b, 0x08, 0x10)))
        .isEqualTo(BytesValue.of(0x3f, 0x1c, 0xb8));
  }

  @Test
  public void decode() {
    assertThat(CompactEncoding.decode(BytesValue.of(0x11, 0x23, 0x45)))
        .isEqualTo(BytesValue.of(0x01, 0x02, 0x03, 0x04, 0x05));
    assertThat(CompactEncoding.decode(BytesValue.of(0x00, 0x01, 0x23, 0x45)))
        .isEqualTo(BytesValue.of(0x00, 0x01, 0x02, 0x03, 0x04, 0x05));
    assertThat(CompactEncoding.decode(BytesValue.of(0x20, 0x0f, 0x1c, 0xb8)))
        .isEqualTo(BytesValue.of(0x00, 0x0f, 0x01, 0x0c, 0x0b, 0x08, 0x10));
    assertThat(CompactEncoding.decode(BytesValue.of(0x3f, 0x1c, 0xb8)))
        .isEqualTo(BytesValue.of(0x0f, 0x01, 0x0c, 0x0b, 0x08, 0x10));
  }
}
