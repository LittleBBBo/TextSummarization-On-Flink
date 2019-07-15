from abc import ABCMeta, abstractmethod

import tensorflow as tf


class AbstractWriter(object):
    def build_graph(self):
        return

    def write_result(self, sess, abstract, reference):
        return

    def close(self, sess):
        return


class FlinkWriter(AbstractWriter):
    def __init__(self, tf_context):
        self._context = tf_context

    def build_graph(self):
        self._write_feed = tf.placeholder(dtype=tf.string)
        self.write_op, self._close_op = self._context.output_writer_op([self._write_feed])

    def write_result(self, sess, abstract, reference):
        example = tf.train.Example(features=tf.train.Features(
            feature={
                'abstract': tf.train.Feature(bytes_list=tf.train.BytesList(value=[abstract])),
                'reference': tf.train.Feature(bytes_list=tf.train.BytesList(value=[reference])),
            }
        ))
        sess.run(self.write_op, feed_dict={self._write_feed: example.SerializeToString()})

    def close(self, sess):
        sess.run(self._close_op)


class AbstractFlinkWriter(object):
    __metaclass__ = ABCMeta

    def __init__(self, context):
        """
        Initialize the writer
        :param context: TFContext
        """
        self._context = context
        self._build_graph()

    def _build_graph(self):
        self._write_feed = tf.placeholder(dtype=tf.string)
        self.write_op, self._close_op = self._context.output_writer_op([self._write_feed])

    @abstractmethod
    def _example(self, results):
        # TODO: Implement this function using context automatically
        """
        Encode the results tensor to `tf.train.Example`

        Examples:
        ```
        example = tf.train.Example(features=tf.train.Features(
            feature={
                'predict_label': tf.train.Feature(int64_list=tf.train.Int64List(value=[results[0]])),
                'label_org': tf.train.Feature(int64_list=tf.train.Int64List(value=[results[1]])),
                'abstract': tf.train.Feature(bytes_list=tf.train.BytesList(value=[results[2]])),
            }
        ))
        return example
        ```

        :param results: the result list of `Tensor` to write into Flink
        :return: An Example.
        """
        pass

    def write_result(self, sess, results):
        """
        Encode the results tensor and write to Flink.
        """
        sess.run(self.write_op, feed_dict={self._write_feed: self._example(results).SerializeToString()})

    def close(self, sess):
        """
        close writer
        :param sess: the session to execute operator
        """
        sess.run(self._close_op)
