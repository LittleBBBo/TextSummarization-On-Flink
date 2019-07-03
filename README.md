## 2019 Summer Intern Mission

### Goal:

Build an end-to-end application for abstractive document summarization on top of TensorFlow, Flink-AI-Extended [1] and Flink ML pipeline framework [2].

### Introduction

Document summarization is the process of shortening a text document in order to create a summary with the major points of the original document.

In general, a native Flink application will be built to serve an external document summarization application. In the training phase, a corpus with plenty of article-summary tuple will be fed as input to an estimator pipeline for training to produce a model pipeline. In the reference phase, the Flink application will use the trained model pipeline to serve the summarization request from external application, which take raw article as input and response the summary.

Inside the estimator pipeline, an abstract TF estimator will be created on top of Flink ML pipeline framework. The TF estimator is actually an untrained tensorflow model running in python, which use Flink-AI-Extended to connect to tensorflow. After fitting the corpus(or training), the estimator pipeline is converted to the model pipeline. Similarly, a abstract TF Model will be created inside the model pipeline, which actually use trained model on tensorflow to execute transform function.

The design of the entire system is shown below:

![design](doc/design.pdf)

### Schedule (JUL 1st - AUG 2nd)

​	**Week1**: Build an abstractive document summarization application on top of pure TensorFlow. Implements the ability to train models from the original corpus and generate summaries for new articles.

​	**Week2**: Encapsulate and call model’s training & inference function through Flink-AI-Extended

​	**Week3**: Integrate into the ML pipeline framework and train/inference/persist the model through pipeline

​	**Week4**: Build a simple WebUI, summarize & demo, make suggestions and improvements

​	**Week5**: buffer period

### Expected contributions

1. Build a complete application, connect TensorFlow <—> Flink-AI-Extended <—> Flink ML pipeline framework. Lessons learned from the perspective of MLlib users and developers.
2. Found the disadvantages of Flink-AI-Extended and ML pipeline in use, design and implementation
3. Suggestions and practical improvements for the disadvantages

### References

\[1] [Flink-AI-Extended: Extend deep learning framework on the Flink project](https://github.com/alibaba/flink-ai-extended)

\[2] [Flink ML pipeline framework: A new set of ML core interface on top of Flink TableAPI](https://github.com/c4emmmm/flink/tree/flink-ml-export/flink-ml-parent)