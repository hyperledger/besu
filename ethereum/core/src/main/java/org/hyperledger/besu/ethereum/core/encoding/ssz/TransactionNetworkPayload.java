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
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.core.encoding.ssz;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZReader;
import org.apache.tuweni.ssz.SSZWriter;

public class TransactionNetworkPayload implements SSZUtil.SSZType {
  public static final int FIELD_ELEMENTS_PER_BLOB = 4096;
  SignedBlobTransaction transaction = new SignedBlobTransaction();
  SSZUtil.SSZFixedSizeList<KZGCommitment> kzgCommitments =
      new SSZUtil.SSZFixedSizeList<>(KZGCommitment::new);
  SSZUtil.SSZFixedSizeList<Blob> blobSSZVariableSizeList =
      new SSZUtil.SSZFixedSizeList<>(Blob::new);

  KZGProof kzgProof = new KZGProof();

  @Override
  public boolean isFixedSize() {
    return false;
  }

  @Override
  public long decodeFrom(final SSZReader input, final long length) {
    return SSZUtil.decodeContainer(
        input, length, transaction, kzgCommitments, blobSSZVariableSizeList, kzgProof);
  }

  @Override
  public long encodeInto(final SSZWriter rlpOutput) {
    return SSZUtil.encodeContainer(
        rlpOutput, transaction, kzgCommitments, blobSSZVariableSizeList, kzgProof);
  }

  @Override
  public long getSize() {
    return transaction.getSize()
        + kzgCommitments.getSize()
        + blobSSZVariableSizeList.getSize()
        + kzgProof.getSize();
  }

  public SignedBlobTransaction getTransaction() {
    return transaction;
  }

  public SSZUtil.SSZFixedSizeList<KZGCommitment> getKzgCommitments() {
    return kzgCommitments;
  }

  public SSZUtil.SSZFixedSizeList<Blob> getBlobSSZVariableSizeList() {
    return blobSSZVariableSizeList;
  }

  public KZGProof getKzgProof() {
    return kzgProof;
  }

  public static class KZGCommitment implements SSZUtil.SSZFixedType {
    Bytes bytes;

    @Override
    public boolean isFixedSize() {
      return true;
    }

    @Override
    public int getFixedSize() {
      return 48;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      bytes = input.readFixedBytes(48);
      return 48;
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      rlpOutput.writeFixedBytes(bytes);
      return 48;
    }
  }

  public static class Blob implements SSZUtil.SSZFixedType {

    SSZUtil.FixedSizeSSZVector<SSZUtil.Uint256SSZWrapper> vector =
        new SSZUtil.FixedSizeSSZVector<>(SSZUtil.Uint256SSZWrapper::new, FIELD_ELEMENTS_PER_BLOB);

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      return vector.decodeFrom(input, length);
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      return vector.encodeInto(rlpOutput);
    }

    @Override
    public int getFixedSize() {
      return vector.getFixedSize();
    }
  }

  public static class KZGProof implements SSZUtil.SSZFixedType {
    Bytes bytes;

    @Override
    public boolean isFixedSize() {
      return true;
    }

    @Override
    public int getFixedSize() {
      return 48;
    }

    @Override
    public long decodeFrom(final SSZReader input, final long length) {
      bytes = input.readFixedBytes(48);
      return 48;
    }

    @Override
    public long encodeInto(final SSZWriter rlpOutput) {
      rlpOutput.writeFixedBytes(bytes);
      return 48;
    }
  }
}
