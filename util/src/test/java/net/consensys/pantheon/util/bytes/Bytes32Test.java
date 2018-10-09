package net.consensys.pantheon.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class Bytes32Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan32() {
    Bytes32.wrap(new byte[31]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan32() {
    Bytes32.wrap(new byte[33]);
  }

  @Test
  public void leftPadAValueToBytes32() {
    final Bytes32 b32 = Bytes32.leftPad(BytesValue.of(1, 2, 3));
    assertThat(b32.size()).isEqualTo(32);
    for (int i = 0; i < 28; ++i) {
      assertThat(b32.get(i)).isEqualTo((byte) 0);
    }
    assertThat(b32.get(29)).isEqualTo((byte) 1);
    assertThat(b32.get(30)).isEqualTo((byte) 2);
    assertThat(b32.get(31)).isEqualTo((byte) 3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenLeftPaddingValueLargerThan32() {
    Bytes32.leftPad(MutableBytesValue.create(33));
  }
}
