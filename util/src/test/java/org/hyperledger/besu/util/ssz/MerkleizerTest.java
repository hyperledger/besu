package org.hyperledger.besu.util.ssz;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MerkleizerTest {

    @Test
    public void testMerkleizeChunks() {
        Merkleizer merkleizer = new Merkleizer();
        List<Bytes32> chunks = List.of(
                Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00),
                Bytes32.leftPad(Bytes.fromHexString("02"), (byte) 0x00),
                Bytes32.leftPad(Bytes.fromHexString("03"), (byte) 0x00),
                Bytes32.leftPad(Bytes.fromHexString("04"), (byte) 0x00)
        );

        Bytes32 result = merkleizer.merkleizeChunks(chunks);
        Assertions.assertEquals("0xd7351286df93d1e31e51c21378fba9f9c7c14c3a8f621065069809b6e635ae0a", result.toHexString());
    }

    @Test
    public void testMerkleizeChunksPadToSize() {
        Merkleizer merkleizer = new Merkleizer();
        List<Bytes32> chunks = List.of(
                Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00),
                Bytes32.leftPad(Bytes.fromHexString("02"), (byte) 0x00),
                Bytes32.leftPad(Bytes.fromHexString("03"), (byte) 0x00)
        );

        Bytes32 result = merkleizer.merkleizeChunks(chunks, 4);
        Assertions.assertEquals("0xdfea42101f94476e3b2e26f2a3e23505a696a6fd3d3ded213cece4adb19cb9ac", result.toHexString());
    }

    @Test
    public void testMixinLength() {
        Merkleizer merkleizer = new Merkleizer();
        Bytes32 rootHash = Bytes32.leftPad(Bytes.fromHexString("01"), (byte) 0x00);

        Bytes32 result = merkleizer.mixinLength(rootHash, UInt256.ONE);
        Assertions.assertEquals("0x9460060257cf1b1c720d01aab3842bd30abb9ce86d8a5221043c7eb7961d1364", result.toHexString());
    }
}
