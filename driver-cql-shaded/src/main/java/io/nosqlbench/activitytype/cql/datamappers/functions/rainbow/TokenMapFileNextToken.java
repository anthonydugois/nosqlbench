package io.nosqlbench.activitytype.cql.datamappers.functions.rainbow;

import io.nosqlbench.virtdata.api.annotations.ThreadSafeMapper;

/**
 * Utility function used for advanced data generation experiments.
 */
@ThreadSafeMapper
public class TokenMapFileNextToken extends TokenMapFileBaseFunction {

    public TokenMapFileNextToken(String filename, boolean loopdata, boolean ascending) {
        super(filename, loopdata, false, ascending);
    }

    @Override
    public long applyAsLong(int value) {
        TokenMapFileAPIService datasvc = tl_DataSvc.get();
        datasvc.next(value);
        return datasvc.getToken();
    }
}
