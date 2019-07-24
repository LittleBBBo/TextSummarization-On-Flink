package org.apache.flink.table.ml.lib.tensorflow;

import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.client.TFUtils;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.ml.api.core.Estimator;
import org.apache.flink.ml.api.misc.param.Params;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.ml.lib.tensorflow.param.*;
import org.apache.flink.table.ml.lib.tensorflow.util.Utils;
import org.apache.flink.types.Row;


import java.util.HashMap;
import java.util.Map;

public class TFEstimator implements Estimator<TFEstimator, TFModel>, HasClusterConfig<TFEstimator>,
        HasTrainPythonConfig<TFEstimator>, HasInferencePythonConfig<TFEstimator>,
        HasTrainSelectedCols<TFEstimator>, HasTrainOutputCols<TFEstimator>, HasTrainOutputTypes<TFEstimator>,
        HasInferenceSelectedCols<TFEstimator>, HasInferenceOutputCols<TFEstimator>, HasInferenceOutputTypes<TFEstimator> {
    private static final String CONFIG_HYPERPARAMETER = "TF_Hyperparameter";
    private Params params = new Params();

    protected Table configureInputTable(Table rawTable) {
        if (getTrainSelectedCols().length == 0) {
            return null;
        } else {
            return rawTable.select(String.join(",", getTrainSelectedCols()));
        }
    }
    protected TableSchema configureOutputSchema() {
        if (getTrainOutputCols().length == 0) {
            // fake schema
            return new TableSchema(new String[] {"none"}, new TypeInformation[]{BasicTypeInfo.STRING_TYPE_INFO});
        } else {
            return new TableSchema(getTrainOutputCols(),
                    Utils.dataTypesListToTypeInformation(getTrainOutputTypes()));
        }
    }

    protected TFConfig configureTFConfig() {
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, getZookeeperConnStr());
        prop.put(CONFIG_HYPERPARAMETER, String.join(" ", getTrainHyperParams()));
        return new TFConfig(getWorkerNum(), getPsNum(), prop, getTrainScripts(), getTrainMapFunc(), getTrainEnvPath());
    }

    protected void configureExampleCoding(TFConfig config, TableSchema inputSchema, TableSchema outputSchema) {
        Utils.configureExampleCoding(config, inputSchema, outputSchema, ExampleCodingConfig.ObjectType.ROW, Row.class);
    }

    @Override
    public TFModel fit(TableEnvironment tableEnvironment, Table table) {

        StreamExecutionEnvironment streamEnv;
        try {
            if (tableEnvironment instanceof StreamTableEnvironment) {
                StreamTableEnvironment streamTableEnvironment = (StreamTableEnvironment)tableEnvironment;
                streamEnv = streamTableEnvironment.toAppendStream(table, Row.class).getExecutionEnvironment();
            } else {
                throw new RuntimeException("Unsupported TableEnvironment, please use StreamTableEnvironment");
            }
            Table inputTable = configureInputTable(table);
            TableSchema inputSchema = null;
            if (inputTable != null) {
                inputSchema = inputTable.getSchema();
            }
            TableSchema outputSchema = configureOutputSchema();
            TFConfig config = configureTFConfig();
            configureExampleCoding(config, inputSchema, outputSchema);
            Table outputTable = TFUtils.train(streamEnv, tableEnvironment, inputTable, config, outputSchema);
//            streamEnv.execute();
//            ((StreamTableEnvironment) tableEnvironment).toAppendStream(outputTable, Row.class).print().setParallelism(1);
            TFModel model = new TFModel()
                    .setZookeeperConnStr(getZookeeperConnStr())
                    .setWorkerNum(getWorkerNum())
                    .setPsNum(getPsNum())
                    .setInferenceScripts(getInferenceScripts())
                    .setInferenceMapFunc(getInferenceMapFunc())
                    .setInferenceHyperParams(getInferenceHyperParams())
                    .setInferenceEnvPath(getInferenceEnvPath())
                    .setInferenceSelectedCols(getInferenceSelectedCols())
                    .setInferenceOutputCols(getInferenceOutputCols())
                    .setInferenceOutputTypes(getInferenceOutputTypes());

            return model;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Params getParams() {
        return params;
    }
}
