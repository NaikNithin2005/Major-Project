import ai

def test_ai_module_imports():
    assert hasattr(ai, "__version__")
    assert ai.__version__ == "0.1.0"

def test_ai_subpackages():
    import ai.preprocessing
    import ai.feature_engineering
    import ai.tinybert
    import ai.xgboost
    import ai.anomaly
    import ai.explainable_ai
    import ai.training
    import ai.evaluation
    import ai.deployment
    assert True
