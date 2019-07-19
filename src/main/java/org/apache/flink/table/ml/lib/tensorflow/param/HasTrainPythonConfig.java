package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

public interface HasTrainPythonConfig<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_SCRIPTS = ParamInfoFactory
            .createParamInfo("train_scripts", String[].class)
            .setDescription("python scripts path, the first file entry, for train processing")
            .setRequired().build();
    ParamInfo<String> TRAIN_MAP_FUNC = ParamInfoFactory
            .createParamInfo("train_map_func", String.class)
            .setDescription("the entry function in entry file to be called, for train processing")
            .setRequired().build();
    ParamInfo<String[]> TRAIN_HYPER_PARAMS = ParamInfoFactory
            .createParamInfo("train_hyper_params", String[].class)
            .setDescription("hyper params for TensorFlow, each param format is '--param1=value1', for train processing")
            .setRequired()
            .setHasDefaultValue(new String[]{}).build();
    ParamInfo<String> TRAIN_ENV_PATH = ParamInfoFactory
            .createParamInfo("train_env_path", String.class)
            .setDescription("virtual environment path, for train processing")
            .setOptional()
            .setHasDefaultValue(null).build();

    default String[] getTrainScripts() {
        return get(TRAIN_SCRIPTS);
    }

    default T setTrainScripts(String[] scripts) {
        return set(TRAIN_SCRIPTS, scripts);
    }

    default String getTrainMapFunc() {
        return get(TRAIN_MAP_FUNC);
    }

    default T setTrainMapFunc(String mapFunc) {
        return set(TRAIN_MAP_FUNC, mapFunc);
    }

    default String[] getTrainHyperParams() {
        return get(TRAIN_HYPER_PARAMS);
    }

    default T setTrainHyperParams(String[] hyperParams) {
        return set(TRAIN_HYPER_PARAMS, hyperParams);
    }

    default String getTrainEnvPath() {
        return get(TRAIN_ENV_PATH);
    }

    default T setTrainEnvPath(String envPath) {
        return set(TRAIN_ENV_PATH, envPath);
    }
}
