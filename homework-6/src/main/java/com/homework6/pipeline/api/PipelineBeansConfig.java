package com.homework6.pipeline.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework6.pipeline.PipelineExecutor;
import com.homework6.pipeline.PipelineSequence;
import com.homework6.pipeline.PipelineSummaryWriter;
import com.homework6.pipeline.agent.ComplianceCheckerAgent;
import com.homework6.pipeline.agent.FraudDetectorAgent;
import com.homework6.pipeline.agent.PipelineAgent;
import com.homework6.pipeline.agent.SettlementProcessorAgent;
import com.homework6.pipeline.agent.TransactionValidatorAgent;
import com.homework6.pipeline.audit.AuditLogger;
import com.homework6.pipeline.messaging.FileMessageBus;
import com.homework6.pipeline.messaging.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires the same framework-agnostic pipeline classes {@code Integrator} uses (agents,
 * {@link PipelineExecutor}, {@link PipelineSummaryWriter}, {@link FileMessageBus}) as
 * Spring beans, so the REST API and the CLI share identical agent instances/behavior
 * without the core classes themselves knowing anything about Spring.
 */
@Configuration
public class PipelineBeansConfig {

    /**
     * Reuses the project's existing snake_case/JavaTimeModule ObjectMapper for HTTP
     * responses too, so the API's JSON shape matches shared/results/*.json exactly.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.instance();
    }

    @Bean
    public AuditLogger auditLogger() {
        return new AuditLogger();
    }

    @Bean
    public TransactionValidatorAgent transactionValidatorAgent(AuditLogger auditLogger) {
        return new TransactionValidatorAgent(auditLogger);
    }

    @Bean
    public FraudDetectorAgent fraudDetectorAgent(AuditLogger auditLogger) {
        return new FraudDetectorAgent(auditLogger);
    }

    @Bean
    public ComplianceCheckerAgent complianceCheckerAgent(AuditLogger auditLogger) {
        return new ComplianceCheckerAgent(auditLogger);
    }

    @Bean
    public SettlementProcessorAgent settlementProcessorAgent(AuditLogger auditLogger) {
        return new SettlementProcessorAgent(auditLogger);
    }

    @Bean
    public Map<String, PipelineAgent> agentsByName(TransactionValidatorAgent validator,
                                                     FraudDetectorAgent fraudDetector,
                                                     ComplianceCheckerAgent complianceChecker,
                                                     SettlementProcessorAgent settlementProcessor) {
        return Map.of(
                validator.name(), validator,
                fraudDetector.name(), fraudDetector,
                complianceChecker.name(), complianceChecker,
                settlementProcessor.name(), settlementProcessor);
    }

    @Bean
    public PipelineSequence pipelineSequence() {
        return PipelineSequence.defaultSequence();
    }

    @Bean
    public PipelineExecutor pipelineExecutor() {
        return new PipelineExecutor();
    }

    @Bean
    public FileMessageBus fileMessageBus() {
        return new FileMessageBus();
    }

    @Bean
    public PipelineSummaryWriter pipelineSummaryWriter(FileMessageBus fileMessageBus) {
        return new PipelineSummaryWriter(fileMessageBus);
    }
}
