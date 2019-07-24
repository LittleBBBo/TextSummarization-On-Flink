# Copyright 2016 The TensorFlow Authors. All Rights Reserved.
# Modifications Copyright 2017 Abigail See
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================

"""This file contains some utility functions"""
import tensorflow as tf
import time
import os
FLAGS = tf.app.flags.FLAGS

def get_config():
    """Returns config for tf.session"""
    config = tf.ConfigProto(allow_soft_placement=True)
    config.gpu_options.allow_growth=True
    return config

def load_ckpt(saver, sess, ckpt_dir="train"):
    """Load checkpoint from the ckpt_dir (if unspecified, this is train dir) and restore it to saver and sess, waiting 10 secs in the case of failure. Also returns checkpoint name."""
    while True:
        try:
            latest_filename = "checkpoint_best" if ckpt_dir=="eval" else None
            ckpt_dir = os.path.join(FLAGS.log_root, ckpt_dir)
            ckpt_state = tf.train.get_checkpoint_state(ckpt_dir, latest_filename=latest_filename)
            tf.logging.info('Loading checkpoint %s', ckpt_state.model_checkpoint_path)
            saver.restore(sess, ckpt_state.model_checkpoint_path)
            return ckpt_state.model_checkpoint_path
        except:
            tf.logging.info("Failed to load checkpoint from %s. Sleeping for %i secs...", ckpt_dir, 10)
            time.sleep(10)


def bin2txt(data_path, finished_dir):
    import glob
    import json
    import struct
    import nltk
    import data
    from tensorflow.core.example import example_pb2
    from collections import OrderedDict

    def example_generator(file_path):
        with open(file_path, 'rb') as reader:
            while True:
                len_bytes = reader.read(8)
                if not len_bytes:
                    break  # finished reading this file
                str_len = struct.unpack('q', len_bytes)[0]
                example_str = struct.unpack('%ds' % str_len, reader.read(str_len))[0]
                yield example_pb2.Example.FromString(example_str)

    def text_generator(example_generator):
        while True:
            e = example_generator.next() # e is a tf.Example
            try:
                article_text = e.features.feature['article'].bytes_list.value[0]  # the article text was saved under the key 'article' in the data files
                abstract_text = e.features.feature['abstract'].bytes_list.value[0]  # the abstract text was saved under the key 'abstract' in the data files
            except ValueError:
                tf.logging.error('Failed to get article or abstract from example')
                continue
            if len(article_text)==0: # See https://github.com/abisee/pointer-generator/issues/1
                tf.logging.warning('Found an example with empty article text. Skipping it.')
            else:
                yield (article_text, abstract_text)
    counter = 0
    filelist = glob.glob(data_path)  # get the list of datafiles
    assert filelist, ('Error: Empty filelist at %s' % data_path)  # check filelist isn't empty
    filelist = sorted(filelist)
    for f in filelist:
        input_gen = text_generator(example_generator(f))
        with open(finished_dir + '/' + f.split('/')[-1].replace('.bin', '.txt'), 'w') as writer:
            while True:
                try:
                    (article, abstract) = input_gen.next()  # read the next example from file. article and abstract are both strings.
                    abstract_sentences = [sent.strip() for sent in data.abstract2sents(abstract)]  # Use the <s> and </s> tags in abstract to get a list of sentences.
                    abstract = ' '.join(abstract_sentences)
                    abstract_sentences = [' '.join(nltk.word_tokenize(sent)) for sent in nltk.sent_tokenize(abstract)]

                    json_format = json.dumps(OrderedDict([('uuid', 'uuid-%i' % counter), ('article', article), ('summary', ''), ('reference', abstract)]))
                    counter += 1
                    writer.write(json_format)
                    writer.write('\n')
                except StopIteration:  # if there are no more examples:
                    tf.logging.info("The example generator for this example queue filling thread has exhausted data.")
                    break
                except UnicodeDecodeError:
                    continue
        print "finished " + f

# data_path = '/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/chunked/train_*'
# finished_dir = '/Users/bodeng/TextSummarization-On-Flink/data/cnn-dailymail/finished_files/json'
# bin2txt(data_path, finished_dir)
