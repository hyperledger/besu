description: What transaction validation is performed when 
<!--- END of page meta data -->

# Validating Transactions 

When transactions are submitted and added to a block, validations are performed as illustrated. 

![Transaction Validation](../../images/transaction-validation.png)

The set of transaction pool validations are repeated when the transaction is propagated. The same set of 
validations are repeated when the block including the transaction is imported except the nonce must be 
exactly right when importing the block. 

When the transaction is added to a block an additional validation is performed to check the transaction gas limit
is less than the remaining gas limit for the block. After creating a block, the node imports the block and
the transaction pool validations are repeated. 

!!! important 
    The transaction is only added if the entire transaction gas limit is less than the remaining gas 
    for the block. The total gas used by the transaction is not relevant to this validation. That is, if the total gas used
    by the transaction is less than the remaining block gas but the transaction gas limit is more than the remaining black 
    gas, the transaction is not added. 