package org.apache.flink.table.ml.lib.tensorflow.app;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageDeserializationSchema implements DeserializationSchema<Row> {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private AtomicInteger counter = new AtomicInteger();
    private Integer maxCount;

    public MessageDeserializationSchema(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public Row deserialize(byte[] bytes) throws IOException {
        Message message = objectMapper.readValue(bytes, Message.class);
        Row row = new Row(4);
        row.setField(0, message.getUuid());
        row.setField(1, message.getArticle());
        row.setField(2, message.getSummary());
        row.setField(3, message.getReference());
        counter.incrementAndGet();
        return row;
    }

    @Override
    public boolean isEndOfStream(Row row) {
        if (counter.get() > maxCount) {
            return true;
        }
        return false;
    }

    @Override
    public TypeInformation<Row> getProducedType() {
        return new RowTypeInfo(Types.STRING, Types.STRING, Types.STRING,Types.STRING);
    }
}
