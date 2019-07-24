package org.apache.flink.table.ml.lib.tensorflow.app;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSerializationSchema implements SerializationSchema<Row> {
    private ObjectMapper objectMapper;
    private Logger logger = LoggerFactory.getLogger(MessageSerializationSchema.class);

    @Override
    public byte[] serialize(Row row) {
        if(objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        try {
            Message message = new Message((String)row.getField(0), (String)row.getField(1),
                    (String)row.getField(2), (String)row.getField(3));
            return objectMapper.writeValueAsString(message).getBytes();
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON", e);
        }
        return new byte[0];
    }
}
