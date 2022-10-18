package org.apache.commons.statistics.distribution;

/**
 * Efficient Zipf distribution implementation using approximation methods. The generalized harmonic numbers are
 * approximated using the Euler-Maclaurin formula (of order 2). The approximation for the inverse CDF has been taken
 * from well-known article "Quickly generating billion-record synthetic databases" from Gray et al.
 * (see <a href="https://doi.org/10.1145/191839.191886">paper</a>).
 */
public class ApproximatedZipfDistribution extends AbstractDiscreteDistribution {
    private final int numberOfElements;
    private final double exponent;
    private final double nthHarmonic;
    private final double alpha;
    private final double eta;

    public ApproximatedZipfDistribution(int numberOfElements, double exponent) {
        if (numberOfElements <= 0) {
            throw new DistributionException(DistributionException.NEGATIVE, numberOfElements);
        }

        if (exponent <= 0) {
            throw new DistributionException(DistributionException.NEGATIVE, exponent);
        }

        this.numberOfElements = numberOfElements;
        this.exponent = exponent;
        this.nthHarmonic = approximatedGeneralizedHarmonic(numberOfElements, exponent);
        this.alpha = 1.0 / (1.0 - exponent);
        this.eta = (1.0 - Math.pow(2.0 / numberOfElements, 1.0 - exponent)) /
            (1.0 - (1.0 + Math.pow(0.5, exponent)) / nthHarmonic);
    }

    public int getNumberOfElements() {
        return numberOfElements;
    }

    public double getExponent() {
        return exponent;
    }

    /**
     * Approximation of generalized harmonic number using Euler-Maclaurin formula
     * (see <a href="https://en.wikipedia.org/wiki/Euler-Maclaurin_formula">details</a>).
     */
    private static double approximatedGeneralizedHarmonic(int numberOfElements, double exponent) {
        return (Math.pow(numberOfElements, 1.0 - exponent) - 1.0) / (1.0 - exponent) +
            0.5 * (1.0 + Math.pow(numberOfElements, -exponent)) +
            0.083333 * exponent * (1.0 - Math.pow(numberOfElements, -1.0 - exponent));
    }

    @Override
    public double probability(final int x) {
        if (x <= 0 || x > numberOfElements) {
            return 0;
        }

        return (1 / Math.pow(x, exponent)) / nthHarmonic;
    }

    @Override
    public double logProbability(int x) {
        if (x <= 0 || x > numberOfElements) {
            return Double.NEGATIVE_INFINITY;
        }

        return -Math.log(x) * exponent - Math.log(nthHarmonic);
    }

    @Override
    public double cumulativeProbability(int x) {
        if (x <= 0) {
            return 0;
        }

        if (x >= numberOfElements) {
            return 1;
        }

        return approximatedGeneralizedHarmonic(x, exponent) / nthHarmonic;
    }

    @Override
    public int inverseCumulativeProbability(double p) {
        double z = p * nthHarmonic;

        if (z < 1.0) {
            return 1;
        }

        if (z < 1.0 + Math.pow(0.5, exponent)) {
            return 2;
        }

        return 1 + (int) (numberOfElements * Math.pow(eta * p - eta + 1.0, alpha));
    }

    @Override
    public double getMean() {
        return approximatedGeneralizedHarmonic(numberOfElements, exponent - 1.0) / nthHarmonic;
    }

    @Override
    public double getVariance() {
        final double Hs2 = approximatedGeneralizedHarmonic(numberOfElements, exponent - 2.0);
        final double Hs1 = approximatedGeneralizedHarmonic(numberOfElements, exponent - 1.0);

        return (Hs2 / nthHarmonic) - ((Hs1 * Hs1) / (nthHarmonic * nthHarmonic));
    }

    @Override
    public int getSupportLowerBound() {
        return 1;
    }

    @Override
    public int getSupportUpperBound() {
        return getNumberOfElements();
    }

    @Override
    public boolean isSupportConnected() {
        return true;
    }
}
