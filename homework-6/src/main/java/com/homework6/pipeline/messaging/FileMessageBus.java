package com.homework6.pipeline.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework6.pipeline.model.PipelineMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * File-based message bus for agent-to-agent handoff through {@code shared/} directories.
 * All writes are atomic (write to a sibling .tmp file, then {@code ATOMIC_MOVE} into place)
 * so a reader never observes a partially-written message (agents.md: Idempotency rule).
 */
public final class FileMessageBus {

    private static final Logger log = LoggerFactory.getLogger(FileMessageBus.class);
    private static final String JSON_SUFFIX = ".json";

    private final ObjectMapper mapper = JsonMapper.instance();

    public void ensureDirectories(Path... dirs) {
        for (Path dir : dirs) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create pipeline directory " + dir, e);
            }
        }
    }

    /** Reads and parses every message file in {@code dir}, skipping non-message files like pipeline-summary.json. */
    public List<PipelineMessage> listMessages(Path dir) {
        List<PipelineMessage> messages = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + JSON_SUFFIX)) {
            for (Path path : stream) {
                if (path.getFileName().toString().equals("pipeline-summary.json")) {
                    continue;
                }
                messages.add(mapper.readValue(path.toFile(), PipelineMessage.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list messages in " + dir, e);
        }
        return messages;
    }

    public List<PipelineMessage> listByTargetAgent(Path dir, String targetAgent) {
        return listMessages(dir).stream()
                .filter(m -> targetAgent.equals(m.targetAgent()))
                .collect(Collectors.toList());
    }

    public Optional<PipelineMessage> find(Path dir, String transactionId) {
        Path path = dir.resolve(transactionId + JSON_SUFFIX);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(path.toFile(), PipelineMessage.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read message " + path, e);
        }
    }

    public boolean exists(Path dir, String transactionId) {
        return Files.exists(dir.resolve(transactionId + JSON_SUFFIX));
    }

    /** Atomically writes {@code message} into {@code dir}, keyed by its transaction id. */
    public void write(Path dir, PipelineMessage message) {
        Path target = dir.resolve(message.data().transactionId() + JSON_SUFFIX);
        Path tmp = dir.resolve(message.data().transactionId() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), message);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write message to " + target, e);
        }
    }

    /** Relocates a message file unchanged, e.g. {@code shared/input -> shared/processing}. */
    public void moveRaw(Path fromDir, Path toDir, String transactionId) {
        Path from = fromDir.resolve(transactionId + JSON_SUFFIX);
        Path to = toDir.resolve(transactionId + JSON_SUFFIX);
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move message " + transactionId + " from " + fromDir + " to " + toDir, e);
        }
    }

    public void delete(Path dir, String transactionId) {
        try {
            Files.deleteIfExists(dir.resolve(transactionId + JSON_SUFFIX));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete message " + transactionId + " from " + dir, e);
        }
    }

    public void writeJson(Path path, Object value) {
        try {
            mapper.writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path, e);
        }
    }
}
