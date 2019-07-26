## Flink-AI-Extended Integration Report

在7.15-7.26这段时间内，我基于Flink ML pipeline框架实现了两个通用的class：**TFEstimator**和**TFModel**，这两个class分别在**fit()**和**transform()**方法里封装了Flink-AI-Extended的**train**和**inference**过程，通过**WithParams**接口实现通用参数的配置和传递。

截至7.26，独立的TFEstimator训练并返回TFModel、独立的TFModel引用均已完成功能上的实现与测试，并通过两个Kafka topic作为source和sink构建出一个简单的命令行end-to-end应用。

但是，将TFEstimator作为一个**PipelineStage**加入到Pipeline并进行训练时，发现**Blocker**，原因是pipeline.fit()方法需要先调用TFEstimator.fit()产生一个TFModel，再用该TFModel来transform输入的table传给下一个PipelineStage，相当于在同一个job里调用Flink-AI-Extended的**train**和**inference**过程，这在目前是不支持的，详见Issue。

本文将详细介绍在Flink-AI-Extended到Flink ML pipeline框架的整合过程中 ：

1. 设计实现上的细节；
2. 遇到的问题、发生的场景、可能的解决方案；
3. 后续改进的建议；

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

#### TFEstimator

### 2. Issues, scenarios & possible solutions

**\[Issue-1][Flink-AI-Extended] Support multiple trains or inferences in the same job.**

**\[Issue-2][Flink-AI-Extended] Support BatchEnvironment for batch process application.**

**\[Issue-3][Flink-AI-Extended] What is the necessary difference between train and inference.**

**\[Issue-4][Flink-AI-Extended] Add strategy when batch size does not evenly divide the input dataset size.**

**\[Issue-5][Flink-AI-Extended] The streaming inference result needs to wait until the next query to write to sink.**

**\[Issue-6][Flink-AI-Extended] Exception when there is only InputTfExampleConfig but no OutputTfExampleConfig.**

**\[Issue-7][Flink ML Pipeline] Restart a estimator from a pre-trained model.**

**\[Issue-8][Flink ML Pipeline] Support StreamEnvironment for stream machine learning.**

### 3. Suggestions for improvements

