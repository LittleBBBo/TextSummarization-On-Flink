## [[Issue](https://github.com/alibaba/flink-ai-extended/issues/17)] Simplify the ExampleCoding configuration process

Now the process of configuring ExampleCoding is cumbersome. As a common configuration, I think we can add some tool interfaces to help users simply configure.
The following is the general configuration process under the current version:

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

It can be seen that the user needs to know the column names, types, various constants, etc. of the field when configuring example coding. In fact, it can be encapsulated, and the user only needs to provide the input and output table schema to complete the configuration. For example:

```java
ExampleCodingConfigUtil.configureExampleCoding(tfConfig, inputSchema, outputSchema, 
                                               ExampleCodingConfig.ObjectType.ROW, Row.class);
```

In the current version, the data type that TF can accept is defined in **DataTypes** in Flink-AI-Extended project, and the data type of Flink Table field is defined in **TypeInformation** in Flink project and some basic types such as *BasicTypeInfo* and *BasicArrayTypeInfo* are implemented. But the problem is that the basic types of **DataTypes** and **TypeInformation** are not one-to-one correspondence.

Therefore, if we want to encapsulate the ExampleCoding configuration process, we need to solve the problem that DataTypes is not compatible with TypeInformation. There are two options:

1. Provide a method for converting DataTypes and TypeInformation. Although most of the commonly used types can be matched, they are not completely one-to-one correspondence, so there are some problems that cannot be converted.
2. Discard DataTypes and use TypeInformation directly in Flink-AI-Extended. DataTypes is just a simple enumeration type that only participates in the identification of data types. TypeInformation can also achieve the same functionality.

Solution 1 is relatively simple to implement and easy to be compatible, but solution 2 is better in the long run.



