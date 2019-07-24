package org.apache.flink.table.ml.lib.tensorflow;

import com.alibaba.flink.ml.operator.util.DataTypes;
import org.apache.curator.test.TestingServer;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SocketClientSink;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.core.appender.mom.kafka.DefaultKafkaProducerFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TFModelTest {
    public static final Logger LOG = LoggerFactory.getLogger(TFModelTest.class);
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
    public static final String[] inference_hyperparameter = {
            "run_summarization.py", // first param is uesless but required
            "--mode=decode",
            "--data_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/cnn_stories_test/0*",
            "--vocab_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/vocab",
            "--log_root=/Users/bodeng/TextSummarization-On-Flink/log",
            "--exp_name=pretrained_model_tf1.2.1",
            "--batch_size=8", // default to 16
            "--max_enc_steps=400",
            "--max_dec_steps=100",
            "--coverage=1",
            "--single_pass=1",
            "--inference=1",
    };
    public static final String[] train_hyperparameter = {
            "run_summarization.py", // first param is uesless but required
            "--mode=train",
            "--data_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/chunked/train_*",
            "--vocab_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/vocab",
            "--log_root=/Users/bodeng/TextSummarization-On-Flink/log",
            "--exp_name=pretrained_model_tf1.2.1",
            "--batch_size=8", // default to 16
            "--max_enc_steps=400",
            "--max_dec_steps=100",
            "--coverage=1",
            "--num_steps=1", // if 0, never stop
    };

    @Test
    public void testInferenceAfterTraining() throws Exception {
        TestingServer server = new TestingServer(2181, true);

        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table fakeInput = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");
        TFEstimator estimator = createEstimator();
        TFModel model = estimator.fit(tableEnv, fakeInput);

        // // create a new environment
        streamEnv.execute();
        streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        tableEnv = StreamTableEnvironment.create(streamEnv);
        // // create a new environment

        Table realInput = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");
        Table output = model.transform(tableEnv, realInput);
        tableEnv.toAppendStream(output, Row.class).print().setParallelism(1);
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testModelInference() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");

        TFModel model = createModel();
        Table output = model.transform(tableEnv, input);

        tableEnv.toAppendStream(output, Row.class).print().setParallelism(1);
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testModelTraining() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");
        TFEstimator estimator = createEstimator();
        estimator.fit(tableEnv, input);
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testInferenceFromSocket() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(
                streamEnv.socketTextStream("localhost", 9000, "\n", 10),
                "article");

        TFModel model = createModel();
        Table output = model.transform(tableEnv, input);
        tableEnv.toAppendStream(output, Row.class)
                .map(row ->  (String) row.getField(0))
                .writeToSocket("localhost", 9001, new SimpleStringSchema());
        tableEnv.toAppendStream(output, Row.class).print().setParallelism(1);
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testJsonExportImport() throws Exception {
        TestingServer server = new TestingServer(2181, true);

        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table fakeInput = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");
        TFEstimator estimator = createEstimator();
        TFModel model = estimator.fit(tableEnv, fakeInput);
        String modelStr = model.toJson();
        LOG.info("json of model: " + modelStr);
        // // create a new environment
        streamEnv.execute();
        streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        tableEnv = StreamTableEnvironment.create(streamEnv);
        // // create a new environment

        Table realInput = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()),
                "uuid,article,summary,reference");
        TFModel loadedModel = new TFModel();
        loadedModel.loadJson(modelStr);
        Table output = loadedModel.transform(tableEnv, realInput);
        tableEnv.toAppendStream(output, Row.class).print().setParallelism(1);
        streamEnv.execute();
        server.stop();
    }

    private List<Row> createDummyData() {
        List<Row> data = new ArrayList<>();
        Row r = new Row(1);
        r.setField(0, 1);
        data.add(r);
        return data;
    }

    private List<Row> createArticleData() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Row row = new Row(4);
            row.setField(0, String.format("uuid-%d", i));
            row.setField(1, String.format("article %d.", i));
            row.setField(2, "");
            row.setField(3, String.format("reference %d.", i));
            rows.add(row);
        }
        return rows;
//        return Arrays.asList("article 1.", "article 2.", "article 3.", "article 4.", "article 5.",
//                "article 6.", "article 7.", "article 8.", "article 9.", "article 10.");
    }

    public static TFModel createModel() {
        return new TFModel()
                .setZookeeperConnStr("127.0.0.1:2181")
                .setWorkerNum(1)
                .setPsNum(0)
                .setInferenceScripts(scripts)
                .setInferenceMapFunc("main_on_flink")
                .setInferenceHyperParams(inference_hyperparameter)
                .setInferenceEnvPath(null)
                .setInferenceSelectedCols(new String[]{ "uuid", "article", "reference" })
                .setInferenceOutputCols(new String[]{ "uuid", "article", "summary", "reference" })
//                .setInferenceOutputCols(new String[]{ "abstract", "reference" })
                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING, DataTypes.STRING, DataTypes.STRING});
//                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING});
    }

    public static TFEstimator createEstimator() {
        return new TFEstimator()
                .setZookeeperConnStr("127.0.0.1:2181")
                .setWorkerNum(1)
                .setPsNum(0)

                .setTrainScripts(scripts)
                .setTrainMapFunc("main_on_flink")
                .setTrainHyperParams(train_hyperparameter)
                .setTrainEnvPath(null)
                .setTrainSelectedCols(new String[]{ "uuid", "article", "reference" })
                .setTrainOutputCols(new String[]{ "uuid"})
                .setTrainOutputTypes(new DataTypes[]{ DataTypes.STRING })

                .setInferenceScripts(scripts)
                .setInferenceMapFunc("main_on_flink")
                .setInferenceHyperParams(inference_hyperparameter)
                .setInferenceEnvPath(null)
                .setInferenceSelectedCols(new String[]{ "uuid", "article", "reference" })
                .setInferenceOutputCols(new String[]{ "uuid", "article", "summary", "reference" })
                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING, DataTypes.STRING, DataTypes.STRING});
    }
}
