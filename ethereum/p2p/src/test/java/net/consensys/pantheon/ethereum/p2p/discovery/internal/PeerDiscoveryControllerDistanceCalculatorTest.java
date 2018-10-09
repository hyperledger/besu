package net.consensys.pantheon.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Random;

import org.junit.Test;

public class PeerDiscoveryControllerDistanceCalculatorTest {

  @Test
  public void distanceZero() {
    final byte[] id = new byte[64];
    new Random().nextBytes(id);
    assertThat(PeerTable.distance(BytesValue.wrap(id), BytesValue.wrap(id))).isEqualTo(0);
  }

  @Test
  public void distance1() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400001");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(1);
  }

  @Test
  public void distance2() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400002");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(2);
  }

  @Test
  public void distance3() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400004");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(3);
  }

  @Test
  public void distance9() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400100");
    final BytesValue id2 = BytesValue.fromHexString("0x8f19400000");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(9);
  }

  @Test
  public void distance40() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x0f19400000");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(40);
  }

  @Test(expected = AssertionError.class)
  public void distance40_differentLengths() {
    final BytesValue id1 = BytesValue.fromHexString("0x8f19400000");
    final BytesValue id2 = BytesValue.fromHexString("0x0f1940000099");
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(40);
  }

  @Test
  public void distanceZero_emptyArrays() {
    final BytesValue id1 = BytesValue.EMPTY;
    final BytesValue id2 = BytesValue.EMPTY;
    assertThat(PeerTable.distance(id1, id2)).isEqualTo(0);
  }
}
