package org.hyperledger.besu.util.snappy;

import org.xerial.snappy.SnappyFramedInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SnappyFactory {

    public SnappyFramedInputStream createFramedInputStream(final byte[] compressedData) throws IOException {
        return new SnappyFramedInputStream(new ByteArrayInputStream(compressedData));
    }
}
