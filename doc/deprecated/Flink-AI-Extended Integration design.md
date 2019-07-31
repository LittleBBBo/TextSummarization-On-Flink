## Flink-AI-Extended Integration design

### TFEstimator:

```java
/**
* A general TensorFlow Estimator who need general TFConfig,
* the fit() function is actually executed by Flink-AI-Extended
*/
public interface TFEstimator<E extends TFEstimator<E, M>, M extends TFModel<M>> 
implements WithTFConfigParams<E> extends Estimator<E, M> {
}
```

### TFModel:

```java
/**
* A general TensorFlow Model who need general TFConfig,
* the transform() function is actually executed by Flink-AI-Extended
*/
public interface TFModel<M extends TFModel<M>> 
implements WithTFConfigParams<M> extends Model<M> {
}
```

### WithTFConfigParams:

```java
/**
* Calling python scripts via Flink-AI-Extended generally requires the following parameters：
* 1. Zookeeper address: String
* 2. Python scripts path: String[], the first file will be the entry
* 3. Worker number: Int
* 4. Ps number: Int
* 5. Map function: String, the function in entry file to be called
* 6. Virtual environment path: String, could be null
*/
public interface WithTFConfigParams<T extends WithTFConfigParams<T>> extends WithZookeeperAddressParams<T>,
WithPythonScriptsParams<T>,
WithWorkerNumParams<T>,
WithPsNumParams<T>,
WithMapFunctionParams<T>,
WithEnvironmentPathParams<T> {
  
}
```

### TFSummaryEstimator:

```java
/**
* A specific document summarization estimator,
* the training process needs to specify the input article column 
* and the input abstract column,
* in addition to the hyperparameter for TensorFlow model
*/
public class TFSummaryEstimator implements TFEstimator<TFSummaryEstimator, TFSummaryModel>,
WithInputArticleCol<TFSummaryEstimator>,
WithInputAbstractCol<TFSummaryEstimator>,
WithHyperParams<TFSummaryEstimator> {
  	private Params params = new Params();
  	
  	@Override
  	public TFSummaryModel fit(TableEnviroment tEnv, Table input) {
      	//TODO: call training process through Flink-AI-Extended
    }
  	
  	@Override
		public Params getParams() {
			return params;
		}
}
```

### TFSummaryModel:

```java
/**
* A specific document summarization estimator,
* the inference process needs to specify the input article column 
* and the output abstract column,
* in addition to the model path and hyperparameter for TensorFlow model
*/
public class TFSummaryModel implements TFModel<TFSummaryModel>,
WithInputArticleCol<TFSummaryEstimator>,
WithOutputAbstractCol<TFSummaryEstimator>,
WithModelPathParams<TFSummaryEstimator>,
WithHyperParams<TFSummaryEstimator> {
  	private Params params = new Params();
  	
  	@Override
		public Table transform(TableEnvironment tEnv, Table input) {
				//TODO: call inference process through Flink-AI-Extended
		}

		@Override
		public Params getParams() {
			return params;
		}
}
```



