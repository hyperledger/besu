package tech.pegasys.pantheon.ethereum.blockcreation;

import java.util.Iterator;

public class IncrementingNonceGenerator implements Iterable<Long> {

  private long nextValue;

  public IncrementingNonceGenerator(final long nextValue) {
    this.nextValue = nextValue;
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
        return nextValue++;
      }
    };
  }
}
