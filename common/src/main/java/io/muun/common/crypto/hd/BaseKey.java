package io.muun.common.crypto.hd;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;

import java.util.Iterator;
import java.util.List;

public abstract class BaseKey {

    /**
     * Derives a deterministic key from {deterministicKey} using {childNumbersToDerive}.
     */
    protected DeterministicKey deriveDeterministicKey(DeterministicKey deterministicKey,
                                                      List<ChildNumber> childNumbersToDerive) {

        DeterministicKey derivedKey = deterministicKey;

        for (final ChildNumber childNumber : childNumbersToDerive) {

            final org.bitcoinj.crypto.ChildNumber bitcoinjChildNumber =
                    new org.bitcoinj.crypto.ChildNumber(
                            childNumber.getIndex(),
                            childNumber.isHardened()
                    );

            derivedKey = HDKeyDerivation.deriveChildKey(derivedKey, bitcoinjChildNumber);
        }

        return derivedKey;
    }

    /**
     * Returns true if any of the child numbers is hardened.
     */
    protected boolean anyHardened(List<ChildNumber> childNumbers) {

        for (final ChildNumber childNumber : childNumbers) {
            if (childNumber.isHardened()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the first path is a prefix of the second.
     */
    protected boolean isPrefix(List<ChildNumber> prefixPath, List<ChildNumber> completePath) {

        if (completePath.size() < prefixPath.size()) {
            return false;
        }

        final Iterator<ChildNumber> toDeriveIterator = completePath.iterator();
        final Iterator<ChildNumber> currentKeyIterator = prefixPath.iterator();

        while (currentKeyIterator.hasNext() && toDeriveIterator.hasNext()) {

            final ChildNumber currentKeyChildNumber = currentKeyIterator.next();
            final ChildNumber toDeriveChildNumber = toDeriveIterator.next();

            if (toDeriveChildNumber.getIndex() != currentKeyChildNumber.getIndex()
                    || toDeriveChildNumber.isHardened() != currentKeyChildNumber.isHardened()) {
                return false;
            }

        }

        return true;
    }

}
