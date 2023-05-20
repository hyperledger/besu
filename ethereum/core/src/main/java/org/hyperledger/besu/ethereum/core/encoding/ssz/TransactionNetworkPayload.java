/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.core.encoding.ssz;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZFixedSizeTypeList;
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

  public void setKzgCommitments(final SSZFixedSizeTypeList<KZGCommitment> kzgCommitments) {
    this.kzgCommitments = kzgCommitments;
  }

  public void setBlobs(final SSZFixedSizeTypeList<Blob> blobs) {
    this.blobs = blobs;
  }

  public void setKzgProof(final KZGProof kzgProof) {
    this.kzgProof = kzgProof;
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
      UInt256 maxFeePerDataGas;

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
            r -> maxFeePerDataGas = r.readUInt256(),
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
            w -> w.writeUInt256(maxFeePerDataGas),
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

      public UInt256 getMaxFeePerDataGas() {
        return maxFeePerDataGas;
      }

      public List<Hash> getBlobVersionedHashes() {
        return blobVersionedHashes.getElements().stream()
            .map(VersionedHash::toHash)
            .collect(Collectors.toList());
      }

      public void setChainId(final UInt256 chainId) {
        this.chainId = chainId;
      }

      public void setNonce(final long nonce) {
        this.nonce = nonce;
      }

      public void setMaxPriorityFeePerGas(final UInt256 maxPriorityFeePerGas) {
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
      }

      public void setMaxFeePerGas(final UInt256 maxFeePerGas) {
        this.maxFeePerGas = maxFeePerGas;
      }

      public void setGas(final long gas) {
        this.gas = gas;
      }

      public void setAddress(final Optional<Address> address) {
        this.address.setAddress(address);
      }

      public void setValue(final UInt256 value) {
        this.value = value;
      }

      public void setAccessList(final SSZVariableSizeTypeList<AccessTuple> accessList) {
        this.accessList = accessList;
      }

      public void setMaxFeePerDataGas(final UInt256 maxFeePerData) {
        this.maxFeePerDataGas = maxFeePerData;
      }

      public void setBlobVersionedHashes(final List<Hash> blobVersionedHashes) {
        this.blobVersionedHashes.getElements().clear();
        this.blobVersionedHashes
            .getElements()
            .addAll(
                blobVersionedHashes.stream()
                    .map(VersionedHash::fromHash)
                    .collect(Collectors.toList()));
      }

      public void setData(final Bytes data) {
        this.data.setData(data);
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

      public void setAddress(final Optional<Address> address) {
        this.address = address.orElse(null);
      }
    }

    public static class Data implements SSZReadable, SSZWritable {
      public static final int MAX_CALL_DATA_SIZE = 16777216; // 2**24

      Bytes data = Bytes.EMPTY;

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
        if (data != Bytes.EMPTY) {
          // TODO: update tuweni to implement ByteList[] correctly.
          // spec relies on delta between offsets to determine variable lengths, length is not
          // explicitly encoded.
          for (byte b : data.toArray()) {
            writer.writeUInt8(b);
          }
        }
      }

      public Bytes getData() {
        return data;
      }

      public void setData(final Bytes data) {
        this.data = data;
      }
    }

    public static class AccessTuple implements SSZReadable, SSZWritable {
      Bytes address;
      SSZFixedSizeTypeList<SSZByte32Wrapper> storageKeys =
          new SSZFixedSizeTypeList<>(ELEMENT_SIZE, SSZByte32Wrapper::new);

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
            .map(sszByte32Wrapper -> sszByte32Wrapper.getData())
            .collect(Collectors.toList());
      }

      public void setAddress(final Bytes address) {
        this.address = address;
      }

      public void setStorageKeys(final List<Bytes32> storageKeys) {
        this.storageKeys.getElements().clear();
        this.storageKeys
            .getElements()
            .addAll(
                storageKeys.stream()
                    .map(
                        bytes32 -> {
                          SSZByte32Wrapper sszByte32Wrapper = new SSZByte32Wrapper();
                          sszByte32Wrapper.setData(UInt256.fromBytes(bytes32));
                          return sszByte32Wrapper;
                        })
                    .collect(Collectors.toList()));
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

      public static VersionedHash fromHash(final Hash hash) {
        VersionedHash versionedHash = new VersionedHash();
        versionedHash.bytes = hash;
        return versionedHash;
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

      public void setParity(final boolean parity) {
        this.parity = parity;
      }

      public void setR(final UInt256 r) {
        this.r = r;
      }

      public void setS(final UInt256 s) {
        this.s = s;
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
    Bytes bytes;

    @Override
    public void populateFromReader(final SSZReader reader) {
      bytes = reader.readFixedBytes(FIELD_ELEMENTS_PER_BLOB * ELEMENT_SIZE);
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeFixedBytes(bytes);
    }

    public Bytes getBytes() {
      return bytes;
    }
  }

  public static class SSZByte32Wrapper implements SSZReadable, SSZWritable {
    Bytes32 data;

    @Override
    public void populateFromReader(final SSZReader reader) {
      data = Bytes32.wrap(reader.readFixedBytes(32));
    }

    @Override
    public void writeTo(final SSZWriter writer) {
      writer.writeBytes(data);
    }

    public Bytes32 getData() {
      return data;
    }

    public void setData(final Bytes32 data) {
      this.data = data;
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
