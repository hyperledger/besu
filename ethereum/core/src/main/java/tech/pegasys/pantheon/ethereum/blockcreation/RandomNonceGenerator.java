package net.consensys.pantheon.ethereum.blockcreation;

import net.consensys.pantheon.crypto.SecureRandomProvider;

import java.util.Iterator;
import java.util.Random;

/** Creates an everlasting random long value (for use in nonces). */
public class RandomNonceGenerator implements Iterable<Long> {

  private final Random longGenerator;

  public RandomNonceGenerator() {
    this.longGenerator = SecureRandomProvider.publicSecureRandom();
  }

  @Override
  public Iterator<Long> iterator() {
    return new Iterator<Long>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Long next() {
        return longGenerator.nextLong();
      }
    };
  }
}
