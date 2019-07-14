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

