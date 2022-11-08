/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and lengthations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.encoding.ssz;

import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.ssz.SSZWriter;
import org.apache.tuweni.units.bigints.UInt256;

public class SignedBlobTransaction implements SSZUtil.SSZType {
  public static final int MAX_CALL_DATA_SIZE = 16777216; // 2**24

  BlobTransaction message = new BlobTransaction();
  ECDSASignature signature = new ECDSASignature();

  public BlobTransaction getMessage() {
    return message;
  }

  public void setMessage(final BlobTransaction message) {
    this.message = message;
  }

  public ECDSASignature getSignature() {
    return signature;
  }

  public void setSignature(final ECDSASignature signature) {
    this.signature = signature;
  }

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public long decodeFrom(final SSZReader input, final long length) {
    return SSZUtil.decodeContainer(input, length, message, signature);
  }

  @Override
  public long encodeInto(final SSZWriter rlpOutput) {
    return SSZUtil.encodeContainer(rlpOutput, message, signature);
  }

  @Override
  public long getSize() {
    return message.getSize() + signature.getSize();
  }

  public static class BlobTransaction implements SSZUtil.SSZType {
    SSZUtil.Uint256SSZWrapper chainId = new SSZUtil.Uint256SSZWrapper();
    SSZUtil.Uint64SSZWrapper nonce = new SSZUtil.Uint64SSZWrapper();
    SSZUtil.Uint256SSZWrapper maxPriorityFeePerGas = new SSZUtil.Uint256SSZWrapper();
    SSZUtil.Uint256SSZWrapper maxFeePerGas = new SSZUtil.Uint256SSZWrapper();
    SSZUtil.Uint64SSZWrapper gas = new SSZUtil.Uint64SSZWrapper();
    MaybeAddress address = new MaybeAddress();
    SSZUtil.Uint256SSZWrapper value = new SSZUtil.Uint256SSZWrapper();
    Data data = new Data(MAX_CALL_DATA_SIZE);

    SSZUtil.SSZVariableSizeList<AccessTuple> accessList =
        new SSZUtil.SSZVariableSizeList<>(AccessTuple::new);
    SSZUtil.Uint256SSZWrapper maxFeePerData = new SSZUtil.Uint256SSZWrapper();

    SSZUtil.SSZFixedSizeList<VersionedHash> blobVersionedHashes =
        new SSZUtil.SSZFixedSizeList<>(VersionedHash::new);

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      return SSZUtil.decodeContainer(
          input,
          length,
          chainId,
          nonce,
          maxPriorityFeePerGas,
          maxFeePerGas,
          gas,
          address,
          value,
          data,
          accessList,
          maxFeePerData,
          blobVersionedHashes);
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      return SSZUtil.encodeContainer(
          rlpOutput,
          chainId,
          nonce,
          maxPriorityFeePerGas,
          maxFeePerGas,
          gas,
          address,
          value,
          data,
          accessList,
          maxFeePerData,
          blobVersionedHashes);
    }

    @Override
    public long getSize() {
      return chainId.getSize()
          + nonce.getSize()
          + maxPriorityFeePerGas.getSize()
          + maxFeePerGas.getSize()
          + gas.getSize()
          + address.getSize()
          + value.getSize()
          + data.getSize()
          + accessList.getSize()
          + maxFeePerData.getSize()
          + blobVersionedHashes.getSize();
    }

    public UInt256 getChainId() {
      return chainId.getValue();
    }

    public Long getNonce() {
      return nonce.getValue();
    }

    public UInt256 getMaxPriorityFeePerGas() {
      return maxPriorityFeePerGas.getValue();
    }

    public UInt256 getMaxFeePerGas() {
      return maxFeePerGas.getValue();
    }

    public Long getGas() {
      return gas.getValue();
    }

    public Optional<Address> getAddress() {
      return address.getAddress().map(SSZUtil.SSZAddress::getAddress);
    }

    public SSZUtil.Uint256SSZWrapper getValue() {
      return value;
    }

    public Bytes getData() {
      return data.getData();
    }

    public List<AccessTuple> getAccessList() {
      return accessList.getList();
    }

    public SSZUtil.Uint256SSZWrapper getMaxFeePerData() {
      return maxFeePerData;
    }

    public SSZUtil.SSZFixedSizeList<VersionedHash> getBlobVersionedHashes() {
      return blobVersionedHashes;
    }
  }

  public static class Data implements SSZUtil.SSZType {

    private final int maxCallDataSize;
    private Bytes data = Bytes.EMPTY;

    public Data(final int maxCallDataSize) {
      this.maxCallDataSize = maxCallDataSize;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      if (length > maxCallDataSize) {
        throw new IllegalArgumentException("Call data size exceeds maximum allowed size");
      }
      data = input.readFixedBytes((int) length);
      return length;
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      rlpOutput.writeFixedBytes(data);
      return data.size();
    }

    @Override
    public long getSize() {
      return data.size();
    }

    public Bytes getData() {
      return data;
    }
  }

  public static class MaybeAddress implements SSZUtil.SSZType {

    private SSZUtil.SSZAddress address;

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      return SSZUtil.decodeUnion(
          input,
          length,
          List.of(
              SSZUtil.SSZNone::new,
              () -> {
                address = new SSZUtil.SSZAddress();
                return address;
              }));
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      if (address == null) {
        return SSZUtil.encodeUnion(rlpOutput, 0, new SSZUtil.SSZNone());
      } else {
        return SSZUtil.encodeUnion(rlpOutput, 1, address);
      }
    }

    @Override
    public long getSize() {
      return address == null ? 1 : address.getSize() + 1;
    }

    public Optional<SSZUtil.SSZAddress> getAddress() {
      return Optional.ofNullable(address);
    }
  }

  public static class AccessTuple implements SSZUtil.SSZType {
    SSZUtil.SSZAddress address = new SSZUtil.SSZAddress();
    SSZUtil.SSZVariableSizeList<SSZUtil.Uint256SSZWrapper> storageKeys =
        new SSZUtil.SSZVariableSizeList<>(SSZUtil.Uint256SSZWrapper::new);

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      return SSZUtil.decodeContainer(input, length, address, storageKeys);
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      return SSZUtil.encodeContainer(rlpOutput, address, storageKeys);
    }

    @Override
    public long getSize() {
      return address.getSize() + storageKeys.getSize();
    }

    public Address getAddress() {
      return address.getAddress();
    }

    public List<Bytes32> getStorageKeys() {
      return storageKeys.getList().stream()
          .map(SSZUtil.Uint256SSZWrapper::getValue)
          .map(UInt256::toBytes)
          .map(Bytes32::wrap)
          .collect(Collectors.toList());
    }
  }

  public static class ECDSASignature implements SSZUtil.SSZFixedType {
    SSZUtil.BooleanSSZWrapper yParity = new SSZUtil.BooleanSSZWrapper();
    SSZUtil.Uint256SSZWrapper r = new SSZUtil.Uint256SSZWrapper();
    SSZUtil.Uint256SSZWrapper s = new SSZUtil.Uint256SSZWrapper();

    @Override
    public boolean isFixedSize() {
      return true;
    }

    @Override
    public int getFixedSize() {
      return 1 + 32 + 32;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      return SSZUtil.decodeContainer(input, length, yParity, r, s);
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      return SSZUtil.encodeContainer(rlpOutput, yParity, r, s);
    }

    public SSZUtil.BooleanSSZWrapper getyParity() {
      return yParity;
    }

    public SSZUtil.Uint256SSZWrapper getR() {
      return r;
    }

    public SSZUtil.Uint256SSZWrapper getS() {
      return s;
    }
  }

  public static class VersionedHash implements SSZUtil.SSZFixedType {

    private Bytes bytes;

    @Override
    public boolean isFixedSize() {
      return true;
    }

    @Override
    public int getFixedSize() {
      return 32;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      bytes = input.readFixedBytes(32);
      return 32;
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      rlpOutput.writeFixedBytes(bytes);
      return 32;
    }

    public Bytes getBytes() {
      return bytes;
    }
  }
}
