import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.client.TFUtils;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.curator.test.TestingServer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.HashMap;
import java.util.Map;

public class Summarization {
    public static void main(String args[]) throws Exception {
        // local zookeeper server
        TestingServer server = new TestingServer(2181, true);
        String[] scripts = new String[] {
//                "/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/add.py",
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
        };
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.getExecutionEnvironment();
        // if zookeeper has other address
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, "127.0.0.1:2181");

        TFConfig config = new TFConfig(2, 1, prop, scripts, "main_on_flink", null);
        TFUtils.train(streamEnv, null, config);
        streamEnv.execute();
    }
}
