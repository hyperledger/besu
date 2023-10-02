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
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.layered.BaseTransactionPoolTest;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

@Disabled("Need to handle different results on different OS")
public class PendingTransactionEstimatedMemorySizeTest extends BaseTransactionPoolTest {
  private static final Set<Class<?>> SHARED_CLASSES =
      Set.of(SignatureAlgorithm.class, TransactionType.class);
  private static final Set<String> EIP1559_CONSTANT_FIELD_PATHS = Set.of(".gasPrice");
  private static final Set<String> EIP1559_VARIABLE_SIZE_PATHS =
      Set.of(".to", ".payload", ".maybeAccessList");

  private static final Set<String> FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS =
      Set.of(".maxFeePerGas", ".maxPriorityFeePerGas");
  private static final Set<String> FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS =
      Set.of(".to", ".payload", ".maybeAccessList");

  @Test
  public void toSize() {
    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10);
    Transaction txTo =
        preparedTx.to(Optional.of(Address.extract(Bytes32.random()))).createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txTo.writeTo(rlpOut);

    txTo = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
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
          // byte[] is shared so only count the specific part for each field
          if (gpr.path().endsWith(".bytes")) {
            if (gpr.path().contains("delegate")) {
              size.add(20);
              System.out.println(
                  "("
                      + size
                      + ")[20 = fixed address size; overrides: "
                      + gpr.size()
                      + ", "
                      + gpr.path()
                      + ", "
                      + gpr.klass().toString()
                      + "]");
            }
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

    gw.walk(to);

    System.out.println("Optional To size: " + size);

    assertThat(size.sum()).isEqualTo(PendingTransaction.OPTIONAL_TO_MEMORY_SIZE);
  }

  @Test
  public void payloadSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10);
    Transaction txPayload = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txPayload.writeTo(rlpOut);

    txPayload = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
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
  public void pendingTransactionSize() {

    TransactionTestFixture preparedTx =
        prepareTransaction(TransactionType.ACCESS_LIST, 10, Wei.of(500), 10);
    Transaction txPayload = preparedTx.createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txPayload.writeTo(rlpOut);

    txPayload = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
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
        prepareTransaction(TransactionType.ACCESS_LIST, 0, Wei.of(500), 0);
    Transaction txAccessList = preparedTx.accessList(ales).createTransaction(KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txAccessList.writeTo(rlpOut);

    txAccessList = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
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

    final ClassLayout cl3 = ClassLayout.parseInstance(ale);
    System.out.println(cl3.toPrintable());
    System.out.println("AccessListEntry size: " + cl3.instanceSize());

    LongAdder size = new LongAdder();
    size.add(cl3.instanceSize());

    GraphVisitor gv =
        gpr -> {
          // byte[] is shared so only count the specific part for each field
          if (gpr.path().endsWith(".bytes")) {
            if (gpr.path().contains("address")) {
              size.add(20);
              System.out.println(
                  "("
                      + size
                      + ")[20 = fixed address size; overrides: "
                      + gpr.size()
                      + ", "
                      + gpr.path()
                      + ", "
                      + gpr.klass().toString()
                      + "]");
            }
          } else if (!gpr.path()
              .contains(
                  "storageKeys.elementData[")) { // exclude elements since we want the container
            // size
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

    gw.walk(ale);

    System.out.println("AccessListEntry container size: " + size);

    assertThat(size.sum()).isEqualTo(PendingTransaction.ACCESS_LIST_ENTRY_BASE_MEMORY_SIZE);

    final Bytes32 storageKey = ale.storageKeys().get(0);
    final ClassLayout cl4 = ClassLayout.parseInstance(storageKey);
    System.out.println(cl4.toPrintable());
    System.out.println("Single storage key size: " + cl4.instanceSize());

    assertThat(cl4.instanceSize())
        .isEqualTo(PendingTransaction.ACCESS_LIST_STORAGE_KEY_MEMORY_SIZE);
  }

  @Test
  public void baseEIP1559TransactionMemorySize() {
    System.setProperty("jol.magicFieldOffset", "true");
    Transaction txEip1559 = createEIP1559Transaction(1, KEYS1, 10);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txEip1559.writeTo(rlpOut);

    txEip1559 = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
    System.out.println(txEip1559.getSender());
    System.out.println(txEip1559.getHash());
    System.out.println(txEip1559.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(txEip1559);
    System.out.println(cl.toPrintable());
    LongAdder eip1559size = new LongAdder();
    eip1559size.add(cl.instanceSize());
    System.out.println(eip1559size);

    final Set<String> skipPrefixes = new HashSet<>();

    GraphVisitor gv =
        gpr -> {
          if (!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
            if (SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))) {
              skipPrefixes.add(gpr.path());
            } else if (!startWithAnyOf(EIP1559_CONSTANT_FIELD_PATHS, gpr)
                && !startWithAnyOf(EIP1559_VARIABLE_SIZE_PATHS, gpr)) {
              eip1559size.add(gpr.size());
              System.out.println(
                  "("
                      + eip1559size
                      + ")["
                      + gpr.size()
                      + ", "
                      + gpr.path()
                      + ", "
                      + gpr.klass().toString()
                      + "]");
            }
          }
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(txEip1559);

    System.out.println("Base EIP1559 size: " + eip1559size);
    assertThat(eip1559size.sum()).isEqualTo(PendingTransaction.EIP1559_BASE_MEMORY_SIZE);
  }

  @Test
  public void baseAccessListTransactionMemorySize() {
    System.setProperty("jol.magicFieldOffset", "true");
    Transaction txAccessList =
        createTransaction(TransactionType.ACCESS_LIST, 1, Wei.of(500), 0, KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txAccessList.writeTo(rlpOut);

    txAccessList = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
    System.out.println(txAccessList.getSender());
    System.out.println(txAccessList.getHash());
    System.out.println(txAccessList.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(txAccessList);
    System.out.println(cl.toPrintable());
    LongAdder accessListSize = new LongAdder();
    accessListSize.add(cl.instanceSize());
    System.out.println(accessListSize);

    final Set<String> skipPrefixes = new HashSet<>();

    GraphVisitor gv =
        gpr -> {
          if (!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
            if (SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))) {
              skipPrefixes.add(gpr.path());
            } else if (!startWithAnyOf(FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS, gpr)
                && !startWithAnyOf(FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS, gpr)) {
              accessListSize.add(gpr.size());
              System.out.println(
                  "("
                      + accessListSize
                      + ")["
                      + gpr.size()
                      + ", "
                      + gpr.path()
                      + ", "
                      + gpr.klass().toString()
                      + "]");
            }
          }
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(txAccessList);
    System.out.println("Base Access List size: " + accessListSize);
    assertThat(accessListSize.sum()).isEqualTo(PendingTransaction.ACCESS_LIST_BASE_MEMORY_SIZE);
  }

  @Test
  public void baseFrontierTransactionMemorySize() {
    System.setProperty("jol.magicFieldOffset", "true");
    Transaction txFrontier = createTransaction(TransactionType.FRONTIER, 1, Wei.of(500), 0, KEYS1);
    BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    txFrontier.writeTo(rlpOut);

    txFrontier = Transaction.readFrom(new BytesValueRLPInput(rlpOut.encoded(), false));
    System.out.println(txFrontier.getSender());
    System.out.println(txFrontier.getHash());
    System.out.println(txFrontier.getSize());

    final ClassLayout cl = ClassLayout.parseInstance(txFrontier);
    System.out.println(cl.toPrintable());
    LongAdder frontierSize = new LongAdder();
    frontierSize.add(cl.instanceSize());
    System.out.println(frontierSize);

    final Set<String> skipPrefixes = new HashSet<>();

    GraphVisitor gv =
        gpr -> {
          if (!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
            if (SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))) {
              skipPrefixes.add(gpr.path());
            } else if (!startWithAnyOf(FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS, gpr)
                && !startWithAnyOf(FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS, gpr)) {
              frontierSize.add(gpr.size());
              System.out.println(
                  "("
                      + frontierSize
                      + ")["
                      + gpr.size()
                      + ", "
                      + gpr.path()
                      + ", "
                      + gpr.klass().toString()
                      + "]");
            }
          }
        };

    GraphWalker gw = new GraphWalker(gv);

    gw.walk(txFrontier);
    System.out.println("Base Frontier size: " + frontierSize);
    assertThat(frontierSize.sum()).isEqualTo(PendingTransaction.FRONTIER_BASE_MEMORY_SIZE);
  }

  private boolean startWithAnyOf(final Set<String> prefixes, final GraphPathRecord path) {
    return prefixes.stream().anyMatch(prefix -> path.path().startsWith(prefix));
  }
}
