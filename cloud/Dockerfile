# Use an official Python runtime as a parent image
FROM python:3.9-slim

# Set environment variables for Python
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PORT=8000

# --- Create a non-root user ---
ARG APP_USER=appuser
ARG APP_UID=1000
ARG APP_GID=1000
RUN groupadd -g ${APP_GID} ${APP_USER} && \
    useradd -u ${APP_UID} -g ${APP_GID} -m -s /bin/bash ${APP_USER}

# Set the working directory in the container
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    ca-certificates \
    supervisor \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy the requirements file
COPY --chown=${APP_USER}:${APP_USER} requirements.txt .

# Install Python packages
RUN pip install --no-cache-dir -r requirements.txt

# Copy the supervisor configuration file
COPY --chown=${APP_USER}:${APP_USER} supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Copy the rest of the application code
COPY --chown=${APP_USER}:${APP_USER} . .


# # Create directories and set ownership
# RUN mkdir -p /app/secrets && chown -R ${APP_USER}:${APP_USER} /app/secrets
# Ensure the /app directory itself is owned by appuser
RUN chown -R ${APP_USER}:${APP_USER} /app

# Switch to the non-root user
USER ${APP_USER}

# Expose the port Gunicorn will run on (will be overridden by ENV PORT if set by Azure)
EXPOSE 8000

# Start supervisor
CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
