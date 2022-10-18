package io.nosqlbench.virtdata.library.curves4.discrete.long_long;

import io.nosqlbench.virtdata.api.annotations.ThreadSafeMapper;
import org.apache.commons.statistics.distribution.ApproximatedZipfDistribution;

@ThreadSafeMapper
public class ApproximatedZipf extends LongToLongDiscreteCurve {
    public ApproximatedZipf(int numberOfElements, double exponent, String... modslist) {
        super(new ApproximatedZipfDistribution(numberOfElements, exponent), modslist);
    }
}
