import com.alibaba.flink.ml.operator.util.DataTypes;
import com.alibaba.flink.ml.tensorflow.client.TFConfig;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCoding;
import com.alibaba.flink.ml.tensorflow.coding.ExampleCodingConfig;
import com.alibaba.flink.ml.tensorflow.util.TFConstants;
import com.alibaba.flink.ml.util.MLConstants;
import org.apache.flink.api.common.typeinfo.BasicArrayTypeInfo;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.api.TableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Utils {
    private static Logger LOG = LoggerFactory.getLogger(Utils.class);

    /***
     *
     * @param typeInformation
     * @return
     * @throws RuntimeException
     */
    public static DataTypes typeInformationToDataTypes(TypeInformation typeInformation) throws RuntimeException {
        if (typeInformation == BasicTypeInfo.STRING_TYPE_INFO) {
            return DataTypes.STRING;
        } else if (typeInformation == BasicTypeInfo.BOOLEAN_TYPE_INFO) {
            return DataTypes.BOOL;
        } else if (typeInformation == BasicTypeInfo.BYTE_TYPE_INFO) {
            return DataTypes.INT_8;
        } else if (typeInformation == BasicTypeInfo.SHORT_TYPE_INFO) {
            return DataTypes.INT_16;
        } else if (typeInformation == BasicTypeInfo.INT_TYPE_INFO) {
            return DataTypes.INT_32;
        } else if (typeInformation == BasicTypeInfo.LONG_TYPE_INFO) {
            return DataTypes.INT_64;
        } else if (typeInformation == BasicTypeInfo.FLOAT_TYPE_INFO) {
            return DataTypes.FLOAT_32;
        } else if (typeInformation == BasicTypeInfo.DOUBLE_TYPE_INFO) {
            return DataTypes.FLOAT_64;
        } else if (typeInformation == BasicTypeInfo.CHAR_TYPE_INFO) {
            return DataTypes.UINT_16;
        } else if (typeInformation == BasicTypeInfo.DATE_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicTypeInfo.VOID_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicTypeInfo.BIG_INT_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicTypeInfo.BIG_DEC_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicTypeInfo.INSTANT_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.STRING_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.BOOLEAN_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.BYTE_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.SHORT_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.INT_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.LONG_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.FLOAT_ARRAY_TYPE_INFO) {
            return DataTypes.FLOAT_32_ARRAY;
        } else if (typeInformation == BasicArrayTypeInfo.DOUBLE_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else if (typeInformation == BasicArrayTypeInfo.CHAR_ARRAY_TYPE_INFO) {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        } else {
            throw new RuntimeException("Unsupported data type of " + typeInformation.toString());
        }
    }

    public static void configureExampleCoding(TFConfig config, String[] encodeNames, TypeInformation[] encodeTypes,
                                               String[] decodeNames, TypeInformation[] decodeTypes,
                                               ExampleCodingConfig.ObjectType entryType, Class entryClass) throws RuntimeException {
        DataTypes[] encodeDataTypes = Arrays
                .stream(encodeTypes)
                .map(Utils::typeInformationToDataTypes)
                .toArray(DataTypes[]::new);
        String strInput = ExampleCodingConfig.createExampleConfigStr(encodeNames, encodeDataTypes, entryType, entryClass);
        config.getProperties().put(TFConstants.INPUT_TF_EXAMPLE_CONFIG, strInput);
        LOG.info("input if example config: " + strInput);

        DataTypes[] decodeDataTypes = Arrays
                .stream(decodeTypes)
                .map(Utils::typeInformationToDataTypes)
                .toArray(DataTypes[]::new);
        String strOutput = ExampleCodingConfig.createExampleConfigStr(decodeNames, decodeDataTypes, entryType, entryClass);
        config.getProperties().put(TFConstants.OUTPUT_TF_EXAMPLE_CONFIG, strOutput);
        LOG.info("output if example config: " + strOutput);

        config.getProperties().put(MLConstants.ENCODING_CLASS, ExampleCoding.class.getCanonicalName());
        config.getProperties().put(MLConstants.DECODING_CLASS, ExampleCoding.class.getCanonicalName());
    }

    public static void configureExampleCoding(TFConfig config, TableSchema encodeSchema, TableSchema decodeSchema,
                                               ExampleCodingConfig.ObjectType entryType, Class entryClass) throws RuntimeException {
        configureExampleCoding(config, encodeSchema.getFieldNames(), encodeSchema.getFieldTypes(),
                decodeSchema.getFieldNames(), decodeSchema.getFieldTypes(),
                entryType, entryClass);
    }
}
