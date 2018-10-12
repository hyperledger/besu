package tech.pegasys.pantheon.ethereum.rlp;

/** Exception thrown if an RLP input is corrupted and cannot be decoded properly. */
public class CorruptedRLPInputException extends RLPException {
  CorruptedRLPInputException(final String message) {
    super(message);
  }
}
