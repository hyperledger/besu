package net.consensys.pantheon.ethereum.vm;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.util.bytes.BytesValue;

import org.junit.Assert;
import org.junit.Test;

public class AddressTest {

  @Test
  public void accountAddressToString() {
    final Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Assert.assertEquals("0x0000000000000000000000000000000000101010", addr.toString());
  }

  @Test
  public void accountAddressEquals() {
    final Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    final Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr, addr2);
  }

  @Test
  public void accountAddresHashCode() {
    final Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    final Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr.hashCode(), addr2.hashCode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidAccountAddress() {
    Address.wrap(BytesValue.fromHexString("0x00101010"));
  }
}
