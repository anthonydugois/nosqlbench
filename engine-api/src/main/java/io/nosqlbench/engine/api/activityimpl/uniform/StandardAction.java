/*
 * Copyright (c) 2022 nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nosqlbench.engine.api.activityimpl.uniform;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import io.nosqlbench.engine.api.activityapi.core.ActivityDefObserver;
import io.nosqlbench.engine.api.activityapi.core.SyncAction;
import io.nosqlbench.engine.api.activityapi.errorhandling.modular.ErrorDetail;
import io.nosqlbench.engine.api.activityapi.errorhandling.modular.NBErrorHandler;
import io.nosqlbench.engine.api.activityapi.planning.OpSequence;
import io.nosqlbench.api.engine.activityimpl.ActivityDef;
import io.nosqlbench.engine.api.activityimpl.OpDispenser;
import io.nosqlbench.engine.api.activityimpl.uniform.flowtypes.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * This is the generified version of an Action. All activity types should endeavor to use
 * this, as the API surface is being consolidated so that the internal machinery of NB
 * works in a very consistent and uniform way.
 * There will be changes to multiple drivers to support this consolidation, but the bulk
 * of this work will be undertaken by the project maintainers.
 *
 * @param <A> The type of activity
 * @param <R> The type of operation
 */
public class StandardAction<A extends StandardActivity<R, ?>, R extends Op> implements SyncAction, ActivityDefObserver {
    private final static Logger logger = LogManager.getLogger("ACTION");

    private final A activity;
    private final int slot;
    private final Timer executeTimer;
    private final Histogram triesHistogram;
    private final Timer resultSuccessTimer;
    private final Timer smallLatencyTimer;
    private final Timer largeLatencyTimer;
    private final Histogram stretchHistogram;
    private final Histogram smallStretchHistogram;
    private final Histogram largeStretchHistogram;
    private final Timer resultTimer;
    private final Timer bindTimer;
    private final NBErrorHandler errorHandler;
    private final OpSequence<OpDispenser<? extends Op>> opsequence;

    public StandardAction(A activity, int slot) {
        this.activity = activity;
        this.opsequence = activity.getOpSequence();
        this.slot = slot;
        bindTimer = activity.getInstrumentation().getOrCreateBindTimer();
        executeTimer = activity.getInstrumentation().getOrCreateExecuteTimer();
        triesHistogram = activity.getInstrumentation().getOrCreateTriesHistogram();
        resultTimer = activity.getInstrumentation().getOrCreateResultTimer();
        resultSuccessTimer = activity.getInstrumentation().getOrCreateResultSuccessTimer();
        smallLatencyTimer = activity.getInstrumentation().getOrCreateSmallLatencyTimer();
        largeLatencyTimer = activity.getInstrumentation().getOrCreateLargeLatencyTimer();
        stretchHistogram = activity.getInstrumentation().getOrCreateStretchHistogram();
        smallStretchHistogram = activity.getInstrumentation().getOrCreateSmallStretchHistogram();
        largeStretchHistogram = activity.getInstrumentation().getOrCreateLargeStretchHistogram();
        errorHandler = activity.getErrorHandler();
    }

    @Override
    public int runCycle(long cycle) {

        OpDispenser<? extends Op> dispenser;
        Op op = null;

        try (Timer.Context ct = bindTimer.time()) {
            dispenser = opsequence.apply(cycle);
            op = dispenser.apply(cycle);
        } catch (Exception e) {
            throw new RuntimeException("while binding request in cycle " + cycle + ": " + e.getMessage(), e);
        }

        int code = 0;
        Object result = null;
        while (op != null) {

            int tries = 0;
            while (tries++ <= activity.getMaxTries()) {
                Throwable error = null;
                long startedAt = System.nanoTime();

                dispenser.onStart(cycle);

                try (Timer.Context ct = executeTimer.time()) {
                    if (op instanceof RunnableOp) {
                        ((RunnableOp) op).run();
                    } else if (op instanceof CycleOp<?>) {
                        result = ((CycleOp) op).apply(cycle);
                    } else if (op instanceof ChainingOp) {
                        result = ((ChainingOp) op).apply(result);
                    } else {
                        throw new RuntimeException("The op implementation did not implement any active logic. Implement " +
                            "one of [RunnableOp, CycleOp, or ChainingOp]");
                    }
                } catch (Exception e) {
                    error = e;
                } finally {
                    long nanos = System.nanoTime() - startedAt;

                    resultTimer.update(nanos, TimeUnit.NANOSECONDS);

                    if (error == null) {
                        long resultSize = op.getResultSize();

                        resultSuccessTimer.update(nanos, TimeUnit.NANOSECONDS);

                        if (resultSize > 0) {
                            long stretch = nanos / resultSize;

                            stretchHistogram.update(stretch);

                            if (resultSize > 10000) {
                                largeLatencyTimer.update(nanos, TimeUnit.NANOSECONDS);
                                largeStretchHistogram.update(stretch);
                            } else {
                                smallLatencyTimer.update(nanos, TimeUnit.NANOSECONDS);
                                smallStretchHistogram.update(stretch);
                            }
                        }

                        dispenser.onSuccess(cycle, nanos, resultSize);

                        break;
                    } else {
                        ErrorDetail detail = errorHandler.handleError(error, cycle, nanos);
                        dispenser.onError(cycle, nanos, error);
                        code = detail.resultCode;
                        if (!detail.isRetryable()) {
                            break;
                        }
                    }
                }
            }
            triesHistogram.update(tries);

            if (op instanceof OpGenerator) {
                logger.trace("GEN OP for cycle(" + cycle + ")");
                op = ((OpGenerator) op).getNextOp();
            } else {
                op = null;
            }
        }

        return code;
    }

    @Override
    public void onActivityDefUpdate(ActivityDef activityDef) {
    }
}
