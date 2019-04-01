# Transaction Pool 

Options and methods for configuring and monitoring the transaction pool include: 

* [`txpool_pantheonTransactions`](../../Reference/JSON-RPC-API-Methods.md#txpool_pantheontransactions) JSON-RPC API method to list
transactions in the node transaction pool

* [`--tx-pool-max-size`](../../Reference/Pantheon-CLI-Syntax.md#tx-pool-max-size) command line option to specify the maximum number
of transactions in the node transaction pool

* [`newPendingTransactions`](../RPC-PubSub.md#pending-transactions) and [`droppedPendingTransactions`](../RPC-PubSub.md#dropped-transactions)
RPC subscriptions to notify of transactions added to and dropped from the node transaction pool  

Once full, the Pantheon transaction pool accepts and retains local transactions in preference to remote transactions. 
If the transaction pool is full of local transactions, the oldest local transactions are dropped first.  That is, a 
full transaction pool continues to accept new local transactions by first dropping remote transactions and then by 
dropping the oldest local transactions. 

Decreasing the maximum size of the transaction pool reduces memory use. If the network is busy and there is a backlog
of transactions, increasing the size of the transaction pool reduces the risk of transactions being 
removed from the transaction pool.