package org.hyperledger.besu.ethereum.blockcreation.stratum;

public class StratumServerException extends RuntimeException {

  public StratumServerException(final String message) {
    super(message);
  }
}
