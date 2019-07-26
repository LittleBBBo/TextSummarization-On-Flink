package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

/**
 * Parameters for python configuration in inference process, including:
 * 1. paths of python scripts
 * 2. entry function in main python file
 * 3. key to get hyper parameter in python
 * 4. hyper parameter for python
 * 5. virtual environment path
 * @param <T> the actual type of this WithParams, as the return type of setter
 */
public interface HasInferencePythonConfig<T> extends WithParams<T> {
    ParamInfo<String[]> INFERENCE_SCRIPTS = ParamInfoFactory
            .createParamInfo("inference_scripts", String[].class)
            .setDescription("python scripts path, the first file entry, for inference processing")
            .setRequired().build();
    ParamInfo<String> INFERENCE_MAP_FUNC = ParamInfoFactory
            .createParamInfo("inference_map_func", String.class)
            .setDescription("the entry function in entry file to be called, for inference processing")
            .setRequired().build();
    ParamInfo<String> INFERENCE_HYPER_PARAMS_KEY = ParamInfoFactory
            .createParamInfo("inference_hyper_params_key", String.class)
            .setDescription("the key name to get hyper params from context inf TensorFlow, for inference processing")
            .setRequired()
            .build();
    ParamInfo<String[]> INFERENCE_HYPER_PARAMS = ParamInfoFactory
            .createParamInfo("inference_hyper_params", String[].class)
            .setDescription("hyper params for TensorFlow, each param format is '--param1=value1', for inference processing")
            .setRequired()
            .setHasDefaultValue(new String[]{}).build();
    ParamInfo<String> INFERENCE_ENV_PATH = ParamInfoFactory
            .createParamInfo("inference_env_path", String.class)
            .setDescription("virtual environment path, for inference processing")
            .setOptional()
            .setHasDefaultValue(null).build();

    default String[] getInferenceScripts() {
        return get(INFERENCE_SCRIPTS);
    }

    default T setInferenceScripts(String[] scripts) {
        return set(INFERENCE_SCRIPTS, scripts);
    }

    default String getInferenceMapFunc() {
        return get(INFERENCE_MAP_FUNC);
    }

    default T setInferenceMapFunc(String mapFunc) {
        return set(INFERENCE_MAP_FUNC, mapFunc);
    }

    default String getInferenceHyperParamsKey() {
        return get(INFERENCE_HYPER_PARAMS_KEY);
    }

    default T setInferenceHyperParamsKey(String key) {
        return set(INFERENCE_HYPER_PARAMS_KEY, key);
    }

    default String[] getInferenceHyperParams() {
        return get(INFERENCE_HYPER_PARAMS);
    }

    default T setInferenceHyperParams(String[] hyperParams) {
        return set(INFERENCE_HYPER_PARAMS, hyperParams);
    }

    default String getInferenceEnvPath() {
        return get(INFERENCE_ENV_PATH);
    }

    default T setInferenceEnvPath(String envPath) {
        return set(INFERENCE_ENV_PATH, envPath);
    }
}
