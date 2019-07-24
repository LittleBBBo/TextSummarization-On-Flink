package org.apache.flink.table.ml.lib.tensorflow;

import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.client.TFUtils;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.flink.ml.api.core.Model;
import org.apache.flink.ml.api.misc.param.Params;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.ml.lib.tensorflow.param.*;
import org.apache.flink.table.ml.lib.tensorflow.util.Utils;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TFModel implements Model<TFModel>, HasClusterConfig<TFModel>, HasInferencePythonConfig<TFModel>,
        HasInferenceSelectedCols<TFModel>, HasInferenceOutputCols<TFModel>, HasInferenceOutputTypes<TFModel> {
    private static final Logger LOG = LoggerFactory.getLogger(TFModel.class);
    private static final String CONFIG_HYPERPARAMETER = "TF_Hyperparameter";
    private Params params = new Params();

    protected Table configureInputTable(Table rawTable) {
        return rawTable.select(String.join(",", getInferenceSelectedCols()));
    }

    protected TableSchema configureOutputSchema() {
        return new TableSchema(getInferenceOutputCols(),
                Utils.dataTypesListToTypeInformation(getInferenceOutputTypes()));
    }

    protected TFConfig configureTFConfig() {
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, getZookeeperConnStr());
        prop.put(CONFIG_HYPERPARAMETER, String.join(" ", getInferenceHyperParams()));
        return new TFConfig(getWorkerNum(), getPsNum(), prop, getInferenceScripts(), getInferenceMapFunc(), getInferenceEnvPath());
    }

    protected void configureExampleCoding(TFConfig config, TableSchema inputSchema, TableSchema outputSchema) {
        Utils.configureExampleCoding(config, inputSchema, outputSchema, ExampleCodingConfig.ObjectType.ROW, Row.class);
    }

    @Override
    public Table transform(TableEnvironment tableEnvironment, Table table) {
        StreamExecutionEnvironment streamEnv;
        try {
            if (tableEnvironment instanceof StreamTableEnvironment) {
                StreamTableEnvironment streamTableEnvironment = (StreamTableEnvironment)tableEnvironment;
                streamEnv = streamTableEnvironment.toAppendStream(table, Row.class).getExecutionEnvironment();
            } else {
                throw new RuntimeException("Unsupported TableEnvironment, please use StreamTableEnvironment");
            }
            Table inputTable = configureInputTable(table);
            TableSchema outputSchema = configureOutputSchema();
            TFConfig config = configureTFConfig();
            configureExampleCoding(config, inputTable.getSchema(), outputSchema);
            Table outputTable = TFUtils.inference(streamEnv, tableEnvironment, inputTable, config, outputSchema);
            return outputTable;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Params getParams() {
        return params;
    }
}
