import os
import time

import tensorflow as tf
from tensorflow.python.training.summary_io import SummaryWriterCache
import numpy as np


FLAGS = tf.app.flags.FLAGS


class FlinkTrainer(object):
    def __init__(self, hps, model, batcher, sess_config, server_target):
        self._hps = hps
        self._model = model
        self._batcher = batcher

        train_dir = os.path.join(FLAGS.log_root, "train")
        if not os.path.exists(train_dir): os.makedirs(train_dir)

        self._model.build_graph()
        scaffold = tf.train.Scaffold(saver=tf.train.Saver(max_to_keep=3))
        hooks = None
        if hps.num_steps > 0:
            hooks = [tf.train.StopAtStepHook(num_steps=hps.num_steps)]

        self._sess = tf.train.MonitoredTrainingSession(master=server_target,
                                                       is_chief=True,
                                                       config=sess_config,
                                                       checkpoint_dir=train_dir,
                                                       save_checkpoint_secs=60,
                                                       save_summaries_secs=60,
                                                       scaffold=scaffold,
                                                       hooks=hooks)
        self._summary_writer = SummaryWriterCache.get(train_dir)
        tf.logging.info("Created session.")

    def stop(self):
        self._sess.close()

    def train(self):
        tf.logging.info("starting run_training")
        try:
            while not self._sess.should_stop():
                batch = self._batcher.next_batch()
                tf.logging.info('running training step...')
                t0 = time.time()
                results = self._model.run_train_step(self._sess, batch)
                t1 = time.time()
                tf.logging.info('seconds for training step: %.3f', t1 - t0)

                loss = results['loss']
                tf.logging.info('loss: %f', loss)  # print the loss to screen

                if not np.isfinite(loss):
                    raise Exception("Loss is not finite. Stopping.")

                if FLAGS.coverage:
                    coverage_loss = results['coverage_loss']
                    tf.logging.info("coverage_loss: %f", coverage_loss)  # print the coverage loss to screen

                # get the summaries and iteration number so we can write summaries to tensorboard
                summaries = results['summaries']  # we will write these summaries to tensorboard using summary_writer
                train_step = results['global_step']  # we need this to update our running average loss

                self._summary_writer.add_summary(summaries, train_step)  # write the summaries
                # if train_step % 100 == 0:  # flush the summary writer every so often
                #     self._summary_writer.flush()
        except KeyboardInterrupt:
            tf.logging.info("Caught keyboard interrupt on worker. Stopping supervisor...")
            # self.stop()
        finally:
            self.stop()
