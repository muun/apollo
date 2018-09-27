package io.muun.common.crypto.hd.exception;

public class InvalidDerivationBranchException extends RuntimeException {

    /**
     * Creates an InvalidDerivationBranchException.
     */
    public InvalidDerivationBranchException(String currentAbsolutePath,
                                            String absolutePathToDerive) {
        super("Invalid derivation branch. Trying to derive " + absolutePathToDerive + " from "
                + currentAbsolutePath);

    }
}
