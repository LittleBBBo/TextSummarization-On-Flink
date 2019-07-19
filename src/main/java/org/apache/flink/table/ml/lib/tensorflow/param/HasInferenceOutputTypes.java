package org.apache.flink.table.ml.lib.tensorflow.param;

import com.alibaba.flink.ml.operator.util.DataTypes;
import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

public interface HasInferenceOutputTypes<T> extends WithParams<T> {
    ParamInfo<DataTypes[]> INFERENCE_OUTPUT_TYPES = ParamInfoFactory
            .createParamInfo("inferenceOutputTypes", DataTypes[].class)
            .setDescription("TypeInformation of output columns for inference processing")
            .setRequired()
            .build();

    default DataTypes[] getInferenceOutputTypes() {
        return get(INFERENCE_OUTPUT_TYPES);
    }

    default T setInferenceOutputTypes(DataTypes[] outputTypes) {
        return set(INFERENCE_OUTPUT_TYPES, outputTypes);
    }
}
