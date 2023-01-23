package org.hyperledger.besu.ethereum.core.encoding.ssz;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZFixedSizeTypeList;
import org.apache.tuweni.ssz.SSZFixedSizeVector;
import org.apache.tuweni.ssz.SSZReadable;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.ssz.SSZVariableSizeTypeList;
import org.apache.tuweni.ssz.SSZWritable;
import org.apache.tuweni.ssz.SSZWriter;
import org.apache.tuweni.units.bigints.UInt256;

public class TransactionNetworkPayload implements SSZReadable, SSZWritable {
  public static final int KZG_COMMITMENT_SIZE = 48;
  public static final int FIELD_ELEMENTS_PER_BLOB = 4096;
  public static final int ELEMENT_SIZE = 32;
  SingedBlobTransaction signedBlobTransaction = new SingedBlobTransaction();
  SSZFixedSizeTypeList<KZGCommitment> kzgCommitments =
      new SSZFixedSizeTypeList<>(KZG_COMMITMENT_SIZE, KZGCommitment::new);
  SSZFixedSizeTypeList<Blob> blobs =
      new SSZFixedSizeTypeList<>(FIELD_ELEMENTS_PER_BLOB * ELEMENT_SIZE, Blob::new);

  KZGProof kzgProof = new KZGProof();

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public void populateFromReader(final SSZReader reader) {
    reader.readAsContainer(signedBlobTransaction, kzgCommitments, blobs, kzgProof);
  }

  @Override
  public void writeTo(final SSZWriter writer) {
    writer.writeAsContainer(signedBlobTransaction, kzgCommitments, blobs, kzgProof);
  }

  public SingedBlobTransaction getSignedBlobTransaction() {
    return signedBlobTransaction;
  }

  public SSZFixedSizeTypeList<KZGCommitment> getKzgCommitments() {
    return kzgCommitments;
  }

  public SSZFixedSizeTypeList<Blob> getBlobs() {
    return blobs;
  }

  public KZGProof getKzgProof() {
    return kzgProof;
  }

  public static class SingedBlobTransaction implements SSZReadable, SSZWritable {
    private final BlobTransaction message = new BlobTransaction();
    private final ECDSASignature signature = new ECDSASignature();

    @Override
    public boolean isFixed() {
      return false;
    }

    @Override
    public void populateFromReader(final SSZReader reader) {
      reader.readAsContainer(message, signature);
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeAsContainer(message, signature);
    }

    public BlobTransaction getMessage() {
      return message;
    }

    public ECDSASignature getSignature() {
      return signature;
    }

    public static class BlobTransaction implements SSZReadable, SSZWritable {
      final Data data = new Data();
      UInt256 chainId;
      long nonce;
      UInt256 maxPriorityFeePerGas;
      UInt256 maxFeePerGas;
      long gas;
      AddressUnion address = new AddressUnion();
      UInt256 value;
      SSZVariableSizeTypeList<AccessTuple> accessList =
          new SSZVariableSizeTypeList<>(AccessTuple::new);
      UInt256 maxFeePerData;

      SSZFixedSizeTypeList<VersionedHash> blobVersionedHashes =
          new SSZFixedSizeTypeList<>(32, VersionedHash::new);

      @Override
      public boolean isFixed() {
        return false;
      }

      @Override
      public void populateFromReader(final SSZReader reader) {
        reader.readAsContainer(
            r -> chainId = r.readUInt256(),
            r -> nonce = r.readUInt64(),
            r -> maxPriorityFeePerGas = r.readUInt256(),
            r -> maxFeePerGas = r.readUInt256(),
            r -> gas = r.readUInt64(),
            address,
            r -> value = r.readUInt256(),
            data,
            accessList,
            r -> maxFeePerData = r.readUInt256(),
            blobVersionedHashes);
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        writer.writeAsContainer(
            w -> w.writeUInt256(chainId),
            w -> w.writeUInt64(nonce),
            w -> w.writeUInt256(maxPriorityFeePerGas),
            w -> w.writeUInt256(maxFeePerGas),
            w -> w.writeUInt64(gas),
            address,
            w -> w.writeUInt256(value),
            data,
            accessList,
            w -> w.writeUInt256(maxFeePerData),
            blobVersionedHashes);
      }

      public Bytes getData() {
        return data.getData();
      }

      public UInt256 getChainId() {
        return chainId;
      }

      public long getNonce() {
        return nonce;
      }

      public UInt256 getMaxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
      }

      public UInt256 getMaxFeePerGas() {
        return maxFeePerGas;
      }

      public long getGas() {
        return gas;
      }

      public Optional<Address> getAddress() {
        return Optional.ofNullable(address.getAddress()).map(Address::wrap);
      }

      public UInt256 getValue() {
        return value;
      }

      public List<AccessTuple> getAccessList() {
        return accessList.getElements();
      }

      public UInt256 getMaxFeePerData() {
        return maxFeePerData;
      }

      public List<Hash> getBlobVersionedHashes() {
        return blobVersionedHashes.getElements().stream()
            .map(VersionedHash::toHash)
            .collect(Collectors.toList());
      }
    }

    public static class AddressUnion implements SSZReadable, SSZWritable {
      private Bytes address;

      @Override
      public boolean isFixed() {
        return false;
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        if (address == null) {
          writer.writeUInt8(0);
        } else {
          writer.writeUInt8(1);
          writer.writeAddress(address);
        }
      }

      @Override
      public void populateFromReader(final SSZReader reader) {
        final int type = reader.readUInt8();
        if (type == 1) {
          address = reader.readAddress();
        }
      }

      public Bytes getAddress() {
        return address;
      }
    }

    public static class Data implements SSZReadable, SSZWritable {
      public static final int MAX_CALL_DATA_SIZE = 16777216; // 2**24

      Bytes data;

      @Override
      public boolean isFixed() {
        return false;
      }

      @Override
      public void populateFromReader(final SSZReader reader) {
        if (reader.isComplete()) {
          return;
        }
        data = reader.consumeRemainingBytes(MAX_CALL_DATA_SIZE);
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        if (data != null) {
          writer.writeBytes(data);
        }
      }

      public Bytes getData() {
        return data;
      }
    }

    public static class AccessTuple implements SSZReadable, SSZWritable {
      Bytes address;
      SSZFixedSizeTypeList<SSZUInt256Wrapper> storageKeys =
          new SSZFixedSizeTypeList<>(ELEMENT_SIZE, SSZUInt256Wrapper::new);

      @Override
      public boolean isFixed() {
        return false;
      }

      @Override
      public void populateFromReader(final SSZReader reader) {
        reader.readAsContainer(r -> address = r.readAddress(), storageKeys);
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        writer.writeAsContainer(w -> w.writeAddress(address), storageKeys);
      }

      public Address getAddress() {
        return Address.wrap(address);
      }

      public List<Bytes32> getStorageKeys() {
        return storageKeys.getElements().stream()
            .map(sszuInt256Wrapper -> sszuInt256Wrapper.getData().toBytes())
            .collect(Collectors.toList());
      }
    }

    public static class VersionedHash implements SSZReadable, SSZWritable {
      private Bytes bytes;

      @Override
      public boolean isFixed() {
        return true;
      }

      @Override
      public void populateFromReader(final SSZReader reader) {
        reader.readAsContainer(r -> bytes = r.readFixedBytes(32));
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        writer.writeAsContainer(w -> w.writeFixedBytes(bytes));
      }

      public Bytes getBytes() {
        return bytes;
      }

      public Hash toHash() {
        return Hash.wrap(Bytes32.wrap(bytes));
      }
    }

    public static class ECDSASignature implements SSZReadable, SSZWritable {
      boolean parity;
      UInt256 r;
      UInt256 s;

      @Override
      public void populateFromReader(final SSZReader reader) {
        parity = reader.readBoolean();
        r = reader.readUInt256();
        s = reader.readUInt256();
      }

      @Override
      public void writeTo(final SSZWriter writer) {
        writer.writeBoolean(parity);
        writer.writeUInt256(r);
        writer.writeUInt256(s);
      }

      public boolean isParity() {
        return parity;
      }

      public UInt256 getR() {
        return r;
      }

      public UInt256 getS() {
        return s;
      }
    }
  }

  public static class KZGCommitment implements SSZReadable, SSZWritable {
    Bytes data;

    @Override
    public void populateFromReader(final SSZReader reader) {
      data = reader.readFixedBytes(KZG_COMMITMENT_SIZE);
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeFixedBytes(data);
    }

    public Bytes getData() {
      return data;
    }
  }

  public static class Blob implements SSZReadable, SSZWritable {
    SSZFixedSizeVector<SSZUInt256Wrapper> vector =
        new SSZFixedSizeVector<>(FIELD_ELEMENTS_PER_BLOB, ELEMENT_SIZE, SSZUInt256Wrapper::new);

    @Override
    public void populateFromReader(final SSZReader reader) {
      vector.populateFromReader(reader);
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      vector.writeTo(writer);
    }

    public List<SSZUInt256Wrapper> getElements() {
      return vector.getElements();
    }
  }

  public static class SSZUInt256Wrapper implements SSZReadable, SSZWritable {
    UInt256 data;

    @Override
    public void populateFromReader(final SSZReader reader) {
      data = reader.readUInt256();
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeUInt256(data);
    }

    public UInt256 getData() {
      return data;
    }
  }

  public static class KZGProof implements SSZReadable, SSZWritable {
    Bytes bytes;

    @Override
    public void populateFromReader(final SSZReader reader) {
      bytes = reader.readFixedBytes(48);
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeFixedBytes(bytes);
    }

    public Bytes getBytes() {
      return bytes;
    }
  }
}
