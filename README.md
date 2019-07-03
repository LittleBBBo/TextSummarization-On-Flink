## Alibaba 2019 Summer Intern Mission

### Target

Build an end-to-end application for abstractive document summarization on top of TensorFlow, Flink-AI-Extended and Flink ML pipeline framework.

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

[Flink-AI-Extended: Extend deep learning framework on the Flink project](https://github.com/alibaba/flink-ai-extended)

[Flink ML pipeline framework: A new set of ML core interface on top of Flink TableAPI](https://github.com/c4emmmm/flink/tree/flink-ml-export/flink-ml-parent)

