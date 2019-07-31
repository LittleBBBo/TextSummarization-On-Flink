package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

/**
 * An interface for classes with a parameter specifying the names of multiple output columns.
 * @param <T> the actual type of this WithParams, as the return type of setter
 */
public interface HasInferenceOutputCols<T> extends WithParams<T> {
    ParamInfo<String[]> INFERENCE_OUTPUT_COLS = ParamInfoFactory
            .createParamInfo("inferenceOutputCols", String[].class)
            .setDescription("Names of the output columns for inference processing")
            .setRequired()
            .build();

    default String[] getInferenceOutputCols() {
        return get(INFERENCE_OUTPUT_COLS);
    }

    default T setInferenceOutputCols(String... value) {
        return set(INFERENCE_OUTPUT_COLS, value);
    }
}
