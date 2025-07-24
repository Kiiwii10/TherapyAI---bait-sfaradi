# sessions//utils.py
from azure.ai.textanalytics import TextAnalyticsClient
from azure.core.credentials import AzureKeyCredential
import matplotlib.pyplot as plt
import numpy as np
from io import BytesIO
import base64
import os
from dotenv import load_dotenv
import matplotlib
matplotlib.use('Agg')  # Use non-GUI backend
import matplotlib.pyplot as plt
import config

from nltk.tokenize import sent_tokenize

load_dotenv()

text_analytics_client = TextAnalyticsClient(
    endpoint=config.TEXT_ANALYTICS_ENDPOINT,
    credential=AzureKeyCredential(config.TEXT_ANALYTICS_KEY)
)

def analyze_sentiment(sentences):
    results = []
    batch_size = 10
    for i in range(0, len(sentences), batch_size):
        batch = sentences[i:i + batch_size]
        response = text_analytics_client.analyze_sentiment(
        documents=[{"id": str(j), "language": config.TEXT_ANALYTICS_DEFAULT_LANGUAGE, "text": s} for j, s in enumerate(batch)]
    )
        results.extend([res.confidence_scores for res in response])
    return results

def create_sentiment_chart(sentences, scores):
    x = np.arange(len(sentences))
    pos = [s.positive for s in scores]
    neu = [s.neutral for s in scores]
    neg = [s.negative for s in scores]
    width = 0.3

    fig, ax = plt.subplots(figsize=(10, 5))
    ax.bar(x - width, pos, width, label='Positive', color='green')
    ax.bar(x, neu, width, label='Neutral', color='blue')
    ax.bar(x + width, neg, width, label='Negative', color='red')
    ax.set_xlabel('Sentence Index')
    ax.set_ylabel('Score')
    ax.set_title("Patient's Emotional Condition")
    ax.legend()

    buffer = BytesIO()
    fig.savefig(buffer, format='png')
    buffer.seek(0)
    return base64.b64encode(buffer.read()).decode()


