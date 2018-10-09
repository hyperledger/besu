package net.consensys.pantheon.ethereum.blockcreation;

public class CoinbaseNotSetException extends RuntimeException {

  public CoinbaseNotSetException(final String message) {
    super(message);
  }
}
