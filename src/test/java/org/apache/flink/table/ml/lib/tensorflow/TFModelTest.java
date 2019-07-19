package org.apache.flink.table.ml.lib.tensorflow;

import com.alibaba.flink.ml.operator.util.DataTypes;
import org.apache.curator.test.TestingServer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TFModelTest {
    private static final String[] scripts = {
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
    private static final String[] inference_hyperparameter = {
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
    private static final String[] train_hyperparameter = {
            "run_summarization.py", // first param is uesless but required
            "--mode=train",
            "--data_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/chunked/test_*",
            "--vocab_path=/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/vocab",
            "--log_root=/Users/bodeng/TextSummarization-On-Flink/log",
            "--exp_name=pretrained_model_tf1.2.1",
            "--max_enc_steps=400",
            "--max_dec_steps=100",
            "--coverage=1",
            "--num_steps=1",
    };

    @Test
    public void testInferenceAfterTraining() throws Exception {
        TestingServer server = new TestingServer(2181, true);

        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table fakeInput = tableEnv.fromDataStream(streamEnv.fromCollection(createDummyData()));
        TFEstimator estimator = new TFEstimator()
                .setZookeeperConnStr("127.0.0.1:2181")
                .setWorkerNum(1)
                .setPsNum(0)

                .setTrainScripts(scripts)
                .setTrainMapFunc("main_on_flink")
                .setTrainHyperParams(train_hyperparameter)
                .setTrainEnvPath(null)
                .setTrainSelectedCols(new String[]{})
                .setTrainOutputCols(new String[]{})
                .setTrainOutputTypes(new DataTypes[]{})

                .setInferenceScripts(scripts)
                .setInferenceMapFunc("main_on_flink")
                .setInferenceHyperParams(inference_hyperparameter)
                .setInferenceEnvPath(null)
                .setInferenceSelectedCols(new String[]{ "article" })
                .setInferenceOutputCols(new String[]{ "abstract", "reference" })
                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING});
        TFModel model = estimator.fit(tableEnv, fakeInput);

        // // create a new environment
        streamEnv.execute();
        streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        tableEnv = StreamTableEnvironment.create(streamEnv);
        // // create a new environment

        Table realInput = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()), "article");
        Table output = model.transform(tableEnv, realInput);
        tableEnv.toAppendStream(output, Row.class).print();
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testModelInference() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()), "article");

        TFModel model = new TFModel()
                .setZookeeperConnStr("127.0.0.1:2181")
                .setWorkerNum(1)
                .setPsNum(0)
                .setInferenceScripts(scripts)
                .setInferenceMapFunc("main_on_flink")
                .setInferenceHyperParams(inference_hyperparameter)
                .setInferenceEnvPath(null)
                .setInferenceSelectedCols(new String[]{ "article" })
                .setInferenceOutputCols(new String[]{ "abstract", "reference" })
                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING});
        Table output = model.transform(tableEnv, input);

        tableEnv.toAppendStream(output, Row.class).print();
        streamEnv.execute();
        server.stop();
    }

    @Test
    public void testModelTraining() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createDummyData()));
        TFEstimator estimator = new TFEstimator()
                .setZookeeperConnStr("127.0.0.1:2181")
                .setWorkerNum(1)
                .setPsNum(0)

                .setTrainScripts(scripts)
                .setTrainMapFunc("main_on_flink")
                .setTrainHyperParams(train_hyperparameter)
                .setTrainEnvPath(null)
                .setTrainSelectedCols(new String[]{})
                .setTrainOutputCols(new String[]{})
                .setTrainOutputTypes(new DataTypes[]{})

                .setInferenceScripts(scripts)
                .setInferenceMapFunc("main_on_flink")
                .setInferenceHyperParams(inference_hyperparameter)
                .setInferenceEnvPath(null)
                .setInferenceSelectedCols(new String[]{ "article" })
                .setInferenceOutputCols(new String[]{ "abstract", "reference" })
                .setInferenceOutputTypes(new DataTypes[] {DataTypes.STRING, DataTypes.STRING});
        estimator.fit(tableEnv, input);
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

    private List<String> createArticleData() {
        return Arrays.asList("article 1.", "article 2.", "article 3.", "article 4.", "article 5.",
                "article 6.", "article 7.", "article 8.", "article 9.", "article 10.");
    }
}
