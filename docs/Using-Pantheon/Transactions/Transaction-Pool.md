# Transaction Pool 

Options and methods for configuring and viewing the transaction pool include: 

* [`txpool_pantheonTransactions`](../../Reference/JSON-RPC-API-Methods.md#txpool_pantheonTransactions) JSON-RPC API method

* [`--tx-pool-max-size`](../../Reference/Pantheon-CLI-Syntax.md#tx-pool-max-size) command line option

Once full, the Pantheon transaction pool accepts and retains local transactions in preference to remote transactions. 

Decreasing the maximum size of the transaction pool reduces memory use. If the network is busy and there is a backlog
of transactions, increasing the size of the transaction pool reduces the risk of transactions being 
removed from the transaction pool.