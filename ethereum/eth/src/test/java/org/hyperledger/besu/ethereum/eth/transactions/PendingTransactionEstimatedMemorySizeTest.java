/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.ACCESS_LIST_ENTRY_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.ACCESS_LIST_STORAGE_KEY_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.BLOBS_WITH_COMMITMENTS_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.BLOB_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.CODE_DELEGATION_ENTRY_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.EIP1559_AND_EIP4844_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.KZG_COMMITMENT_OR_PROOF_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_ACCESS_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_CHAIN_ID_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_CODE_DELEGATION_LIST_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.OPTIONAL_TO_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.PAYLOAD_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.PENDING_TRANSACTION_SHALLOW_SIZE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.MemorySize.VERSIONED_HASH_SIZE;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.CodeDelegation;
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

import java.io.IOException;
import java.lang.management.ManagementFactory;
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
import javax.management.MBeanServer;

import com.google.common.collect.Sets;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

/**
 * This test class has a double utility, first it is used to verify that the current memory size of
 * a pending transaction object correspond to the calculated values, so if any of the tests in this
 * file fails, it probably means that one of the related classes has changed its format, and a new
 * calculation needs to be done.
 *
 * <p>The second utility is to help with the calculation of the memory size of a class, using the <a
 * href="https://github.com/openjdk/jol">JOL Tool</a>, to navigate the class layout and collect the
 * reported memory sizes.
 *
 * <p>For a correct calculation there are some things to consider, first exclude from the
 * calculation any reference to a <i>constant</i> object, for example if a field is always a
 * reference to {@code Optional.empty()} then just count the reference size, but not the size of the
 * Optional since it is always the same instance for every pending transaction.
 *
 * <p>To study the layout of a class it is usually useful to create a test method with one or more
 * instance of it, then programmatically save a heap dump using the {@link #dumpHeap(String,
 * boolean)} method, then analyze the heap dump with a tool to identify which are the dynamic and
 * constant fields, then use the JOL Tool to print the class layout and walk in the object tree,
 * then complete the writing of the test that will verify the current amount of memory used by that
 * class.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
public class PendingTransactionEstimatedMemorySizeTest extends BaseTransactionPoolTest {
  /**
   * Classes that represent constant instances, across all pending transaction types, and are
   * ignored during the calculation
   */
  private static final Set<Class<?>> SHARED_CLASSES =
      Set.of(SignatureAlgorithm.class, TransactionType.class);

  /**
   * Field that points to constant values, across all pending transaction types, and are ignored
   * during the calculation
   */
  private static final Set<String> COMMON_CONSTANT_FIELD_PATHS =
      Set.of(".value.ctor", ".hashNoSignature", ".signature.encoded.delegate");

  /**
   * Field that points to constant values, for EIP-1559 and EIP-4844 pending transactions, and are
   * ignored during the calculation
   */
  private static final Set<String> EIP1559_EIP4844_CONSTANT_FIELD_PATHS =
      Sets.union(COMMON_CONSTANT_FIELD_PATHS, Set.of(".gasPrice"));

  /**
   * Field that points to constant values, for Frontier and Access List pending transactions, and
   * are ignored during the calculation
   */
  private static final Set<String> FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS =
      Sets.union(COMMON_CONSTANT_FIELD_PATHS, Set.of(".maxFeePerGas", ".maxPriorityFeePerGas"));

  /** Field which value is dynamic and can only be calculated at runtime */
  private static final Set<String> VARIABLE_SIZE_PATHS =
      Set.of(
          ".chainId",
          ".to",
          ".payload",
          ".maybeAccessList",
          ".versionedHashes",
          ".blobsWithCommitments",
          ".maybeCodeDelegationList");

  @Test
  public void toSize() {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), Wei.ZERO, 10, 0, null);
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
          System.out.println(ClassLayout.parseClass(gpr.klass()).toPrintable());
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(to);

    System.out.println("Optional To size: " + size);

    assertThat(size.sum()).isEqualTo(OPTIONAL_TO_SIZE);
  }

  @Test
  public void payloadSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), Wei.ZERO, 10, 0, null);
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

    assertThat(size.sum()).isEqualTo(PAYLOAD_SHALLOW_SIZE);
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

    assertThat(size.sum()).isEqualTo(OPTIONAL_CHAIN_ID_SIZE);
  }

  @Test
  public void kgzCommitmentsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getKzgCommitments(),
        LIST_SHALLOW_SIZE,
        KZG_COMMITMENT_OR_PROOF_SIZE);
  }

  @Test
  public void kgzProofsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getKzgProofs(),
        LIST_SHALLOW_SIZE,
        KZG_COMMITMENT_OR_PROOF_SIZE);
  }

  @Test
  public void blobsSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getBlobs(), LIST_SHALLOW_SIZE, BLOB_SIZE);
  }

  @Test
  public void versionedHashesSize() {
    blobsWithCommitmentsFieldSize(
        t -> t.getBlobsWithCommitments().get().getVersionedHashes(),
        LIST_SHALLOW_SIZE,
        VERSIONED_HASH_SIZE);
  }

  private void blobsWithCommitmentsFieldSize(
      final Function<Transaction, List<? extends Object>> containerExtractor,
      final long containerSize,
      final long itemSize) {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.BLOB, 10, Wei.of(500), Wei.of(50), 10, 1, null);
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
        prepareTransaction(TransactionType.BLOB, 10, Wei.of(500), Wei.of(50), 10, 1, null);
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

    assertThat(cl.instanceSize() + rl.instanceSize()).isEqualTo(BLOBS_WITH_COMMITMENTS_SIZE);
  }

  @Test
  public void pendingTransactionSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), Wei.ZERO, 10, 0, null);
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

    assertThat(size.sum()).isEqualTo(PENDING_TRANSACTION_SHALLOW_SIZE);
  }

  @Test
  public void accessListSize() {
    System.setProperty("jol.magicFieldOffset", "true");

    final AccessListEntry ale1 =
        new AccessListEntry(Address.extract(Bytes32.random()), List.of(Bytes32.random()));

    final List<AccessListEntry> ales = List.of(ale1);

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 0, Wei.of(500), Wei.ZERO, 0, 0, null);
    Transaction txAccessList = preparedTx.accessList(ales).createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txAccessList.writeTo(rlpOut);

    txAccessList =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txAccessList.getSender());
    System.out.println(txAccessList.getHash());
    System.out.println(txAccessList.getSize());

    final var optAL = txAccessList.getAccessList();

    final ClassLayout optionalClassLayout = ClassLayout.parseInstance(optAL);
    System.out.println(optionalClassLayout.toPrintable());
    System.out.println("Optional size: " + optionalClassLayout.instanceSize());

    final ClassLayout listClassLayout = ClassLayout.parseInstance(optAL.get());
    System.out.println(listClassLayout.toPrintable());
    System.out.println(
        "Optional + list size: "
            + (optionalClassLayout.instanceSize() + listClassLayout.instanceSize()));

    assertThat(optionalClassLayout.instanceSize() + listClassLayout.instanceSize())
        .isEqualTo(OPTIONAL_ACCESS_LIST_SHALLOW_SIZE);

    final AccessListEntry ale = optAL.get().get(0);

    long aleSize = sizeOfField(ale, "storageKeys.elementData[");

    System.out.println("AccessListEntry container size: " + aleSize);

    assertThat(aleSize).isEqualTo(ACCESS_LIST_ENTRY_SHALLOW_SIZE);

    final Bytes32 storageKey = ale.storageKeys().get(0);
    final ClassLayout cl4 = ClassLayout.parseInstance(storageKey);
    System.out.println(cl4.toPrintable());
    System.out.println("Single storage key size: " + cl4.instanceSize());

    assertThat(cl4.instanceSize()).isEqualTo(ACCESS_LIST_STORAGE_KEY_SIZE);
  }

  @Test
  public void codeDelegationListSize() {
    System.setProperty("jol.magicFieldOffset", "true");

    TransactionTestFixture preparedTx =
        prepareTransaction(
            TransactionType.DELEGATE_CODE,
            0,
            Wei.of(500),
            Wei.ZERO,
            0,
            0,
            List.of(CODE_DELEGATION_SENDER_1));
    Transaction txDelegateCode = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txDelegateCode.writeTo(rlpOut);

    txDelegateCode =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(txDelegateCode.getSender());
    System.out.println(txDelegateCode.getHash());
    System.out.println(txDelegateCode.getSize());

    final var optCD = txDelegateCode.getCodeDelegationList();

    final ClassLayout optionalClassLayout = ClassLayout.parseInstance(optCD);
    System.out.println(optionalClassLayout.toPrintable());
    System.out.println("Optional size: " + optionalClassLayout.instanceSize());

    final ClassLayout listClassLayout = ClassLayout.parseInstance(optCD.get());
    System.out.println(listClassLayout.toPrintable());
    System.out.println(
        "Optional + list size: "
            + (optionalClassLayout.instanceSize() + listClassLayout.instanceSize()));

    assertThat(optionalClassLayout.instanceSize() + listClassLayout.instanceSize())
        .isEqualTo(OPTIONAL_CODE_DELEGATION_LIST_SHALLOW_SIZE);

    final CodeDelegation codeDelegation = optCD.get().get(0);

    long cdSize = sizeOfField(codeDelegation, "storageKeys.elementData[");

    System.out.println("CodeDelegation container size: " + cdSize);

    assertThat(cdSize).isEqualTo(CODE_DELEGATION_ENTRY_SIZE);
  }

  @Test
  public void baseEIP1559AndEIP4844TransactionMemorySize() {
    Transaction txEip1559 = createEIP1559Transaction(1, KEYS1, 10);
    assertThat(baseTransactionMemorySize(txEip1559, EIP1559_EIP4844_CONSTANT_FIELD_PATHS))
        .isEqualTo(EIP1559_AND_EIP4844_SHALLOW_SIZE);
  }

  @Test
  public void baseFrontierAndAccessListTransactionMemorySize() {
    final Transaction txFrontier =
        createTransaction(TransactionType.FRONTIER, 1, Wei.of(500), 0, List.of(), KEYS1);
    assertThat(baseTransactionMemorySize(txFrontier, FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS))
        .isEqualTo(FRONTIER_AND_ACCESS_LIST_SHALLOW_SIZE);
  }

  private long baseTransactionMemorySize(final Transaction tx, final Set<String> constantFields) {
    System.setProperty("jol.magicFieldOffset", "true");
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    tx.writeTo(rlpOut);

    final var baseTx =
        Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false)).detachedCopy();
    System.out.println(baseTx.getSender());
    System.out.println(baseTx.getHash());
    System.out.println(baseTx.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(baseTx);
    System.out.println(cl.toPrintable());
    LongAdder baseTxSize = new LongAdder();
    baseTxSize.add(cl.instanceSize());
    System.out.println(baseTxSize);

    final SortedSet<FieldSize> fieldSizes = new TreeSet<>();

    GraphWalker gw = getGraphWalker(constantFields, fieldSizes);

    gw.walk(baseTx);

    fieldSizes.forEach(
        fieldSize -> {
          baseTxSize.add(fieldSize.size());
          System.out.println(
              "("
                  + baseTxSize
                  + ")["
                  + fieldSize.size()
                  + ", "
                  + fieldSize.path()
                  + ", "
                  + fieldSize
                  + "]");
        });
    System.out.println("Base tx size: " + baseTxSize);
    return baseTxSize.sum();
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
            System.out.println(ClassLayout.parseClass(gpr.klass()).toPrintable());
          }
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(container);

    System.out.println("Container size: " + size);
    return size.sum();
  }

  /**
   * Utility method useful for producing a heap dump when calculating the memory size of a new
   * object. Note that the file is not overwritten, so you need to remove it to create a new heap
   * dump.
   *
   * @param filePath where to save the heap dump
   * @param live true to only include live objects
   */
  @SuppressWarnings("unused")
  private static void dumpHeap(final String filePath, final boolean live) {
    try {
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      HotSpotDiagnosticMXBean mxBean =
          ManagementFactory.newPlatformMXBeanProxy(
              server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
      mxBean.dumpHeap(filePath, live);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  record FieldSize(String path, Class<?> clazz, long size) implements Comparable<FieldSize> {

    @Override
    public int compareTo(final FieldSize o) {
      return path.compareTo(o.path);
    }
  }
}
