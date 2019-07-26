package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

/**
 * An interface for classes with a parameter specifying the name of multiple selected input columns.
 * @param <T> the actual type of this WithParams, as the return type of setter
 */
public interface HasTrainSelectedCols<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_SELECTED_COLS = ParamInfoFactory
            .createParamInfo("trainSelectedCols", String[].class)
            .setDescription("Names of the columns used for train processing")
            .setRequired()
            .build();

    default String[] getTrainSelectedCols() {
        return (String[])this.get(TRAIN_SELECTED_COLS);
    }

    default T setTrainSelectedCols(String... value) {
        return this.set(TRAIN_SELECTED_COLS, value);
    }
}
