## [[Issue](https://github.com/alibaba/flink-ai-extended/issues/11)] The streaming inference result needs to wait until the next query to write to sink

In the stream processing environment, after reading data from the source and processing it through Flink-AI-Extended, the result is **not immediately written to the sink**, but is not written **until the next data** of the source arrives.

I built a simple demo for stream processing. Source injects a message every 5 seconds, a total of 25. The python part is immediately written back to the sink after reading.
When I inject a message into the source, the log shows that the python process has received it and executed "context.output_writer_op" in python, but the sink did not receive any messages. When I continue to inject a message into the source, the last result is written to the sink.

The following is the log:

```java
...
[Source][2019-07-31 11:45:56.76]produce data-10
[Sink][2019-07-31 11:45:56.76]finish data-9

[Source][2019-07-31 11:46:01.765]produce data-11
[Sink][2019-07-31 11:46:01.765]finish data-10
...
```

But I want to write back to sink immediately after executing "output_writer_op":

```java
...
[Source][2019-07-31 11:45:56.76]produce data-10
[Sink][2019-07-31 11:45:56.76]finish data-10

[Source][2019-07-31 11:46:01.765]produce data-11
[Sink][2019-07-31 11:46:01.765]finish data-11
...
```

For the time being, it is not clear why it is the cause of this situation.

The following is my demo code:

```java
package org.apache.flink.table.ml.lib.tensorflow;

import com.alibaba.flink.ml.operator.util.DataTypes;
import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.client.TFUtils;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCoding;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig;
import com.alibaba.flink.ml.tensorflow.util.TFConstants;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.curator.test.TestingServer;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.api.java.StreamTableEnvironment;
import org.apache.flink.table.ml.lib.tensorflow.util.Utils;
import org.apache.flink.types.Row;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class SourceSinkTest {
    private static final String ZookeeperConn = "127.0.0.1:2181";
    private static final String[] Scripts = {"/Users/bodeng/TextSummarization-On-Flink/src/main/python/pointer-generator/test.py"};
    private static final int WorkerNum = 1;
    private static final int PsNum = 0;

    @Test
    public void testSourceSink() throws Exception {
        TestingServer server = new TestingServer(2181, true);
        StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
        streamEnv.setRestartStrategy(RestartStrategies.noRestart());

        DataStream<Row> sourceStream = streamEnv.addSource(
                new DummyTimedSource(20, 5), new RowTypeInfo(Types.STRING)).setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
        Table input = tableEnv.fromDataStream(sourceStream, "input");
        TFConfig config = createTFConfig("test_source_sink");

        TableSchema outputSchema = new TableSchema(new String[]{"output"}, new TypeInformation[]{BasicTypeInfo.STRING_TYPE_INFO});

        // configure encode coding
        String strInput = ExampleCodingConfig.createExampleConfigStr(
                new String[]{"input"}, new DataTypes[]{DataTypes.STRING},
                ExampleCodingConfig.ObjectType.ROW, Row.class);
        config.getProperties().put(TFConstants.INPUT_TF_EXAMPLE_CONFIG, strInput);
        config.getProperties().put(MLConstants.ENCODING_CLASS,
                ExampleCoding.class.getCanonicalName());

        // configure decode coding
        String strOutput = ExampleCodingConfig.createExampleConfigStr(
                new String[]{"output"}, new DataTypes[]{DataTypes.STRING},
                ExampleCodingConfig.ObjectType.ROW, Row.class);
        config.getProperties().put(TFConstants.OUTPUT_TF_EXAMPLE_CONFIG, strOutput);
        config.getProperties().put(MLConstants.DECODING_CLASS,
                ExampleCoding.class.getCanonicalName());
      
        Table output = TFUtils.inference(streamEnv, tableEnv, input, config, outputSchema);
        tableEnv.toAppendStream(output, Row.class)
                .map(r -> "[Sink][" + new Timestamp(System.currentTimeMillis()) + "]finish " + r.getField(0) + "\n")
                .print().setParallelism(1);

        streamEnv.execute();
        server.stop();
    }

    private TFConfig createTFConfig(String mapFunc) {
        Map<String, String> prop = new HashMap<>();
        prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
        prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, ZookeeperConn);
        return new TFConfig(WorkerNum, PsNum, prop, Scripts, mapFunc, null);
    }

    private static class DummyTimedSource implements SourceFunction<Row>, CheckpointedFunction {
        public static final Logger LOG = LoggerFactory.getLogger(DummyTimedSource.class);
        private long count = 0L;
        private long MAX_COUNT;
        private long INTERVAL;
	    private volatile boolean isRunning = true;

        private transient ListState<Long> checkpointedCount;

        public DummyTimedSource(long maxCount, long interval) {
            this.MAX_COUNT = maxCount;
            this.INTERVAL = interval;
        }

        @Override
        public void run(SourceContext<Row> ctx) throws Exception {
            while (isRunning && count < MAX_COUNT) {
                // this synchronized block ensures that state checkpointing,
                // internal state updates and emission of elements are an atomic operation
                synchronized (ctx.getCheckpointLock()) {
                    Row row = new Row(1);
                    row.setField(0, String.format("data-%d", count));
                    System.out.println("[Source][" + new Timestamp(System.currentTimeMillis()) + "]produce " + row.getField(0));
                    ctx.collect(row);
                    count++;
                    Thread.sleep(INTERVAL * 1000);
                }
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }

        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            this.checkpointedCount.clear();
            this.checkpointedCount.add(count);
        }

        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            this.checkpointedCount = context
                    .getOperatorStateStore()
                    .getListState(new ListStateDescriptor<>("count", Long.class));

            if (context.isRestored()) {
                for (Long count : this.checkpointedCount.get()) {
                    this.count = count;
                }
            }
        }
    }
}

```

and python code:

```python
import sys
import datetime

import tensorflow as tf
from flink_ml_tensorflow.tensorflow_context import TFContext


class FlinkReader(object):
    def __init__(self, context, batch_size=1, features={'input': tf.FixedLenFeature([], tf.string)}):
        self._context = context
        self._batch_size = batch_size
        self._features = features
        self._build_graph()

    def _decode(self, features):
        return features['input']

    def _build_graph(self):
        dataset = self._context.flink_stream_dataset()
        dataset = dataset.map(lambda record: tf.parse_single_example(record, features=self._features))
        dataset = dataset.map(self._decode)
        dataset = dataset.batch(self._batch_size)
        iterator = dataset.make_one_shot_iterator()
        self._next_batch = iterator.get_next()

    def next_batch(self, sess):
        try:
            batch = sess.run(self._next_batch)
            return batch
        except tf.errors.OutOfRangeError:
            return None


class FlinkWriter(object):
    def __init__(self, context):
        self._context = context
        self._build_graph()

    def _build_graph(self):
        self._write_feed = tf.placeholder(dtype=tf.string)
        self.write_op, self._close_op = self._context.output_writer_op([self._write_feed])

    def _example(self, results):
        example = tf.train.Example(features=tf.train.Features(
            feature={
                'output': tf.train.Feature(bytes_list=tf.train.BytesList(value=[results[0]])),
            }
        ))
        return example

    def write_result(self, sess, results):
        sess.run(self.write_op, feed_dict={self._write_feed: self._example(results).SerializeToString()})

    def close(self, sess):
        sess.run(self._close_op)



def test_source_sink(context):
    tf_context = TFContext(context)
    if 'ps' == tf_context.get_role_name():
        from time import sleep
        while True:
            sleep(1)
    else:
        index = tf_context.get_index()
        job_name = tf_context.get_role_name()
        cluster_json = tf_context.get_tf_cluster()
        cluster = tf.train.ClusterSpec(cluster=cluster_json)

        server = tf.train.Server(cluster, job_name=job_name, task_index=index)
        sess_config = tf.ConfigProto(allow_soft_placement=True, log_device_placement=False,
                                     device_filters=["/job:ps", "/job:worker/task:%d" % index])
        with tf.device(tf.train.replica_device_setter(worker_device='/job:worker/task:' + str(index), cluster=cluster)):
            reader = FlinkReader(tf_context)
            writer = FlinkWriter(tf_context)

            with tf.train.ChiefSessionCreator(master=server.target, config=sess_config).create_session() as sess:
                while True:
                    batch = reader.next_batch(sess)
                    if batch is None:
                        break
                    # tf.logging.info("[TF][%s]process %s" % (str(datetime.datetime.now()), str(batch)))

                    writer.write_result(sess, batch)
                writer.close(sess)
                sys.stdout.flush()
```

