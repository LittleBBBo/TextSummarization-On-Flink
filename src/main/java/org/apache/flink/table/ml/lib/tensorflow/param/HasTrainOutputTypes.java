package org.apache.flink.table.ml.lib.tensorflow.param;

import com.alibaba.flink.ml.operator.util.DataTypes;
import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

public interface HasTrainOutputTypes<T> extends WithParams<T> {
    ParamInfo<DataTypes[]> TRAIN_OUTPUT_TYPES = ParamInfoFactory
            .createParamInfo("trainOutputTypes", DataTypes[].class)
            .setDescription("TypeInformation of output columns for train processing")
            .setRequired()
            .build();

    default DataTypes[] getTrainOutputTypes() {
        return get(TRAIN_OUTPUT_TYPES);
    }

    default T setTrainOutputTypes(DataTypes[] outputTypes) {
        return set(TRAIN_OUTPUT_TYPES, outputTypes);
    }
}
