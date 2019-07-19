package org.apache.flink.table.ml.lib.tensorflow.param;

import org.apache.flink.ml.api.misc.param.ParamInfo;
import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
import org.apache.flink.ml.api.misc.param.WithParams;

public interface HasClusterConfig<T> extends WithParams<T> {
    ParamInfo<String> ZOOKEEPER_CONNECT_STR = ParamInfoFactory
            .createParamInfo("zookeeper_connect_str", String.class)
            .setDescription("zookeeper address to connect")
            .setRequired()
            .setHasDefaultValue("127.0.0.1:2181").build();

    ParamInfo<Integer> WORKER_NUM = ParamInfoFactory
            .createParamInfo("worker_num", Integer.class)
            .setDescription("worker number")
            .setRequired()
            .setHasDefaultValue(1).build();
    ParamInfo<Integer> PS_NUM = ParamInfoFactory
            .createParamInfo("ps_num", Integer.class)
            .setDescription("ps number")
            .setRequired()
            .setHasDefaultValue(0).build();

    default String getZookeeperConnStr() {
        return get(ZOOKEEPER_CONNECT_STR);
    }

    default T setZookeeperConnStr(String zookeeperConnStr) {
        return set(ZOOKEEPER_CONNECT_STR, zookeeperConnStr);
    }

    default int getWorkerNum() {
        return get(WORKER_NUM);
    }

    default T setWorkerNum(int workerNum) {
        return set(WORKER_NUM, workerNum);
    }

    default int getPsNum() {
        return get(PS_NUM);
    }

    default T setPsNum(int psNum) {
        return set(PS_NUM, psNum);
    }
}
