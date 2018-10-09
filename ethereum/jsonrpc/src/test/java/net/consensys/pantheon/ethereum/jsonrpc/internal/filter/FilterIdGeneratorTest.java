package net.consensys.pantheon.ethereum.jsonrpc.internal.filter;

import static org.junit.Assert.assertEquals;

import net.consensys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class FilterIdGeneratorTest {

  @Test
  public void idIsAHexString() {
    final FilterIdGenerator generator = new FilterIdGenerator();
    final String s = generator.nextId();
    final BytesValue bytesValue = BytesValue.fromHexString(s);
    assertEquals(s, bytesValue.toString());
  }
}
