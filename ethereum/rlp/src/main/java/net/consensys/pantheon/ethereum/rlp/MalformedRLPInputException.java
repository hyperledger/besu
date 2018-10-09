package net.consensys.pantheon.ethereum.rlp;

/**
 * Exception thrown if an RLP input is strictly malformed, but in such a way that can be processed
 * by a lenient RLP decoder.
 */
public class MalformedRLPInputException extends RLPException {
  MalformedRLPInputException(final String message) {
    super(message);
  }
}
