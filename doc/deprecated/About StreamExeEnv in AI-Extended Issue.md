## About StreamExeEnv in AI-Extended Issue

我在把Flink-AI-Extended兼容整合进Pipeline框架时存在缺少**StreamExecutionEnvironment**的问题。

现版本ML pipeline框架的接口定义如下，fit()与transform()方法均只接收input table所绑定的**TableEnv**，该**TableEnv**是由上层**ExecutionEnvironment**创建的（或**StreamExeEnv**或**BatchExeEnv**）。但问题是现在的Flink-AI-Extended里所有的train或inference方法都是基于**StreamExeEnv**，需同时传入**StreamExeEnv**和**TableEnv**，而**StreamExeEnv**在ML pipeline框架下是不一定存在的，这就会导致无法兼容现有的框架。

```java
public interface Transformer<T extends Transformer<T>> extends PipelineStage<T> {
		Table transform(TableEnvironment tEnv, Table input);
}

public interface Model<M extends Model<M>> extends Transformer<M> {
}

public interface Estimator<E extends Estimator<E, M>, M extends Model<M>> 
		extends PipelineStage<E> {
		M fit(TableEnvironment tEnv, Table input);
}
```

我查了一下**StreamExeEnv**在Flink-AI-Extended里的引用，如下所示，主要用在两个地方：

1. 注册python缓存文件。

```java
package com.alibaba.flink.ml.operator.util;
public class PythonFileUtil {
  	//...
    private static List<String> registerPythonLibFiles(
      StreamExecutionEnvironment env, 
      String... userPyLibs) throws IOException {
        Tuple2<Map<String, URI>, List<String>> tuple2 = convertFiles(userPyLibs);
        Map<String, URI> files = (Map)tuple2.f0;
        files.forEach((name, uri) -> {
            env.registerCachedFile(uri.toString(), name);
        });
        return (List)tuple2.f1;
    }
  	//...
}
```

2. 为不同角色的job添加source。

```java
package com.alibaba.flink.ml.operator.client;
public class RoleUtils {
  	//...
		public static <IN, OUT> DataStream<OUT> addRole(
      StreamExecutionEnvironment streamEnv, 
      ExecutionMode mode, 
      DataStream<IN> input, 
      MLConfig mlConfig, 
      TypeInformation<OUT> outTI, 
      BaseRole role) {
        if (null != input) {
            mlConfig.addProperty("job_has_input", "true");
        }

        TypeInformation workerTI = outTI == null ? DUMMY_TI : outTI;
        DataStream worker = null;
        int workerParallelism = (Integer)mlConfig.getRoleParallelismMap().get(role.name());
        if (input == null) {
            worker = streamEnv
              .addSource(NodeSource.createSource(mode, role, mlConfig, workerTI))
              .setParallelism(workerParallelism)
              .name(role.name());
        } else {
            FlatMapFunction flatMapper = new MLFlatMapOp(mode, role, mlConfig, 
                                                         input.getType(), workerTI);
            worker = input
              .flatMap(flatMapper)
              .setParallelism(workerParallelism)
              .name(role.name());
        }

        if (outTI == null && worker != null) {
            worker
              .addSink(new DummySink())
              .setParallelism(workerParallelism)
              .name("Dummy sink");
        }

        return worker;
    }
  	//...
}
```

我对ExecutionEnvironment不太熟悉，不知道这两部分的设置是否有其他方式实现？或者是否有其他方法能解决现在无法兼容的问题。特意请教一下！