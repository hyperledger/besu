package net.consensys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

public interface ResponderHandshakeMessage {

  SECP256K1.PublicKey getEphPublicKey();

  Bytes32 getNonce();

  BytesValue encode();
}
