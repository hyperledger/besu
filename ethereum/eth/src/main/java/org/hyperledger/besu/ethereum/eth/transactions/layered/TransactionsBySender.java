package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

class TransactionsBySender {
    private final Map<Address, NavigableMap<Long, PendingTransaction>> txsBySender =
            new HashMap<>();
}
