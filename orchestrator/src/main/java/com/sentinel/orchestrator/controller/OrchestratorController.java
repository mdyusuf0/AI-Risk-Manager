package com.sentinel.orchestrator.controller;

import com.sentinel.orchestrator.dto.AnalyzeRequest;
import com.sentinel.orchestrator.dto.OrchestratorResponse;
import com.sentinel.orchestrator.service.OrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<OrchestratorResponse> analyze(@RequestBody AnalyzeRequest request) {
        if (request.getTransactions() == null || request.getTransactions().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        OrchestratorResponse response = orchestratorService.analyze(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"ok\",\"service\":\"sentinel-ring-orchestrator\"}");
    }
}
