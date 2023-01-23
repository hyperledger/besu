package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;

import java.util.List;

public class BlobsBundleV1 {

    private final Hash blockHash;

    private final List<Bytes> kzgs;

    private final List<Bytes> blobs;

    public BlobsBundleV1(final Hash blockHash, final List<Bytes> kzgs, final List<Bytes> blobs) {
        this.blockHash = blockHash;
        this.kzgs = kzgs;
        this.blobs = blobs;
    }
}
