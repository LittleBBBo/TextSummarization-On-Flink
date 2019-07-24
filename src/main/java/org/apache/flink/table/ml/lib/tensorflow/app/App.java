package org.apache.flink.table.ml.lib.tensorflow.app;

import com.alibaba.flink.ml.operator.util.DataTypes;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.ml.lib.tensorflow.TFEstimator;
import org.apache.flink.table.ml.lib.tensorflow.TFModel;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class App {
    public static final int MAX_ROW_COUNT = 8;

    public static final String trainTopic = "flink_train";
    public static final String inputTopic = "flink_input";
    public static final String outputTopic = "flink_output";
    public static final String consumerGroup = "bode";
    public static final String kafkaAddress = "127.0.0.1:9092";

    public static final Logger LOG = LoggerFactory.getLogger(App.class);
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
            "--batch_size=2", // default to 16
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
            "--batch_size=2", // default to 16
            "--max_enc_steps=400",
            "--max_dec_steps=100",
            "--coverage=1",
            "--num_steps=2", // if 0, never stop
    };

    public static String startTraining() throws Exception {
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);

        FlinkKafkaConsumer<Row> kafkaConsumer = createMessageConsumer(trainTopic, kafkaAddress, consumerGroup);
        kafkaConsumer.setStartFromEarliest();

        DataStream<Row> dataStream = streamEnv.addSource(kafkaConsumer).setParallelism(1);
        Table input = tableEnv.fromDataStream(dataStream, "uuid,article,summary,reference");

//        Table input = tableEnv.fromDataStream(streamEnv.fromCollection(createArticleData()).setParallelism(1),
//                "uuid,article,summary,reference");

        input.printSchema();

//        input = input.select("uuid,article,reference");
        tableEnv.toAppendStream(input, Row.class).print().setParallelism(1);
        TFEstimator estimator = createEstimator();
        TFModel model = estimator.fit(tableEnv, input);
        streamEnv.execute();
        LOG.info("trained model: " + model.toJson());
        return model.toJson();
    }

    public static void startInference(String modelJson) throws Exception {
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);

        FlinkKafkaConsumer<Row> kafkaConsumer = createMessageConsumer(inputTopic, kafkaAddress, consumerGroup);
        kafkaConsumer.setStartFromEarliest();
        FlinkKafkaProducer<Row> kafkaProducer = createStringProducer(outputTopic, kafkaAddress);

        Table input = tableEnv.fromDataStream(streamEnv
                .addSource(kafkaConsumer, "Kafaka Source")
                .setParallelism(1), "uuid,article,summary,reference");
        input.printSchema();

        tableEnv.toAppendStream(input, Row.class).print().setParallelism(1);

        input = input.select("uuid,article,reference");
        TFModel model = createModel();
        if (modelJson != null) {
            model.loadJson(modelJson);
        }
        Table output = model.transform(tableEnv, input);

        tableEnv.toAppendStream(output, Row.class).print().setParallelism(1);
        tableEnv.toAppendStream(output, Row.class).addSink(kafkaProducer).setParallelism(1);
        streamEnv.execute();
    }

    public static FlinkKafkaConsumer<Row> createMessageConsumer(String topic, String kafkaAddress, String kafkaGroup) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", kafkaAddress);
        props.setProperty("group.id", kafkaGroup);
        return new FlinkKafkaConsumer<>(topic, new MessageDeserializationSchema(MAX_ROW_COUNT), props);
    }

    public static FlinkKafkaProducer<Row> createStringProducer(String topic, String kafkaAddress) {
        return new FlinkKafkaProducer<>(kafkaAddress, topic, new MessageSerializationSchema());
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

    private static List<Row> createArticleData() {
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
    }

    public static void main(String[] args) throws Exception {
//        App.startTraining();
//        App.startInference(null);
        String json = App.startTraining();
        App.startInference(json);
    }
}
