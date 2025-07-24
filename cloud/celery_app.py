# celery_app.py
from celery import Celery
from flask import Flask
import ssl
import config


import logging
logger = logging.getLogger(__name__)
logger.info(f"Celery App importing config. CELERY_BROKER_URL: {config.CELERY_BROKER_URL}")

celery = Celery(
    __name__,
    broker=config.CELERY_BROKER_URL,
    backend=config.CELERY_RESULT_BACKEND_URL,
    include=['sessions.tasks']  
)

celery.conf.update(
    task_serializer=config.CELERY_TASK_SERIALIZER,
    result_serializer=config.CELERY_RESULT_SERIALIZER,
    accept_content=config.CELERY_ACCEPT_CONTENT,
    timezone=config.CELERY_TIMEZONE,
    enable_utc=config.CELERY_ENABLE_UTC,
    broker_connection_retry=True, # Default is True
    broker_connection_max_retries=10, # Default is 100, maybe reduce for faster feedback if it's a persistent issue
    broker_connection_retry_on_startup=True, # Good for robust startup
    task_track_started=config.CELERY_TASK_TRACK_STARTED,
)

if config.REDIS_USE_SSL:
    celery.conf.update(
        broker_transport_options={
            'ssl_cert_reqs': ssl.CERT_REQUIRED
        },
        result_backend_transport_options={
            'ssl_cert_reqs': ssl.CERT_REQUIRED
        }
    )

def init_celery(app: Flask):
    class ContextTask(celery.Task):
        def __call__(self, *args, **kwargs):
            with app.app_context():
                return self.run(*args, **kwargs)

    celery.Task = ContextTask
    return celery