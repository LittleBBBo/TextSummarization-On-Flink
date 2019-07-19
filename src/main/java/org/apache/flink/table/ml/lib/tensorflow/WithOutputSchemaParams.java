//package org.apache.flink.table.ml.lib.tensorflow;
//
//import org.apache.flink.ml.api.misc.param.ParamInfo;
//import org.apache.flink.ml.api.misc.param.ParamInfoFactory;
//import org.apache.flink.ml.api.misc.param.WithParams;
//import org.apache.flink.table.api.TableSchema;
//
//public interface WithOutputSchemaParams<T extends WithOutputSchemaParams<T>> extends WithParams<T> {
//    ParamInfo<TableSchema> OUTPUT_SCHEMA = ParamInfoFactory
//            .createParamInfo("output_schema", TableSchema.class)
//            .setDescription("the schema of output table from estimator or model")
//            .setRequired().build();
//
//    default TableSchema getOutputSchema() {
//        return get(OUTPUT_SCHEMA);
//    }
//
//    default T setOutputSchema(TableSchema outputSchema) {
//        return set(OUTPUT_SCHEMA, outputSchema);
//    }
//}
