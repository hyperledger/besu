package net.consensys.pantheon.ethereum.core;

import static org.junit.Assert.assertEquals;

import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import org.junit.Test;

public class LogTest {

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Log log = gen.log();
    final Log copy = Log.readFrom(RLP.input(RLP.encode(log::writeTo)));
    assertEquals(log, copy);
  }
}
