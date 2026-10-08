"""
export_bundle.py - Converts model_bundle.pkl to mobile assets (JSON + TFLite).
"""
import json
import pickle
import numpy as np
import tensorflow as tf
from cnn_scratch import CHAR_TO_IDX

def export():
    with open("model_bundle.pkl", "rb") as f:
        bundle = pickle.load(f)

    cnn, xgb, stacker = bundle["cnn"], bundle["xgb"], bundle["stacker"]

    # 1. Export XGBoost & Stacker to JSON
    trees_json = []
    def serialize_node(node):
        if node is None:
            return {"is_leaf": True, "value": 0.0}
        if node.is_leaf or node.feature_idx is None:
            val = float(node.leaf_value) if node.leaf_value is not None else 0.0
            return {"is_leaf": True, "value": val}
        return {
            "is_leaf": False,
            "feature_idx": int(node.feature_idx),
            "threshold": float(node.threshold),
            "left": serialize_node(node.left),
            "right": serialize_node(node.right),
        }

    for tree in xgb.trees:
        if hasattr(tree, "root"):
            trees_json.append(serialize_node(tree.root))

    meta_payload = {
        "xgb": {
            "base_score": float(xgb.base_score),
            "learning_rate": float(xgb.lr),
            "trees": trees_json,
        },
        "stacker": {
            "weights": [float(w) for w in stacker.w],
            "bias": float(stacker.b),
        },
        "vocab": {c: idx for c, idx in CHAR_TO_IDX.items()},
        "max_len": 128,
    }

    with open("xgboost_stacker.json", "w") as f:
        json.dump(meta_payload, f, indent=2)

    # 2. Re-instantiate CNN in Keras & Convert to TFLite
    inputs = tf.keras.Input(shape=(128,), dtype=tf.int32)
    x = tf.keras.layers.Embedding(
        input_dim=cnn.embed.W.shape[0],
        output_dim=cnn.embed.W.shape[1],
        weights=[cnn.embed.W],
    )(inputs)

    conv_layer = tf.keras.layers.Conv1D(
        filters=cnn.conv.out_ch, kernel_size=cnn.conv.k, padding="valid", activation="relu"
    )
    x = conv_layer(x)
    conv_layer.set_weights([cnn.conv.W, cnn.conv.b])

    x = tf.keras.layers.GlobalMaxPooling1D()(x)
    dense_layer = tf.keras.layers.Dense(units=cnn.dense.W.shape[1], activation="softmax")
    outputs = dense_layer(x)
    dense_layer.set_weights([cnn.dense.W, cnn.dense.b])

    tf_model = tf.keras.Model(inputs=inputs, outputs=outputs)
    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    tflite_model = converter.convert()

    with open("cnn_model.tflite", "wb") as f:
        f.write(tflite_model)

    print("Export complete: Generated 'cnn_model.tflite' and 'xgboost_stacker.json'.")

if __name__ == "__main__":
    export()