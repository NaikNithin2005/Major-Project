"""
ML_Models/common/visualization.py
Reusable, publication-grade plotting utilities for confusion matrices, metric comparisons, and summaries.
"""

import os
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")  # Non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns

# Set overall seaborn aesthetic
sns.set_theme(style="whitegrid", font="sans-serif")
plt.rcParams.update({
    "figure.autolayout": True,
    "font.size": 11,
    "axes.titlesize": 13,
    "axes.labelsize": 11,
    "xtick.labelsize": 10,
    "ytick.labelsize": 10,
    "figure.titlesize": 14,
    "savefig.dpi": 300,
})

PALETTE = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]


def plot_confusion_matrix(cm, class_names, title, output_path):
    """Generates and saves a styled confusion matrix heatmap."""
    cm = np.array(cm)
    fig, ax = plt.subplots(figsize=(6, 5))
    
    # Calculate percentages for cell annotation
    cm_sum = np.sum(cm, axis=1, keepdims=True)
    cm_perc = np.divide(cm, cm_sum, out=np.zeros_like(cm, dtype=float), where=cm_sum != 0) * 100
    
    annot = np.empty_like(cm).astype(str)
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            annot[i, j] = f"{cm[i, j]:,}\n({cm_perc[i, j]:.1f}%)"

    sns.heatmap(
        cm,
        annot=annot,
        fmt="",
        cmap="Blues",
        cbar=True,
        xticklabels=class_names,
        yticklabels=class_names,
        ax=ax,
        linewidths=1,
        linecolor="#e0e0e0"
    )
    ax.set_title(title, pad=15, fontweight="bold")
    ax.set_xlabel("Predicted Class", labelpad=10, fontweight="semibold")
    ax.set_ylabel("True Class", labelpad=10, fontweight="semibold")
    plt.xticks(rotation=25, ha="right")
    plt.yticks(rotation=0)

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_metric_bar_chart(models, values, metric_name, title, output_path):
    """Generates an individual metric bar chart comparing multiple models."""
    fig, ax = plt.subplots(figsize=(7, 5))
    
    colors = ["#2b5c8f", "#e67e22", "#27ae60"][:len(models)]
    bars = ax.bar(models, values, color=colors, width=0.55, edgecolor="#2c3e50", linewidth=1.2)
    
    ax.set_ylabel(f"{metric_name} Score", labelpad=10, fontweight="semibold")
    ax.set_title(title, pad=15, fontweight="bold")
    ax.set_ylim(0, 1.12)
    
    for bar in bars:
        height = bar.get_height()
        ax.annotate(
            f"{height * 100:.2f}%",
            xy=(bar.get_x() + bar.get_width() / 2, height),
            xytext=(0, 5),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontweight="bold",
            fontsize=11
        )
    
    ax.grid(axis="y", linestyle="--", alpha=0.6)
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_grouped_metrics(models, metrics_data, title, output_path):
    """
    Generates a grouped bar chart comparing Accuracy, Precision, Recall, and F1 across models.
    metrics_data format: dict of {model_name: {'accuracy': val, 'precision': val, 'recall': val, 'f1': val}}
    """
    metric_labels = ["Accuracy", "Precision", "Recall", "F1 Score"]
    metric_keys = ["accuracy", "precision", "recall", "f1"]
    
    n_metrics = len(metric_labels)
    n_models = len(models)
    
    x = np.arange(n_metrics)
    width = 0.8 / n_models
    
    fig, ax = plt.subplots(figsize=(9, 5.5))
    colors = ["#3498db", "#e67e22", "#2ecc71"]
    
    for idx, model in enumerate(models):
        m_vals = [metrics_data[model][k] for k in metric_keys]
        pos = x - 0.4 + (idx + 0.5) * width
        bars = ax.bar(pos, m_vals, width, label=model, color=colors[idx % len(colors)], edgecolor="#333333", linewidth=1.0)
        
        for bar in bars:
            height = bar.get_height()
            ax.annotate(
                f"{height * 100:.1f}%",
                xy=(bar.get_x() + bar.get_width() / 2, height),
                xytext=(0, 4),
                textcoords="offset points",
                ha="center",
                va="bottom",
                fontsize=9,
                fontweight="bold"
            )
            
    ax.set_ylabel("Score", labelpad=10, fontweight="semibold")
    ax.set_title(title, pad=15, fontweight="bold")
    ax.set_xticks(x)
    ax.set_xticklabels(metric_labels, fontweight="semibold")
    ax.set_ylim(0, 1.15)
    ax.legend(loc="lower right", frameon=True, framealpha=0.9)
    ax.grid(axis="y", linestyle="--", alpha=0.5)

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_combined_confusion_matrices(matrices_dict, output_path):
    """
    Displays individual confusion matrices for all three models in a unified 3-panel figure.
    matrices_dict: {model_name: (cm_array, class_labels, task_subtitle)}
    """
    n = len(matrices_dict)
    fig, axes = plt.subplots(1, n, figsize=(5.5 * n, 5.2))
    if n == 1:
        axes = [axes]
        
    cmaps = ["Blues", "Oranges", "Greens"]
    
    for idx, (model_name, (cm, labels, task_desc)) in enumerate(matrices_dict.items()):
        ax = axes[idx]
        cm = np.array(cm)
        
        cm_sum = np.sum(cm, axis=1, keepdims=True)
        cm_perc = np.divide(cm, cm_sum, out=np.zeros_like(cm, dtype=float), where=cm_sum != 0) * 100
        
        annot = np.empty_like(cm).astype(str)
        for i in range(cm.shape[0]):
            for j in range(cm.shape[1]):
                annot[i, j] = f"{cm[i, j]:,}\n({cm_perc[i, j]:.1f}%)"

        sns.heatmap(
            cm,
            annot=annot,
            fmt="",
            cmap=cmaps[idx % len(cmaps)],
            cbar=False,
            xticklabels=labels,
            yticklabels=labels,
            ax=ax,
            linewidths=1,
            linecolor="#e0e0e0"
        )
        ax.set_title(f"{model_name}\n({task_desc})", pad=12, fontweight="bold", fontsize=11)
        ax.set_xlabel("Predicted", labelpad=8, fontweight="semibold")
        ax.set_ylabel("True", labelpad=8, fontweight="semibold")
        ax.tick_params(axis="x", rotation=30)
        ax.tick_params(axis="y", rotation=0)

    fig.suptitle("Squish Multi-Model Confusion Matrix Comparison", fontsize=13, fontweight="bold", y=1.02)
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)

