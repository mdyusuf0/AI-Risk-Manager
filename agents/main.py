"""
Sentinel Ring — Python Agents (FastAPI)

This is the MAIN entry point for all Python agents. It's like your
SentinelApplication.java but for the Python side.

JAVA PARALLEL:
  In Spring Boot, you have ONE application with multiple @RestControllers.
  In FastAPI, you have ONE app with multiple "routers" (same concept).

  Spring Boot:
    @SpringBootApplication → starts Tomcat on port 8080
    @RestController classes are auto-discovered via component scanning

  FastAPI:
    FastAPI() → creates the app
    app.include_router(scoring_router) → manually registers each router
    uvicorn runs it on port 8000

HOW TO RUN:
  cd agents/
  pip install -r requirements.txt
  uvicorn main:app --reload --port 8000

  --reload means "restart the server when code changes" (like Spring DevTools)
  main:app means "look in main.py for the variable called app"
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI
from scoring.router import router as scoring_router
import logging

# ── Configure logging ─────────────────────────────────────────────────────
# This is like adding logging config to application.properties in Spring Boot.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s — %(message)s",
    datefmt="%H:%M:%S"
)

logger = logging.getLogger("sentinel-ring")


# ── Lifespan: runs code on startup and shutdown ───────────────────────────
# JAVA PARALLEL: This is like @PostConstruct / @PreDestroy in Spring.
@asynccontextmanager
async def lifespan(application: FastAPI):
    """Startup and shutdown events for the FastAPI app."""
    # ── Startup ───────────────────────────────────────────────────────────
    logger.info("🛡️  Sentinel Ring Python agents started on port 8000")
    logger.info("📊 Active endpoints: POST /score/baseline")
    logger.info("📖 API docs available at: http://localhost:8000/docs")
    # FastAPI auto-generates interactive API docs (Swagger UI) at /docs.
    # Spring Boot equivalent: springdoc-openapi + Swagger UI.
    # But FastAPI does it out of the box — no extra dependency needed!
    yield
    # ── Shutdown (runs when server stops) ──────────────────────────────────
    logger.info("Sentinel Ring Python agents shutting down")


# ── Create the FastAPI application ────────────────────────────────────────
app = FastAPI(
    title="Sentinel Ring — Python Agents",
    description=(
        "Python-side agents for the Sentinel Ring fraud detection system. "
        "Provides scoring, graph analysis, evaluation, and explainability "
        "endpoints called by the Spring Boot orchestrator."
    ),
    version="0.1.0",
    lifespan=lifespan,
)

# ── Register routers (like Spring component scanning, but explicit) ───────
# Each router handles a group of related endpoints.
# As we build more agents, we'll add more include_router() calls here.
app.include_router(scoring_router)

# Future stages will add:
# app.include_router(graph_router)     # Stage 3-4: Graph + Ring Detection
# app.include_router(evaluate_router)  # Stage 5: Evaluation
# app.include_router(explain_router)   # Stage 6: Explainability


# ── Health check endpoint ─────────────────────────────────────────────────
# Quick way to test if the server is running.
# Spring Boot has /actuator/health. We'll just add a simple one.
@app.get("/health")
def health():
    return {"status": "ok", "service": "sentinel-ring-agents"}

