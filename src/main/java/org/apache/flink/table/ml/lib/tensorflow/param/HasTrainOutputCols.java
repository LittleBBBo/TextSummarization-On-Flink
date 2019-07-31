package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

/**
 * An interface for classes with a parameter specifying the names of multiple output columns.
 * @param <T> the actual type of this WithParams, as the return type of setter
 */
public interface HasTrainOutputCols<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_OUTPUT_COLS = ParamInfoFactory
            .createParamInfo("trainOutputCols", String[].class)
            .setDescription("Names of the output columns for train processing")
            .setRequired()
            .build();

    default String[] getTrainOutputCols() {
        return get(TRAIN_OUTPUT_COLS);
    }

    default T setTrainOutputCols(String... value) {
        return set(TRAIN_OUTPUT_COLS, value);
    }
}
