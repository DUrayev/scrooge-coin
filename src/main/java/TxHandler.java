import java.util.*;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if(tx == null) {
            return false;
        }
        double sumOfInputs = 0;
        double sumOfOutputs = 0;

        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input currentInput = tx.getInput(i);
            UTXO currentUtxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);

            if (!this.utxoPool.contains(currentUtxo)) return false; //{1} requirement
            Transaction.Output prevTxOutput = this.utxoPool.getTxOutput(currentUtxo);
            if (currentInput.signature == null || prevTxOutput.address == null || !Crypto.verifySignature(prevTxOutput.address, tx.getRawDataToSign(i), currentInput.signature)) { //{2} requirement
                return false;
            }

            //compare with others elements "(3) no UTXO is claimed multiple times by {@code tx}"
            for (int k = i + 1; k < tx.getInputs().size(); k++) {
                Transaction.Input inputToCompare = tx.getInput(k);

                UTXO u1 = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
                UTXO u2 = new UTXO(inputToCompare.prevTxHash, inputToCompare.outputIndex);

                if (u1.equals(u2)) { // {3} requirement
                    return false;
                }
            }

            sumOfInputs += prevTxOutput.value;
        }

        for (int i = 0; i < tx.getOutputs().size(); i++) {
            Transaction.Output currentOutput = tx.getOutput(i);

            if(currentOutput.value >= 0) { // {4} requirement
                sumOfOutputs += currentOutput.value;
            } else {
                return false;
            }
        }

        if(sumOfOutputs > sumOfInputs) { //{5} requirement
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) {
            return new Transaction[0];
        }

        List<Transaction> acceptedTransactions = new ArrayList<>();
        for(int i = 0; i < possibleTxs.length; i++) {
            Transaction currentTx = possibleTxs[i];

            //------ Find some transactions which depend on other transactions in possibleTxs array
            for (int k = i + 1; k < possibleTxs.length; k++) {
                Transaction tx = possibleTxs[k];
                UTXO u1 = new UTXO(currentTx.getInput(0).prevTxHash, currentTx.getInput(0).outputIndex);
                UTXO u2 = new UTXO(tx.getHash(), currentTx.getInput(0).outputIndex);

                if(u1.equals(u2)) {
                    Transaction[] theRestTransactions = Arrays.copyOfRange(possibleTxs, i + 1, possibleTxs.length);
                    acceptedTransactions.addAll(Arrays.asList(handleTxs(theRestTransactions))); //recursively handle transaction from which currentTx depends on
                }
            }
            //---------------------------------------------------------------------------------------

            if(this.isValidTx(currentTx)) {
                for(Transaction.Input txInput : currentTx.getInputs()) {
                    UTXO utxo = new UTXO(txInput.prevTxHash, txInput.outputIndex);
                    this.utxoPool.removeUTXO(utxo);
                }

                for(int outputIndex = 0; outputIndex < currentTx.getOutputs().size(); outputIndex++) {
                    Transaction.Output txOutput = currentTx.getOutput(outputIndex);
                    UTXO utxo = new UTXO(currentTx.getHash(), outputIndex);
                    this.utxoPool.addUTXO(utxo, txOutput);
                }

                acceptedTransactions.add(currentTx);
            }
        }

        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }

}
