package com.sentinel.orchestrator.service;

import com.sentinel.ingestion.dto.*;
import com.sentinel.ingestion.service.IngestionService;
import com.sentinel.orchestrator.client.PythonAgentClient;
import com.sentinel.orchestrator.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("OrchestratorService E2E")
class OrchestratorServiceTest {

    private PythonAgentClient mockPythonClient;
    private OrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        mockPythonClient = mock(PythonAgentClient.class);
        IngestionService ingestionService = new IngestionService();
        orchestratorService = new OrchestratorService(ingestionService, mockPythonClient);
        orchestratorService.setThresholds(0.70, 0.60, "USD", 50.0);
    }

    // helper: build a raw transaction
    private RawTransaction raw(String id, double amount, String deviceId, String ip,
                               String accountId, String bank, String time) {
        return new RawTransaction(id, amount, deviceId, ip, accountId, bank, time);
    }

    // ═══════ Fix 1: scoring payload preserves null ═══════

    @Test
    @DisplayName("scoring payload sends JSON null for missing device_id and ip, not empty string")
    void scoringPayloadPreservesNull() {
        // track what payload gets sent to /score/baseline
        List<Map<String, Object>> capturedPayloads = new ArrayList<>();

        when(mockPythonClient.post(eq("/score/baseline"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) invocation.getArgument(1);
            capturedPayloads.add(payload);
            // return minimal valid response
            return Map.of("scores", List.of(
                    Map.of("id", "t1", "risk_score", 0.3)
            ));
        });
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of()));

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(
                raw("t1", 100.0, null, null, "a1", null, null)
        ));

        orchestratorService.analyze(req);

        assertThat(capturedPayloads).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txList = (List<Map<String, Object>>) capturedPayloads.get(0).get("transactions");
        Map<String, Object> txPayload = txList.get(0);

        // device_id and ip must be null, NOT empty string
        assertThat(txPayload).containsEntry("device_id", null);
        assertThat(txPayload).containsEntry("ip", null);
        assertThat(txPayload).containsEntry("id", "t1");
    }

    // ═══════ Fix 2: per-ring time windows ═══════

    @Test
    @DisplayName("different rings receive different per-ring time windows")
    void differentRingsGetDifferentTimeWindows() {
        List<Map<String, Object>> explainPayloads = new ArrayList<>();

        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.3),
                        Map.of("id", "t2", "risk_score", 0.3),
                        Map.of("id", "t3", "risk_score", 0.3),
                        Map.of("id", "t4", "risk_score", 0.3),
                        Map.of("id", "t5", "risk_score", 0.3),
                        Map.of("id", "t6", "risk_score", 0.3)
                )));

        // two rings with different time spans
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of(
                        Map.of("ring_id", "ring-1", "account_ids", List.of("a1", "a2", "a3"),
                                "shared_attrs", List.of("device_id"), "ring_score", 0.65),
                        Map.of("ring_id", "ring-2", "account_ids", List.of("a4", "a5", "a6"),
                                "shared_attrs", List.of("bank_ref"), "ring_score", 0.70)
                )));

        when(mockPythonClient.post(eq("/explain"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) invocation.getArgument(1);
            explainPayloads.add(new HashMap<>(payload));
            return Map.of("explanation", "test explanation");
        });

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(
                // ring-1 accounts: span = 10 days
                raw("t1", 100, "d1", "1.1.1.1", "a1", null, "2019-06-01"),
                raw("t2", 100, "d1", "2.2.2.2", "a2", null, "2019-06-11"),
                raw("t3", 100, "d1", "3.3.3.3", "a3", null, "2019-06-05"),
                // ring-2 accounts: span = 3 days
                raw("t4", 100, "d99", "4.4.4.4", "a4", "BANK1", "2019-07-01"),
                raw("t5", 100, "d98", "5.5.5.5", "a5", "BANK1", "2019-07-04"),
                raw("t6", 100, "d97", "6.6.6.6", "a6", "BANK1", "2019-07-02")
        ));

        orchestratorService.analyze(req);

        assertThat(explainPayloads).hasSize(2);

        // find ring-1 and ring-2 payloads
        Map<String, Object> ring1Explain = explainPayloads.stream()
                .filter(p -> "ring-1".equals(p.get("ring_id"))).findFirst().orElseThrow();
        Map<String, Object> ring2Explain = explainPayloads.stream()
                .filter(p -> "ring-2".equals(p.get("ring_id"))).findFirst().orElseThrow();

        // ring-1: Jun 1 to Jun 11 = 10 days
        assertThat(ring1Explain.get("time_window_days")).isEqualTo(10);
        // ring-2: Jul 1 to Jul 4 = 3 days
        assertThat(ring2Explain.get("time_window_days")).isEqualTo(3);
    }

    @Test
    @DisplayName("same-day timestamps produce no time_window_days claim")
    void sameDayTimestampsNoTimeWindow() {
        List<Map<String, Object>> explainPayloads = new ArrayList<>();

        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.3),
                        Map.of("id", "t2", "risk_score", 0.3),
                        Map.of("id", "t3", "risk_score", 0.3)
                )));

        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of(
                        Map.of("ring_id", "ring-1", "account_ids", List.of("a1", "a2", "a3"),
                                "shared_attrs", List.of("device_id"), "ring_score", 0.65)
                )));

        when(mockPythonClient.post(eq("/explain"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) invocation.getArgument(1);
            explainPayloads.add(new HashMap<>(payload));
            return Map.of("explanation", "test");
        });

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(
                raw("t1", 100, "d1", null, "a1", null, "2019-06-15T10:00:00"),
                raw("t2", 100, "d1", null, "a2", null, "2019-06-15T14:00:00"),
                raw("t3", 100, "d1", null, "a3", null, "2019-06-15T18:00:00")
        ));

        orchestratorService.analyze(req);

        assertThat(explainPayloads).hasSize(1);
        // same day → 0 days → no time_window_days key
        assertThat(explainPayloads.get(0)).doesNotContainKey("time_window_days");
    }

    @Test
    @DisplayName("no valid timestamps produce no time_window_days claim")
    void noValidTimestampsNoTimeWindow() {
        List<Map<String, Object>> explainPayloads = new ArrayList<>();

        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.3),
                        Map.of("id", "t2", "risk_score", 0.3),
                        Map.of("id", "t3", "risk_score", 0.3)
                )));

        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of(
                        Map.of("ring_id", "ring-1", "account_ids", List.of("a1", "a2", "a3"),
                                "shared_attrs", List.of("device_id"), "ring_score", 0.65)
                )));

        when(mockPythonClient.post(eq("/explain"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) invocation.getArgument(1);
            explainPayloads.add(new HashMap<>(payload));
            return Map.of("explanation", "test");
        });

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(
                raw("t1", 100, "d1", null, "a1", null, null),
                raw("t2", 100, "d1", null, "a2", null, "garbage"),
                raw("t3", 100, "d1", null, "a3", null, null)
        ));

        orchestratorService.analyze(req);

        assertThat(explainPayloads).hasSize(1);
        assertThat(explainPayloads.get(0)).doesNotContainKey("time_window_days");
    }

    // ═══════ Fix 4: evaluate error propagation ═══════

    @Test
    @DisplayName("/evaluate failure with supplied ground truth returns server error, not metrics:null")
    void evaluateFailureWithLabelsThrowsError() {
        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.8)
                )));
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of()));
        when(mockPythonClient.post(eq("/evaluate"), any()))
                .thenThrow(new RuntimeException("python agent error on /evaluate: 400"));

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(raw("t1", 5000, "d1", "1.1.1.1", "a1", null, null)));
        req.setGroundTruth(List.of(Map.of("id", "a1", "is_fraud", true)));

        assertThatThrownBy(() -> orchestratorService.analyze(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("/evaluate");
    }

    // ═══════ basic verdict shape ═══════

    @Test
    @DisplayName("valid request returns correct verdict shape")
    void validRequestReturnsVerdict() {
        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.85)
                )));
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of()));

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(raw("t1", 9000, "d1", null, "a1", null, null)));

        OrchestratorResponse response = orchestratorService.analyze(req);

        assertThat(response.getVerdict()).hasSize(1);
        VerdictItem v = response.getVerdict().get(0);
        assertThat(v.getAccountId()).isEqualTo("a1");
        assertThat(v.isFlagged()).isTrue();
        assertThat(v.getRiskScore()).isEqualTo(0.85);
        assertThat(v.getRingId()).isNull();
        assertThat(v.getExplanation()).contains("risk score");
    }

    @Test
    @DisplayName("no ground truth → metrics is null (not an error)")
    void noGroundTruthMetricsNull() {
        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.5)
                )));
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of()));

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(raw("t1", 100, "d1", "1.1.1.1", "a1", null, null)));

        OrchestratorResponse response = orchestratorService.analyze(req);

        assertThat(response.getMetrics()).isNull();
    }

    @Test
    @DisplayName("ground truth supplied → metrics populated from /evaluate")
    void groundTruthProducesMetrics() {
        when(mockPythonClient.post(eq("/score/baseline"), any()))
                .thenReturn(Map.of("scores", List.of(
                        Map.of("id", "t1", "risk_score", 0.85)
                )));
        when(mockPythonClient.post(eq("/graph/detect-rings"), any()))
                .thenReturn(Map.of("rings", List.of()));
        when(mockPythonClient.post(eq("/evaluate"), any()))
                .thenReturn(Map.of("precision", 1.0, "recall", 1.0,
                        "false_positive_cost_estimate", 0.0));

        AnalyzeRequest req = new AnalyzeRequest();
        req.setTransactions(List.of(raw("t1", 9000, "d1", null, "a1", null, null)));
        req.setGroundTruth(List.of(Map.of("id", "a1", "is_fraud", true)));

        OrchestratorResponse response = orchestratorService.analyze(req);

        assertThat(response.getMetrics()).isNotNull();
        assertThat(response.getMetrics().getPrecision()).isEqualTo(1.0);
    }

    // ═══════ computeRingTimeWindow unit tests ═══════

    @Test
    @DisplayName("computeRingTimeWindow returns null when no timestamps")
    void ringTimeWindowNullWhenNoTimestamps() {
        Map<String, CleanAccount> lookup = Map.of(
                "a1", new CleanAccount("a1", Set.of(), Set.of(), Set.of(), null, null),
                "a2", new CleanAccount("a2", Set.of(), Set.of(), Set.of(), null, null)
        );
        Integer result = orchestratorService.computeRingTimeWindow(List.of("a1", "a2"), lookup);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("computeRingTimeWindow returns null for same-day span")
    void ringTimeWindowNullForSameDay() {
        Instant t1 = Instant.parse("2019-06-15T10:00:00Z");
        Instant t2 = Instant.parse("2019-06-15T22:00:00Z");
        Map<String, CleanAccount> lookup = Map.of(
                "a1", new CleanAccount("a1", Set.of(), Set.of(), Set.of(), t1, t1),
                "a2", new CleanAccount("a2", Set.of(), Set.of(), Set.of(), t2, t2)
        );
        Integer result = orchestratorService.computeRingTimeWindow(List.of("a1", "a2"), lookup);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("computeRingTimeWindow returns correct span for multi-day ring")
    void ringTimeWindowMultiDay() {
        Instant day1 = Instant.parse("2019-06-01T00:00:00Z");
        Instant day10 = Instant.parse("2019-06-11T00:00:00Z");
        Map<String, CleanAccount> lookup = Map.of(
                "a1", new CleanAccount("a1", Set.of(), Set.of(), Set.of(), day1, day1),
                "a2", new CleanAccount("a2", Set.of(), Set.of(), Set.of(), day10, day10)
        );
        Integer result = orchestratorService.computeRingTimeWindow(List.of("a1", "a2"), lookup);
        assertThat(result).isEqualTo(10);
    }
}
