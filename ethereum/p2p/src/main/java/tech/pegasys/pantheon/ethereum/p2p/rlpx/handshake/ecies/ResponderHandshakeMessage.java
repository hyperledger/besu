package tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public interface ResponderHandshakeMessage {

  SECP256K1.PublicKey getEphPublicKey();

  Bytes32 getNonce();

  BytesValue encode();
}
