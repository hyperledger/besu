/*
 * Copyright Besu contributors.
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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.eth.transactions.layered.BaseTransactionPoolTest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import com.google.common.collect.Sets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

@EnabledOnOs(OS.LINUX)
public class PendingTransactionEstimatedMemorySizeTest extends BaseTransactionPoolTest {
  private static final Set<Class<?>> SHARED_CLASSES =
      Set.of(SignatureAlgorithm.class, TransactionType.class);
  private static final Set<String> COMMON_CONSTANT_FIELD_PATHS =
      Set.of(".value.ctor", ".hashNoSignature");
  private static final Set<String> EIP1559_EIP4844_CONSTANT_FIELD_PATHS =
      Sets.union(COMMON_CONSTANT_FIELD_PATHS, Set.of(".gasPrice"));
  private static final Set<String> FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS =
      Sets.union(COMMON_CONSTANT_FIELD_PATHS, Set.of(".maxFeePerGas", ".maxPriorityFeePerGas"));
  private static final Set<String> VARIABLE_SIZE_PATHS =
      Set.of(".chainId", ".to", ".payload", ".maybeAccessList");

  @Test
  public void toSize() {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10, 0);
    Transaction txTo =
        preparedTx.to(Optional.of(Address.extract(Bytes32.random()))).createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txTo.writeTo(rlpOut);

    txTo = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txTo.getSender());
    System.out.println(txTo.getHash());
    System.out.println(txTo.getSize());

    Optional<Address> to = txTo.getTo();
    final ClassLayout cl = ClassLayout.parseInstance(to);
    System.out.println(cl.toPrintable());
    LongAdder size = new LongAdder();
    size.add(cl.instanceSize());
    System.out.println(size);

    GraphVisitor gv =
        gpr -> {
          size.add(gpr.size());
          System.out.println(
              "("
                  + size
                  + ")["
                  + gpr.size()
                  + ", "
                  + gpr.path()
                  + ", "
                  + gpr.klass().toString()
                  + "]");
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(to);

    System.out.println("Optional To size: " + size);

    assertThat(size.sum()).isEqualTo(PendingTransaction.OPTIONAL_TO_MEMORY_SIZE);
  }

  @Test
  public void payloadSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10, 0);
    Transaction txPayload = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txPayload.writeTo(rlpOut);

    txPayload =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txPayload.getSender());
    System.out.println(txPayload.getHash());
    System.out.println(txPayload.getSize());

    final Bytes payload = txPayload.getPayload();
    final ClassLayout cl = ClassLayout.parseInstance(payload);
    System.out.println(cl.toPrintable());
    LongAdder size = new LongAdder();
    size.add(cl.instanceSize());
    System.out.println("Base payload size: " + size);

    assertThat(size.sum()).isEqualTo(PendingTransaction.PAYLOAD_BASE_MEMORY_SIZE);
  }

  @Test
  public void chainIdSize() {

    BigInteger chainId = BigInteger.valueOf(1);
    Optional<BigInteger> maybeChainId = Optional.of(chainId);

    final ClassLayout cl = ClassLayout.parseInstance(maybeChainId);
    System.out.println(cl.toPrintable());
    LongAdder size = new LongAdder();
    size.add(cl.instanceSize());
    System.out.println("Base chainId size: " + size);

    GraphVisitor gv =
        gpr -> {
          size.add(gpr.size());
          System.out.println(
              "("
                  + size
                  + ")["
                  + gpr.size()
                  + ", "
                  + gpr.path()
                  + ", "
                  + gpr.klass().toString()
                  + "]");
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(maybeChainId);

    assertThat(size.sum()).isEqualTo(PendingTransaction.OPTIONAL_CHAIN_ID_MEMORY_SIZE);
  }

  @Test
  public void kgzCommitmentsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getKzgCommitments(),
        PendingTransaction.BASE_LIST_SIZE,
        PendingTransaction.KZG_COMMITMENT_OR_PROOF_SIZE);
  }

  @Test
  public void kgzProofsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getKzgProofs(),
        PendingTransaction.BASE_LIST_SIZE,
        PendingTransaction.KZG_COMMITMENT_OR_PROOF_SIZE);
  }

  @Test
  public void blobsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getBlobs(),
        PendingTransaction.BASE_LIST_SIZE,
        PendingTransaction.BLOB_SIZE);
  }

  @Test
  public void versionedHashesSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getVersionedHashes(),
        PendingTransaction.BASE_LIST_SIZE,
        PendingTransaction.VERSIONED_HASH_SIZE);
  }

  private void blobsWithCommitmentsFieldSize(
      final Function<Transaction, List<? extends Object>> containerExtractor,
      final long containerSize,
      final long itemSize) {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.BLOB, 10, Wei.of(500), 10, 1);
    Transaction txBlob = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    TransactionEncoder.encodeRLP(txBlob, rlpOut, EncodingContext.POOLED_TRANSACTION);

    txBlob =
        TransactionDecoder.decodeRLP(
                new BytesValueRLPInput(rlpOut.encoded(), false), EncodingContext.POOLED_TRANSACTION)
            .detachedCopy();
    System.out.println(txBlob.getSender());
    System.out.println(txBlob.getHash());
    System.out.println(txBlob.getSize());

    final List<? extends Object> list = containerExtractor.apply(txBlob);

    final long cSize = sizeOfField(list, ".elements[");

    System.out.println("Container size: " + cSize);

    assertThat(cSize).isEqualTo(containerSize);

    final Object item = list.get(0);
    final long iSize = sizeOfField(item);

    System.out.println("Item size: " + iSize);

    assertThat(iSize).isEqualTo(itemSize);
  }

  @Test
  public void blobsWithCommitmentsSize() {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.BLOB, 10, Wei.of(500), 10, 1);
    Transaction txBlob = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    TransactionEncoder.encodeRLP(txBlob, rlpOut, EncodingContext.POOLED_TRANSACTION);

    txBlob =
        TransactionDecoder.decodeRLP(
                new BytesValueRLPInput(rlpOut.encoded(), false), EncodingContext.POOLED_TRANSACTION)
            .detachedCopy();
    System.out.println(txBlob.getSender());
    System.out.println(txBlob.getHash());
    System.out.println(txBlob.getSize());

    final BlobsWithCommitments bwc = txBlob.getBlobsWithCommitments().get();
    final ClassLayout cl = ClassLayout.parseInstance(bwc);
    System.out.println(cl.toPrintable());
    System.out.println("BlobsWithCommitments size: " + cl.instanceSize());
    final ClassLayout rl = ClassLayout.parseInstance(bwc.getBlobs());
    System.out.println(rl.toPrintable());
    System.out.println("BlobQuad size:" + rl.instanceSize());

    assertThat(cl.instanceSize() + rl.instanceSize())
        .isEqualTo(PendingTransaction.BLOBS_WITH_COMMITMENTS_SIZE);
  }

  @Test
  public void pendingTransactionSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10, 0);
    Transaction txPayload = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txPayload.writeTo(rlpOut);

    txPayload =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txPayload.getSender());
    System.out.println(txPayload.getHash());
    System.out.println(txPayload.getSize());

    final PendingTransaction pendingTx = new PendingTransaction.Remote(txPayload);

    final ClassLayout cl = ClassLayout.parseInstance(pendingTx);
    System.out.println(cl.toPrintable());
    LongAdder size = new LongAdder();
    size.add(cl.instanceSize());
    System.out.println("PendingTransaction size: " + size);

    assertThat(size.sum()).isEqualTo(PendingTransaction.PENDING_TRANSACTION_MEMORY_SIZE);
  }

  @Test
  public void accessListSize() {
    System.setProperty("jol.magicFieldOffset", "true");

    final AccessListEntry ale1 =
        new AccessListEntry(Address.extract(Bytes32.random()), List.of(Bytes32.random()));

    final List<AccessListEntry> ales = List.of(ale1);

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 0, Wei.of(500), 0, 0);
    Transaction txAccessList = preparedTx.accessList(ales).createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txAccessList.writeTo(rlpOut);

    txAccessList =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txAccessList.getSender());
    System.out.println(txAccessList.getHash());
    System.out.println(txAccessList.getSize());

    final var optAL = txAccessList.getAccessList();

    final ClassLayout cl1 = ClassLayout.parseInstance(optAL);
    System.out.println(cl1.toPrintable());
    System.out.println("Optional size: " + cl1.instanceSize());

    final ClassLayout cl2 = ClassLayout.parseInstance(optAL.get());
    System.out.println(cl2.toPrintable());
    System.out.println("Optional + list size: " + cl2.instanceSize());

    assertThat(cl2.instanceSize()).isEqualTo(PendingTransaction.OPTIONAL_ACCESS_LIST_MEMORY_SIZE);

    final AccessListEntry ale = optAL.get().get(0);

    long aleSize = sizeOfField(ale, "storageKeys.elementData[");

    System.out.println("AccessListEntry container size: " + aleSize);

    assertThat(aleSize).isEqualTo(PendingTransaction.ACCESS_LIST_ENTRY_BASE_MEMORY_SIZE);

    final Bytes32 storageKey = ale.storageKeys().get(0);
    final ClassLayout cl4 = ClassLayout.parseInstance(storageKey);
    System.out.println(cl4.toPrintable());
    System.out.println("Single storage key size: " + cl4.instanceSize());

    assertThat(cl4.instanceSize())
        .isEqualTo(PendingTransaction.ACCESS_LIST_STORAGE_KEY_MEMORY_SIZE);
  }

  @Test
  public void baseEIP1559AndEIP4844TransactionMemorySize() {
    System.setProperty("jol.magicFieldOffset", "true");
    Transaction txEip1559 = createEIP1559Transaction(1, KEYS1, 10);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txEip1559.writeTo(rlpOut);

    txEip1559 =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txEip1559.getSender());
    System.out.println(txEip1559.getHash());
    System.out.println(txEip1559.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(txEip1559);
    System.out.println(cl.toPrintable());
    LongAdder eip1559size = new LongAdder();
    eip1559size.add(cl.instanceSize());
    System.out.println(eip1559size);

    final SortedSet<FieldSize> fieldSizes = new TreeSet<>();
    GraphWalker gw = getGraphWalker(EIP1559_EIP4844_CONSTANT_FIELD_PATHS, fieldSizes);

    gw.walk(txEip1559);

    fieldSizes.forEach(
        fieldSize -> {
          eip1559size.add(fieldSize.size());
          System.out.println(
              "("
                  + eip1559size
                  + ")["
                  + fieldSize.size()
                  + ", "
                  + fieldSize.path()
                  + ", "
                  + fieldSize
                  + "]");
        });

    System.out.println("Base EIP1559 size: " + eip1559size);
    assertThat(eip1559size.sum())
        .isEqualTo(PendingTransaction.EIP1559_AND_EIP4844_BASE_MEMORY_SIZE);
  }

  @Test
  public void baseFrontierAndAccessListTransactionMemorySize() {
    System.setProperty("jol.magicFieldOffset", "true");
    Transaction txFrontier = createTransaction(TransactionType.FRONTIER, 1, Wei.of(500), 0, KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txFrontier.writeTo(rlpOut);

    txFrontier =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txFrontier.getSender());
    System.out.println(txFrontier.getHash());
    System.out.println(txFrontier.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(txFrontier);
    System.out.println(cl.toPrintable());
    LongAdder frontierSize = new LongAdder();
    frontierSize.add(cl.instanceSize());
    System.out.println(frontierSize);

    final SortedSet<FieldSize> fieldSizes = new TreeSet<>();

    GraphWalker gw = getGraphWalker(FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS, fieldSizes);

    gw.walk(txFrontier);

    fieldSizes.forEach(
        fieldSize -> {
          frontierSize.add(fieldSize.size());
          System.out.println(
              "("
                  + frontierSize
                  + ")["
                  + fieldSize.size()
                  + ", "
                  + fieldSize.path()
                  + ", "
                  + fieldSize
                  + "]");
        });

    System.out.println("Base Frontier size: " + frontierSize);
    assertThat(frontierSize.sum())
        .isEqualTo(PendingTransaction.FRONTIER_AND_ACCESS_LIST_BASE_MEMORY_SIZE);
  }

  private GraphWalker getGraphWalker(
      final Set<String> constantFieldPaths, final SortedSet<FieldSize> fieldSizes) {
    final Set<String> skipPrefixes = new HashSet<>();
    GraphVisitor gv =
        gpr -> {
          if (!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
            if (SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))) {
              skipPrefixes.add(gpr.path());
            } else if (!startWithAnyOf(constantFieldPaths, gpr)
                && !startWithAnyOf(VARIABLE_SIZE_PATHS, gpr)) {

              fieldSizes.add(new FieldSize(gpr.path(), gpr.klass(), gpr.size()));
            }
          }
        };

    GraphWalker gw = new GraphWalker(gv);
    return gw;
  }

  private boolean startWithAnyOf(final Set<String> prefixes, final GraphPathRecord path) {
    return prefixes.stream().anyMatch(prefix -> path.path().startsWith(prefix));
  }

  private long sizeOfField(final Object container, final String... excludePaths) {
    final ClassLayout cl = ClassLayout.parseInstance(container);
    System.out.println(cl.toPrintable());
    System.out.println("Base container size: " + cl.instanceSize());

    LongAdder size = new LongAdder();
    size.add(cl.instanceSize());

    GraphVisitor gv =
        gpr -> {
          if (Arrays.stream(excludePaths)
              .anyMatch(excludePath -> gpr.path().contains(excludePath))) {
            System.out.println("Excluded path " + gpr.path());
          } else {
            size.add(gpr.size());
            System.out.println(
                "("
                    + size
                    + ")["
                    + gpr.size()
                    + ", "
                    + gpr.path()
                    + ", "
                    + gpr.klass().toString()
                    + "]");
          }
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(container);

    System.out.println("Container size: " + size);
    return size.sum();
  }

  record FieldSize(String path, Class<?> clazz, long size) implements Comparable<FieldSize> {

    @Override
    public int compareTo(final FieldSize o) {
      return path.compareTo(o.path);
    }
  }
}
