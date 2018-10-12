package tech.pegasys.pantheon.ethereum.blockcreation;

public class CoinbaseNotSetException extends RuntimeException {

  public CoinbaseNotSetException(final String message) {
    super(message);
  }
}
