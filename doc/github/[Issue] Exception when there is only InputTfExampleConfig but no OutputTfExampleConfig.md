## [[Issue](https://github.com/alibaba/flink-ai-extended/issues/10)] Exception when there is only InputTfExampleConfig but no OutputTfExampleConfig

As follows, this is the normal configuration of ExampleCoding (that is, the format of Flink and Python for data transmission), but if only the encode part is configured without configuring the decode part, it will not be executed, and an exception will be thrown. vice versa.

```java
// configure encode example coding
String strInput = ExampleCodingConfig.createExampleConfigStr(encodeNames, encodeTypes, 
                                                             entryType, entryClass);
config.getProperties().put(TFConstants.INPUT_TF_EXAMPLE_CONFIG, strInput);
config.getProperties().put(MLConstants.ENCODING_CLASS,
                           ExampleCoding.class.getCanonicalName());

// configure decode example coding
String strOutput = ExampleCodingConfig.createExampleConfigStr(decodeNames, decodeTypes, 
                                                              entryType, entryClass);
config.getProperties().put(TFConstants.OUTPUT_TF_EXAMPLE_CONFIG, strOutput);
config.getProperties().put(MLConstants.DECODING_CLASS, 
                           ExampleCoding.class.getCanonicalName());
```

Such a usage scenario is relatively common. For example, during the training process, the user only needs to transfer data to the TF without returning the table, so there will only be an encode phase of flink-to-tf without tf-to-flink. For the user, it is also customary to set only the encoding-related configuration.
**Therefore**, I want to be able to configure **only the encode** part **without** configuring the **decode** part, and vice versa.
After review, the main reason is the method of **ReflectUtil.createInstance(className, classes, objects)** in **CodingFactory.java**. This method will create an **ExampleCoding** instance according to **ENCODING_CLASS**. According to the definition of **ExampleCoding.java**, both inputConfig and outputConfig(even if not) will be configured in the constructor, which will result in a NullPointerException.
The following is the exception information:

```java
java.lang.reflect.InvocationTargetException
	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)
	at com.alibaba.flink.ml.util.ReflectUtil.createInstance(ReflectUtil.java:36)
	at com.alibaba.flink.ml.coding.CodingFactory.getEncoding(CodingFactory.java:49)
	at com.alibaba.flink.ml.data.DataExchange.<init>(DataExchange.java:58)
	at com.alibaba.flink.ml.operator.ops.MLMapFunction.open(MLMapFunction.java:80)
	at com.alibaba.flink.ml.operator.ops.MLFlatMapOp.open(MLFlatMapOp.java:51)
	at org.apache.flink.api.common.functions.util.FunctionUtils.openFunction(FunctionUtils.java:36)
	at org.apache.flink.streaming.api.operators.AbstractUdfStreamOperator.open(AbstractUdfStreamOperator.java:102)
	at org.apache.flink.streaming.api.operators.StreamFlatMap.open(StreamFlatMap.java:43)
	at org.apache.flink.streaming.runtime.tasks.StreamTask.openAllOperators(StreamTask.java:424)
	at org.apache.flink.streaming.runtime.tasks.StreamTask.invoke(StreamTask.java:290)
	at org.apache.flink.runtime.taskmanager.Task.run(Task.java:711)
	at java.lang.Thread.run(Thread.java:748)
Caused by: java.lang.NullPointerException
	at com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig.fromJsonObject(ExampleCodingConfig.java:100)
	at com.alibaba.flink.ml.tensorflow.coding.ExampleCoding.<init>(ExampleCoding.java:57)
	... 16 more
```

The following is a detailed test case, Java code:

```java
private static final String ZookeeperConn = "127.0.0.1:2181";
private static final String[] Scripts = {"test.py"};
private static final int WorkerNum = 1;
private static final int PsNum = 0;

@Test
public void testExampleCodingWithoutDecode() throws Exception {
		TestingServer server = new TestingServer(2181, true);
		StreamExecutionEnvironment streamEnv = 
      	StreamExecutionEnvironment.createLocalEnvironment(1);
		streamEnv.setRestartStrategy(RestartStrategies.noRestart());
		StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);
  
		Table input = tableEnv
				.fromDataStream(streamEnv.fromCollection(createDummyData()), "input");
		TableSchema inputSchema = 
				new TableSchema(new String[]{"input"}, 
                    		new TypeInformation[]{BasicTypeInfo.STRING_TYPE_INFO});
		TableSchema outputSchema = null;
  
  	TFConfig config = createTFConfig("test_example_coding_without_decode");
  	// configure encode coding
  	String strInput = ExampleCodingConfig.createExampleConfigStr(
      	new String[]{"input"}, new DataTypes[]{DataTypes.STRING}, 
      	ExampleCodingConfig.ObjectType.ROW, Row.class);
  	config.getProperties().put(TFConstants.INPUT_TF_EXAMPLE_CONFIG, strInput);
		config.getProperties().put(MLConstants.ENCODING_CLASS, 
                               ExampleCoding.class.getCanonicalName());
  
  	// run in python
		Table output = TFUtils.inference(streamEnv, tableEnv, input, config, outputSchema);

		streamEnv.execute();
		server.stop();
}

private List<Row> createDummyData() {
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
				Row row = new Row(1);
				row.setField(0, String.format("data-%d", i));
        rows.add(row);
		}
		return rows;
}

private TFConfig createTFConfig(String mapFunc) {
		Map<String, String> prop = new HashMap<>();
		prop.put(MLConstants.CONFIG_STORAGE_TYPE, MLConstants.STORAGE_ZOOKEEPER);
		prop.put(MLConstants.CONFIG_ZOOKEEPER_CONNECT_STR, ZookeeperConn);
		return new TFConfig(WorkerNum, PsNum, prop, Scripts, mapFunc, null);
}
```

Python code:

```python
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


def test_example_coding_without_decode(context):
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

            with tf.train.ChiefSessionCreator(master=server.target, config=sess_config).create_session() as sess:
                while True:
                    batch = reader.next_batch(sess)
                    tf.logging.info(str(batch))
                    if batch is None:
                        break
                sys.stdout.flush()
```

