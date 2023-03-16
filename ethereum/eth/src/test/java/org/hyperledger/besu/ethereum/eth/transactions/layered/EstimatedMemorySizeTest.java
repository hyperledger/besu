package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public class EstimatedMemorySizeTest extends BaseTransactionPoolTest {
    private static final Set<Class<?>> SHARED_CLASSES = Set.of(SignatureAlgorithm.class, TransactionType.class);
    private static final Set<String> EIP1559_CONSTANT_FIELD_PATHS = Set.of(".gasPrice");
    private static final Set<String> EIP1559_VARIABLE_SIZE_PATHS = Set.of(
            ".to",
            ".payload",
            ".maybeAccessList"
    );

    private static final Set<String> FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS = Set.of(".maxFeePerGas", ".maxPriorityFeePerGas");
    private static final Set<String> FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS = Set.of(
            ".to",
            ".payload",
            ".maybeAccessList"
    );

    @Test
    public void toSize() {
        final Optional<Address> sampleTo = Optional.of(Address.ALTBN128_ADD);
        final ClassLayout cl = ClassLayout.parseInstance(sampleTo);
        System.out.println(cl.toPrintable());
        LongAdder size = new LongAdder();
        size.add(cl.instanceSize());
        System.out.println(size);

        GraphVisitor gv = gpr -> {
            size.add(gpr.size());
            System.out.println("(" + size + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
        };


        GraphWalker gw = new GraphWalker(gv);

        gw.walk(sampleTo);
    }


    @Test
    public void payloadSize() {
        for(int i = 0; i < 100; i+=5) {
            final Bytes payload = Bytes.random(i);
            final ClassLayout cl = ClassLayout.parseInstance(payload);
            System.out.println(i + " ######");
            System.out.println(cl.toPrintable());
            LongAdder size = new LongAdder();
            size.add(cl.instanceSize());
            System.out.println(size);

            GraphVisitor gv = gpr -> {
                size.add(gpr.size());
                System.out.println("(" + size + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
            };


            GraphWalker gw = new GraphWalker(gv);

            gw.walk(payload);

            System.out.println();
        }
    }

    @Test
    public void accessListSize() {
        final List<AccessListEntry> ales = new ArrayList<>();

        for(int i = 0; i < 10; i++) {
            final List<Bytes32> storages = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                storages.add(Bytes32.random());
            }

            final AccessListEntry ale = new AccessListEntry(Address.extract(Bytes32.random()), storages);

            ales.add(ale);
        }

        final Optional<List<AccessListEntry>> accessList = Optional.of(ales);

        final ClassLayout cl = ClassLayout.parseInstance(accessList);
        System.out.println("[" + ales.size() + "] ######");
        System.out.println(cl.toPrintable());
        LongAdder size = new LongAdder();
        size.add(cl.instanceSize());
        System.out.println(size);

        GraphVisitor gv = gpr -> {
            size.add(gpr.size());
            System.out.println("(" + size + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
        };


        GraphWalker gw = new GraphWalker(gv);

        gw.walk(accessList);

        System.out.println();

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

        GraphVisitor gv = gpr -> {
            if(!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
                if(SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))){
                    skipPrefixes.add(gpr.path());
                } else if(!startWithAnyOf(EIP1559_CONSTANT_FIELD_PATHS, gpr) && ! startWithAnyOf(EIP1559_VARIABLE_SIZE_PATHS,gpr)) {
                    eip1559size.add(gpr.size());
                    System.out.println("(" + eip1559size + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
                }
            }
        };

        GraphWalker gw = new GraphWalker(gv);

        gw.walk(txEip1559);

    }

    @Test
    public void baseAccessListTransactionMemorySize() {
        System.setProperty("jol.magicFieldOffset", "true");
        Transaction txAccessList = createTransaction(TransactionType.ACCESS_LIST, 1, Wei.of(500), 0, KEYS1);
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

        GraphVisitor gv = gpr -> {
            if(!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
                if(SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))){
                    skipPrefixes.add(gpr.path());
                } else if(!startWithAnyOf(FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS, gpr) && ! startWithAnyOf(FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS,gpr)) {
                    accessListSize.add(gpr.size());
                    System.out.println("(" + accessListSize + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
                }
            }
        };

        GraphWalker gw = new GraphWalker(gv);

        gw.walk(txAccessList);

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

        GraphVisitor gv = gpr -> {
            if(!skipPrefixes.stream().anyMatch(sp -> gpr.path().startsWith(sp))) {
                if(SHARED_CLASSES.stream().anyMatch(scz -> scz.isAssignableFrom(gpr.klass()))){
                    skipPrefixes.add(gpr.path());
                } else if(!startWithAnyOf(FRONTIER_ACCESS_LIST_CONSTANT_FIELD_PATHS, gpr) && ! startWithAnyOf(FRONTIER_ACCESS_LIST_VARIABLE_SIZE_PATHS,gpr)) {
                    frontierSize.add(gpr.size());
                    System.out.println("(" + frontierSize + ")[" + gpr.size() + ", " + gpr.path() + ", " + gpr.klass().toString() + "]");
                }
            }
        };

        GraphWalker gw = new GraphWalker(gv);

        gw.walk(txFrontier);

    }

    private boolean startWithAnyOf(final Set<String> prefixes, final GraphPathRecord path) {
        return prefixes.stream().anyMatch(prefix -> path.path().startsWith(prefix));
    }
}
