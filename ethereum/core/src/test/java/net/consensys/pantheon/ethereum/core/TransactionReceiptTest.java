package net.consensys.pantheon.ethereum.core;

import static org.junit.Assert.assertEquals;

import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import org.junit.Test;

public class TransactionReceiptTest {

  @Test
  public void toFromRlp() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final TransactionReceipt receipt = gen.receipt();
    final TransactionReceipt copy =
        TransactionReceipt.readFrom(RLP.input(RLP.encode(receipt::writeTo)));
    assertEquals(receipt, copy);
  }
}
