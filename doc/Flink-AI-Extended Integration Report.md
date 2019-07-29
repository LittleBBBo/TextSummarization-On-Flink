## Flink-AI-Extended Integration Report

在7.15-7.26这段时间内，我基于Flink ML pipeline框架实现了两个通用的class：**TFEstimator**和**TFModel**，这两个class分别在**fit()**和**transform()**方法里封装了Flink-AI-Extended的**train**和**inference**过程，通过**WithParams**接口实现通用参数的配置和传递。

截至7.26，独立的TFEstimator训练并返回TFModel、独立的TFModel引用均已完成功能上的实现与测试，并通过两个Kafka topic作为source和sink构建出一个简单的命令行end-to-end应用。

**但是**，将TFEstimator作为一个**PipelineStage**加入到Pipeline并进行训练时，遇到**Blocker**，原因是pipeline.fit()需要先调用TFEstimator.fit()产生一个TFModel，再用该TFModel来transform输入的table传给下一个PipelineStage，相当于在同一个job里调用Flink-AI-Extended的**train**和**inference**过程，这在目前是不支持的，详见Issue-1。

本文将详细介绍在Flink-AI-Extended到Flink ML pipeline框架的整合过程中 ：

1. 设计实现上的细节；
2. 遇到的问题、发生的场景、可能的解决方案；



###1. Implementation of TFEstimator & TFModel

TFEstimator与TFModel是一个具体的实现类，理论上能运行任何基于Flink-AI-Extended扩展的TF算法，通过一些通用化的参数配置将Flink-AI-Extended的调用过程完全封装起来，目的是让用户仅从自己对TF的知识就能简单的构造一个TF算法的estimator或model。

#### Params

一般来说，使用Flink-AI-Extended时需要配置以下几类参数：

- cluster信息：包括zookeeper地址、worker数量、ps数量
- 输入输出信息：包括input table中需要传给TF的列名、TF返回flink的output table中的列名与对应类型
- python运行信息：包括所有python文件路径、主函数入口、传给python的超参、虚拟环境路径

因此，对每一类的参数设计了统一的接口，并让TFEstimator、TFModel实现了如下这些接口。需要注意的是，对于输入输出和python相关的参数，设计了两套相同的接口分别给Training和Inference过程使用。这样设计是因为虽然TFEstimator与TFModel在一般应用中大部分参数应该是一样的，但无法强制要求用户一定要按这样的规范去开发TF算法，所以保留了给TFEstimator和TFModel独立配置一整套参数的能力。比如说，用户在训练过程中的入口函数是"train_on_flink"，而在引用过程可以是"inference_on_flink"。

```java
package org.apache.flink.table.ml.lib.tensorflow.param;

/**
 * Parameters for cluster configuration, including:
 * 1. zookeeper address
 * 2. worker number
 * 3. ps number
 */
public interface HasClusterConfig<T> extends WithParams<T> {
    ParamInfo<String> ZOOKEEPER_CONNECT_STR;
    ParamInfo<Integer> WORKER_NUM;
    ParamInfo<Integer> PS_NUM;
}


/**
 * Parameters for python configuration in training process, including:
 * 1. paths of python scripts
 * 2. entry function in main python file
 * 3. key to get hyper parameter in python
 * 4. hyper parameter for python
 * 5. virtual environment path
 */
public interface HasTrainPythonConfig<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_SCRIPTS;
    ParamInfo<String> TRAIN_MAP_FUNC;
  	ParamInfo<String> TRAIN_HYPER_PARAMS_KEY;
    ParamInfo<String[]> TRAIN_HYPER_PARAMS;
    ParamInfo<String> TRAIN_ENV_PATH;
}


/**
 * An interface for classes with a parameter specifying 
 * the name of multiple selected input columns.
 */
public interface HasTrainSelectedCols<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_SELECTED_COLS;
}


/**
 * An interface for classes with a parameter specifying 
 * the names of multiple output columns.
 */
public interface HasTrainOutputCols<T> extends WithParams<T> {
    ParamInfo<String[]> TRAIN_OUTPUT_COLS;
}


/**
 * An interface for classes with a parameter specifying
 * the types of multiple output columns.
 */
public interface HasTrainOutputTypes<T> extends WithParams<T> {
    ParamInfo<DataTypes[]> TRAIN_OUTPUT_TYPES;
}


/**
 * Mirrored interfaces for configuration in inference process
 */
public interface HasInferencePythonConfig<T> extends WithParams<T>;
public interface HasInferenceSelectedCols<T> extends WithParams<T>;
public interface HasInferenceOutputCols<T> extends WithParams<T>;
public interface HasInferenceOutputTypes<T> extends WithParams<T>;
```

#### TFModel

通用的TFModel通过HasClusterConfig、HasInferencePythonConfig接口配置集群信息、Python相关信息（如文件路径、超参等）；通过HasInferenceSelectedCols、HasInferenceOutputCols、HasInferenceOutputTypes接口配置与TF进行数据传输相关的encoding、decoding格式。

```java
/**
 * A general TensorFlow model implemented by Flink-AI-Extended,
 * is usually generated by an {@link TFEstimator}
 * when {@link TFEstimator#fit(TableEnvironment, Table)} is invoked.
 */
public class TFModel implements Model<TFModel>, 
		HasClusterConfig<TFModel>, 
		HasInferencePythonConfig<TFModel>, 
		HasInferenceSelectedCols<TFModel>, 
		HasInferenceOutputCols<TFModel>, 
		HasInferenceOutputTypes<TFModel> {
      
    private static final Logger LOG = LoggerFactory.getLogger(TFModel.class);
    private Params params = new Params();

    @Override
    public Table transform(TableEnvironment tableEnvironment, Table table) {
        StreamExecutionEnvironment streamEnv;
        try {
          	// TODO: [hack] transform table to dataStream to get StreamExecutionEnvironment
            if (tableEnvironment instanceof StreamTableEnvironment) {
                StreamTableEnvironment streamTableEnvironment = 
                  	(StreamTableEnvironment)tableEnvironment;
                streamEnv = streamTableEnvironment
                  	.toAppendStream(table, Row.class).getExecutionEnvironment();
            } else {
                throw new RuntimeException("Unsupported TableEnvironment, please use StreamTableEnvironment");
            }
          	
          	// Select the necessary columns according to "SelectedCols"
            Table inputTable = configureInputTable(table);
          	// Construct the output schema according on the "OutputCols" and "OutputTypes"
            TableSchema outputSchema = configureOutputSchema();
          	// Create a basic TFConfig according to "ClusterConfig" and "PythonConfig"
            TFConfig config = configureTFConfig();
          	// Configure the row encoding and decoding base on input & output schema
            configureExampleCoding(config, inputTable.getSchema(), outputSchema);
          	// transform the table by TF which implemented by AI-Extended
            Table outputTable = TFUtils
              	.inference(streamEnv, tableEnvironment, inputTable, config, outputSchema);
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
```

#### TFEstimator

通用的TFEstimator参数配置过程与TFModel类似，但需要同时配置Train过程和Inference过程的参数，因为TFEstimator需要返回一个实例化的TFModel，两者大部分参数应该是一样的，但无法要求用户一定要按这样的规范去开发TF算法，所以保留了给TFEstimator和TFModel独立配置一整套参数的能力。

```java
/**
 * A general TensorFlow estimator implemented by Flink-AI-Extended,
 * responsible for training and generating TensorFlow models.
 */
public class TFEstimator implements Estimator<TFEstimator, TFModel>, 
		HasClusterConfig<TFEstimator>,

    HasTrainPythonConfig<TFEstimator>, 
		HasInferencePythonConfig<TFEstimator>,

    HasTrainSelectedCols<TFEstimator>, 
		HasTrainOutputCols<TFEstimator>, 
		HasTrainOutputTypes<TFEstimator>,

    HasInferenceSelectedCols<TFEstimator>, 
		HasInferenceOutputCols<TFEstimator>, 
		HasInferenceOutputTypes<TFEstimator> {
      
    private Params params = new Params();

    @Override
    public TFModel fit(TableEnvironment tableEnvironment, Table table) {
        StreamExecutionEnvironment streamEnv;
        try {
          	// TODO: [hack] transform table to dataStream to get StreamExecutionEnvironment
            if (tableEnvironment instanceof StreamTableEnvironment) {
                StreamTableEnvironment streamTableEnvironment = 
                  	(StreamTableEnvironment)tableEnvironment;
                streamEnv = streamTableEnvironment
                  	.toAppendStream(table, Row.class).getExecutionEnvironment();
            } else {
                throw new RuntimeException("Unsupported TableEnvironment, please use StreamTableEnvironment");
            }
          	// Select the necessary columns according to "SelectedCols"
            Table inputTable = configureInputTable(table);
          	// Construct the output schema according on the "OutputCols" and "OutputTypes"
            TableSchema outputSchema = configureOutputSchema();
          	// Create a basic TFConfig according to "ClusterConfig" and "PythonConfig"
            TFConfig config = configureTFConfig();
          	// Configure the row encoding and decoding base on input & output schema
            configureExampleCoding(config, inputTable.getSchema(), outputSchema);
          	// transform the table by TF which implemented by AI-Extended
            Table outputTable = TFUtils.train(streamEnv, tableEnvironment, inputTable, config, outputSchema);
						// Construct the trained model by inference related config
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
```



### 2. Issues, scenarios & possible solutions

List：

\[Issue-1][Flink-AI-Extended] **Would it support multiple trains or inferences in the same job?**

\[Issue-2][Flink-AI-Extended] **Would it support BatchTableEnvironment for batch process application?**

\[Issue-3][Flink-AI-Extended] **Would it support Flink-1.9 cause Flink-1.8 doesn't have table-ml-api?**

\[Issue-4][Flink-AI-Extended] **What is the difference between train and inference?**

\[Issue-5][Flink-AI-Extended] **What's the strategy when batch size does not evenly divide the input dataset size?**

\[Issue-6]][Flink-AI-Extended]\[**Bug**] **The streaming inference result needs to wait until the next query to write to sink.**

\[Issue-7][Flink-AI-Extended]\[**Bug**] **Exception when there is only InputTfExampleConfig but no OutputTfExampleConfig.**

\[Issue-8][Flink ML Pipeline] **How to restart a estimator from a pre-trained model?**

\[Issue-9][Flink ML Pipeline] **How to use StreamEnvironment for stream machine learning?**



Details：

#### \[Issue-1][Flink-AI-Extended] Would it support multiple trains or inferences in the same job?

目前在同一个Flink job中只能进行一次TFUtils.train()或inference()调用，若调用多次会出现"am table"重复注册、"am server"被关闭等错误。

```java
PipelineStage s = stages.get(i);
Transformer t;
boolean needFit = isStageNeedFit(s);
if (needFit) {
		t = ((Estimator) s).fit(tEnv, input);
} else {
		// stage is Transformer, guaranteed in appendStage() method
		t = (Transformer) s;
}
transformStages.add(t);
input = t.transform(tEnv, input); // get input for next stage
```

这样的使用场景是存在的，比如说在Flink-1.9的[Pipeline](https://github.com/apache/flink/blob/release-1.9/flink-ml-parent/flink-ml-api/src/main/java/org/apache/flink/ml/api/core/Pipeline.java)中（如上），我将Flink-AI-Extended封装成Estimator和Model来引入到Pipeline框架里，当调用fit(TableEnvironment, Table)时，estimator会先用上一轮输入的table训练得到一个model，然后再用该model来transfrom输入的table传给下一轮stage。这里就需要在同一个job中先调用TFUtils.train()然后再调用inference。

因此，我想问一下之后是否能支持多次train/inference调用？现在版本无法支持的原因主要在哪些地方？如果我想添加这一功能的话，应该从哪里下手好？



#### \[Issue-2][Flink-AI-Extended] Would it support BatchTableEnvironment for batch process application?

```java
TFUtils.train(streamEnv, null, config);
```

现在TFUtils的api中均需传入一个StreamExecutionEnvironment（如上），但如果table是注册在BatchTableEnvironment的话就无法获得所需的StreamExecutionEnvironment。

基于Batch的机器学习算法是比较常见的，反而完全基于Stream的场景不多。大部分算法的开发者都会有一个相对固定的数据集，并在该数据集上训练和评估。虽然可以通过一些方法把Dataset转成Stream，但这样也会强行要求整个ExecutionEnvironment切换到Stream上，对于用户来说不是太友好。而且，Table是无关batch/stream的，在TFUtils的api中强行绑定一个StreamExecutionEnvironment也有点不太合理。

因此，我想问一下能否也在api层面上支持BatchTableEnvironment？或者设计成与Environment无关，只需Table即可？不知道这样在实现上能否行得通。



#### \[Issue-3][Flink-AI-Extended] Would it support Flink-1.9 cause Flink-1.8 doesn't have table-ml-api?

现在的Flink-AI-Extended是基于Flink-1.8.0的，社区在6月份发布了1.8.1，在8月份发布了1.9并提出了新的基于Table的ML api。

该项目之后是否也会兼容1.8.1和1.9？特别是在1.9上不少api（比如Table、StreamExecutionEnvironment）发生了改变。



#### \[Issue-4][Flink-AI-Extended] What is the difference between train and inference?

在TFUtils的api区分了train和inference，但两个接口在使用、参数、返回值上都基本一样，甚至在使用中随意替换也能正常运行，因此想问一下区分这两个api的原因是什么？

在我的想法里，Flink-AI-Extended只是扮演一个环境协调、任务调度的角色，而实际的运行模式应该下放到python管理并通过超参来配置。我认为可以用invoke(args…)替换掉现在的train/inference，这样在api层面上也更加的简洁，不知道在实现上有没有不合适的地方？



#### \[Issue-5][Flink-AI-Extended] What's the strategy when batch size does not evenly divide the input dataset size?

```python
dataset = context.flink_stream_dataset()
dataset = dataset.batch(3)
iterator = dataset.make_one_shot_iterator()
next_batch = iterator.get_next()
```

在python中获取从flink table输入的数据时，可以指定每次batch的大小（比如3）。但在批处理环境下，如果table是有界的，比如1000行，这时batch_size就不能整除dataset_size，导致最后一个batch一直在等待直到超时，程序也不能正常退出。在流处理环境下则一般不存在这个问题，因为把batch_size设成1也是合理的。

因此是否能添加一些策略来保证batch能正常获取，比如说：1.抛弃尾端无法达到一个batch大小的数据；2.用默认值补全成一个batch；3.设置超时机制，当超时后采用策略1或2，超时前继续等待。



#### \[Issue-6]][Flink-AI-Extended]\[Bug] The streaming inference result needs to wait until the next query to write to sink.

流处理环境下，从source读取的数据经过Flink-AI-Extended处理后，并没有立即写到sink上，而是要等到source的下一条数据到达后才写入。

```java
StreamExecutionEnvironment streamEnv = StreamExecutionEnvironment.createLocalEnvironment(1);
StreamTableEnvironment tableEnv = StreamTableEnvironment.create(streamEnv);

FlinkKafkaConsumer<Row> kafkaConsumer = createMessageConsumer(inputTopic, kafkaAddress, consumerGroup);
FlinkKafkaProducer<Row> kafkaProducer = createStringProducer(outputTopic, kafkaAddress);

Table input = tableEnv.fromDataStream(streamEnv
		.addSource(kafkaConsumer, "Kafaka Source")
		.setParallelism(1), "uuid,article,summary,reference");
TFModel model = createModel();
Table output = model.transform(tableEnv, input);
tableEnv.toAppendStream(output, Row.class).addSink(kafkaProducer).setParallelism(1);

streamEnv.execute();
```

我搭建了一个流处理end-to-end应用（如上），source是kafka，sink也是kafka，TFModel是对TFUtils.inference()的封装。我在source为空、sink也为空的状态下启动这个job，当我往source注入一条消息时，日志显示python进程已经接收到并执行了python里的"context.output_writer_op"，但此时sink所对应的topic并没有接收到任何消息。当我继续往source注入一条消息时，上一次的结果才写入到sink中。我尝试把sink换成控制台输出也是出现同样的情况。

但在批处理环境下（数据是有界且一次性全部注入），结果的数量与source是一致了，并没有缺少最后一条的情况。

请问一下这可能哪里的问题？之后我会写一个更直观简单的测试用例。



#### \[Issue-7][Flink-AI-Extended]\[Bug] Exception when there is only InputTfExampleConfig but no OutputTfExampleConfig.

如下，在配置ExampleCoding（即配置Flink与Python进行数据传输的格式）时，如果只配置encode部分而不配置decode部分会无法执行。具体需之后写测试用例再次验证一下。

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



#### \[Issue-8][Flink ML Pipeline] How to restart a estimator from a pre-trained model?

estimator是否可以从已经训练好的model或者另一个训练到一定程度estimator重新加载，然后接着继续训练？

我的想法中，这样的功能主要有两个应用场景：

1. 用户可能会使用别人预训练好的model对自己的数据集做fine-tuning，即estimator的参数并非自己随机初始化，而是从一个已有的model中加载。
2. 用户在训练一个复杂的模型时，训练的时间成本比较高，为了稳健起见可能希望在训练过程中有checkpoint机制，比如每10轮迭代保存一次，万一训练过程中意外中断了也可以从checkpoint中恢复并继续训练。

这些feature在大部分的深度学习框架中都是有的，传统机器学习算法一般没这样做，不知道这样的功能是否有必要呢？



#### \[Issue-9][Flink ML Pipeline] How to use pipeline for stream machine learning?

现在的Pipeline设计以及大部分算法都是基于批处理的，虽然API层面没有强行要求，但一旦尝试使用StreamExecutionEnvionment就会无法使用。

这样的场景有很多，比如说：

1. 一个注册在StreamTableEnvironment的table，要传入estimator进行fit时，假如数据是无界的，fit过程应该什么时候停止？怎么停止？
2. 在StreamExecutionEnvionment下，有一个包含estimator的pipeline需要训练，estimator需要根据数据训练得到一个实例化的model，然后使用该model来transform数据。但是，在streamEnv.execute()之前，所以的操作只是构建执行的DAG，没有真正的数据流动，那么这个需要数据训练出来的model该如何实例化呢？
3. 如果我们使用窗口（window）的方法把流处理环境转换成批处理，那么应该在哪里window？怎么window合适？

这些问题不好解决，主要很少有一个明确的基于流的机器学习标准或算法以供参考指引。

而且有个比较要命的是，Pipeline现在只能用于批处理场景，而Flink-AI-Extended的API必须传入一个StreamExecutionEnvionment。


