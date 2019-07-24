package org.apache.flink.table.ml.lib.tensorflow.app;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.flink.types.Row;


import java.io.Serializable;

@JsonSerialize
public class Message implements Serializable {
    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("article")
    private String article;
    @JsonProperty("summary")
    private String summary;
    @JsonProperty("reference")
    private String reference;

    public Message() {
    }

    public Message(String uuid, String article, String summary, String reference) {
        this.uuid = uuid;
        this.article = article;
        this.summary = summary;
        this.reference = reference;
    }

    public String getUuid() {
        return uuid;
    }

    public String getArticle() {
        return article;
    }

    public String getSummary() {
        return summary;
    }

    public String getReference() {
        return reference;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setArticle(String article) {
        this.article = article;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public static Row toRow(Message message) {
        Row row = new Row(4);
        row.setField(0, message.uuid);
        row.setField(1, message.article);
        row.setField(2, message.summary);
        row.setField(3, message.reference);
        return row;
    }
}
