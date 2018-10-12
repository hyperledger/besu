package net.consensys.pantheon.ethereum.p2p.wire;

public interface SubProtocol {

  /**
   * Returns the 3 character ascii name of this Wire Sub-protocol.
   *
   * @return the name of this sub-protocol
   */
  String getName();

  /**
   * The number of message codes to reserve for the given version of this sub-protocol.
   *
   * @param protocolVersion the version of the protocol
   * @return the number of reserved message codes in the given version of the sub-protocol
   */
  int messageSpace(int protocolVersion);

  /**
   * Returns true if the given protocol version supports the given message code.
   *
   * @param protocolVersion the version of the protocol
   * @param code the message code to check
   * @return true if the given protocol version supports the given message code
   */
  boolean isValidMessageCode(int protocolVersion, int code);
}
