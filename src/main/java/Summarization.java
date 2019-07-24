import com.alibaba.flink.ml.operator.coding.RowCSVCoding;
import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.client.TFUtils;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCoding;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig.ObjectType;
import com.alibaba.flink.ml.tensorflow.util.TFConstants;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.curator.test.TestingServer;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.ml.lib.tensorflow.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Summarization {
    private static Logger LOG = LoggerFactory.getLogger(Summarization.class);
    public static final String CONFIG_HYPERPARAMETER = "TF_Hyperparameter";
    public static final String[] scripts = {
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/run_summarization.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/__init__.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/attention_decoder.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/batcher.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/beam_search.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/data.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/decode.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/inspect_checkpoint.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/model.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/util.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/flink_writer.py",
            "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/train.py",
    };

    private static void setExampleCodingTypeRow(TFConfig config) {
        String[] names = {"article"};
        com.alibaba.flink.ml.operator.util.DataTypes[] types = {com.alibaba.flink.ml.operator.util.DataTypes.STRING};
        String strInput = ExampleCodingConfig.createExampleConfigStr(names, types,
                ExampleCodingConfig.ObjectType.ROW, String.class);
        config.getProperties().put(TFConstants.INPUT_TF_EXAMPLE_CONFIG, strInput);
        LOG.info("input tf example config: " + strInput);

        String[] namesOutput = {"abstract", "reference"};
        com.alibaba.flink.ml.operator.util.DataTypes[] typesOutput = {com.alibaba.flink.ml.operator.util.DataTypes.STRING,
                com.alibaba.flink.ml.operator.util.DataTypes.STRING};
        String strOutput = ExampleCodingConfig.createExampleConfigStr(namesOutput, typesOutput,
                ExampleCodingConfig.ObjectType.ROW, String.class);
        config.getProperties().put(TFConstants.OUTPUT_TF_EXAMPLE_CONFIG, strOutput);
        LOG.info("output tf example config: " + strOutput);

        config.getProperties().put(MLConstants.ENCODING_CLASS, ExampleCoding.class.getCanonicalName());
        config.getProperties().put(MLConstants.DECODING_CLASS, ExampleCoding.class.getCanonicalName());
    }

    private static void setCsvCodingTypeRow(TFConfig config) {
        config.getProperties().put(RowCSVCoding.DELIM_CONFIG, "#");
        config.getProperties().put(MLConstants.ENCODING_CLASS, RowCSVCoding.class.getCanonicalName());
        config.getProperties().put(MLConstants.DECODING_CLASS, RowCSVCoding.class.getCanonicalName());

        StringBuilder inputSb = new StringBuilder();
        inputSb.append(com.alibaba.flink.ml.operator.util.DataTypes.STRING.name());
        config.getProperties().put(RowCSVCoding.ENCODE_TYPES, inputSb.toString());
        inputSb.append(",").append(com.alibaba.flink.ml.operator.util.DataTypes.STRING.name());
        config.getProperties().put(RowCSVCoding.DECODE_TYPES, inputSb.toString());
    }

    public static void inference() throws Exception {
        // local zookeeper server
        TestingServer server = new TestingServer(2181, true);
        String[] hyperparameter = {
                "run_summarization.py", // first param is uesless but required
                "--mode=decode",
                "--data_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/cnn_stories_test/0*",
                "--vocab_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/vocab",
                "--log_root=/Users/bodeng/TextSummarization-On-Flink/log",
                "--exp_name=pretrained_model_tf1.2.1",
                "--max_enc_steps=400",
                "--max_dec_steps=100",
                "--coverage=1",
                "--single_pass=1",
                "--inference=1",
        };

        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
//        tableEnv.registerFunction("LEN", new LEN());
        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()), "article");

        // if zookeeper has other address
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, "127.0.0.1:2181");
        prop.put(CONFIG_HYPERPARAMETER, String.join(" ", hyperparameter));
        TFConfig config = new TFConfig(1, 0, prop, scripts, "main_on_flink", null);
//        setCsvCodingTypeRow(config);
//        setExampleCodingTypeRow(config);

        TableSchema outSchema = new TableSchema(new String[]{"abstract", "reference"},
                new TypeInformation[]{BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO});
        Utils.configureExampleCoding(config, input.getSchema(), outSchema, ObjectType.ROW, String.class);
//        input = input.select("LEN(article) as len, article");
        input.printSchema();
        tableEnv.toRetractStream(input, new RowTypeInfo(BasicTypeInfo.STRING_TYPE_INFO)).print();

        Table resultTable = TFUtils.inference(streamEnv, tableEnv, input, config, outSchema);
        tableEnv.toRetractStream(resultTable,
                new RowTypeInfo(BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.STRING_TYPE_INFO)).print();
        streamEnv.execute();

        server.stop();
    }

    public static void training() throws Exception {
        // local zookeeper server
        TestingServer server = new TestingServer(2181, true);
        String[] hyperparameter = {
                "run_summarization.py", // first param is uesless but required
                "--mode=train",
                "--data_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/chunked/train_*",
                "--vocab_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/vocab",
                "--log_root=/Users/bodeng/TextSummarization-On-Flink/log",
                "--exp_name=pretrained_model_tf1.2.1",
                "--max_enc_steps=400",
                "--max_dec_steps=100",
                "--coverage=1",
                "--num_steps=3",
        };


        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);

        // if zookeeper has other address
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, "127.0.0.1:2181");
        prop.put(CONFIG_HYPERPARAMETER, String.join(" ", hyperparameter));
        TFConfig config = new TFConfig(1, 0, prop, scripts, "main_on_flink", null);

        TFUtils.train(streamEnv, tableEnv, null, config, null);
        streamEnv.execute();
        server.stop();
    }

    private static List<String> createArticleData() {
        return Arrays.asList("article 1.", "article 2.", "article 3.", "article 4.", "article 5.",
                "article 6.", "article 7.", "article 8.", "article 9.", "article 10.");
    }

    public static void main(String[] args) throws Exception {
        training();
//        inference();
    }
}
