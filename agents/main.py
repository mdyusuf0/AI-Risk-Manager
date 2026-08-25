from contextlib import asynccontextmanager
from fastapi import FastAPI
from scoring.router import router as scoring_router
from graph.router import router as graph_router
from eval.router import router as eval_router
from explain.router import router as explain_router
import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s - %(message)s")
logger = logging.getLogger("sentinel-ring")

@asynccontextmanager
async def lifespan(application: FastAPI):
    logger.info("sentinel ring python agents started on port 8000")
    yield

app = FastAPI(
    title="Sentinel Ring Agents",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(scoring_router)
app.include_router(graph_router)
app.include_router(eval_router)
app.include_router(explain_router)

@app.get("/health")
def health():
    return {"status": "ok"}
