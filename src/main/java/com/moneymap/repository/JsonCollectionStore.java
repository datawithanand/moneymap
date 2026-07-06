package com.moneymap.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The JSON file store — the default (and only v1) storage implementation (Section 00 §5.3).
 *
 * Guarantees, per Section 00 §5.3 and Section 16 (Reliability):
 * - ATOMIC WRITES: every write goes to a .tmp file in the same directory, then is renamed
 *   over the target, so a crash or power loss mid-write cannot corrupt existing data.
 * - CONCURRENT-WRITE SAFETY: a per-collection ReentrantLock serializes every
 *   read-modify-write cycle (atomic rename alone does not prevent interleaving).
 * - MALFORMED-FILE RECOVERY: a collection file that fails to parse is logged loudly and
 *   treated as empty — scoped to that one collection, never crashing the whole instance.
 */
@Component
public class JsonCollectionStore {

    private static final Logger log = LoggerFactory.getLogger(JsonCollectionStore.class);

    private final ObjectMapper mapper;
    private final Path dataDir;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public JsonCollectionStore(ObjectMapper mapper, @Value("${moneymap.data-dir}") String dataDir) {
        this.mapper = mapper;
        this.dataDir = Paths.get(dataDir).toAbsolutePath();
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create DATA_DIR at " + this.dataDir, e);
        }
        log.info("JSON collection store initialised at {}", this.dataDir);
    }

    public Path getDataDir() {
        return dataDir;
    }

    private ReentrantLock lockFor(String collection) {
        return locks.computeIfAbsent(collection, k -> new ReentrantLock());
    }

    /** Read a full collection (array file). Returns a mutable copy. */
    public <T> List<T> readAll(String collection, Class<T> type) {
        ReentrantLock lock = lockFor(collection);
        lock.lock();
        try {
            return readListInternal(collection, type);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Read-modify-write under the collection lock. The function mutates the given list
     * in place (or replaces entries); the list is persisted atomically afterwards.
     */
    public <T, R> R mutate(String collection, Class<T> type, Function<List<T>, R> fn) {
        ReentrantLock lock = lockFor(collection);
        lock.lock();
        try {
            List<T> list = readListInternal(collection, type);
            R result = fn.apply(list);
            writeInternal(collection, list);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** Read a singleton-object collection (e.g. global_settings.json). */
    public <T> T readSingleton(String collection, Class<T> type, Supplier<T> defaultSupplier) {
        ReentrantLock lock = lockFor(collection);
        lock.lock();
        try {
            Path file = fileFor(collection);
            if (!Files.exists(file)) {
                return defaultSupplier.get();
            }
            try {
                return mapper.readValue(file.toFile(), type);
            } catch (IOException e) {
                log.error("Failed to parse {} — starting with defaults for this collection. "
                        + "If this is unexpected, restore {} from a backup before making further changes.",
                        file.getFileName(), file.getFileName(), e);
                return defaultSupplier.get();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Read-modify-write for a singleton-object collection, under its lock. */
    public <T> T mutateSingleton(String collection, Class<T> type, Supplier<T> defaultSupplier, UnaryOperator<T> fn) {
        ReentrantLock lock = lockFor(collection);
        lock.lock();
        try {
            Path file = fileFor(collection);
            T current;
            if (Files.exists(file)) {
                try {
                    current = mapper.readValue(file.toFile(), type);
                } catch (IOException e) {
                    log.error("Failed to parse {} — starting with defaults.", file.getFileName(), e);
                    current = defaultSupplier.get();
                }
            } else {
                current = defaultSupplier.get();
            }
            T updated = fn.apply(current);
            writeValueInternal(collection, updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    // ── internals (call only while holding the collection lock) ─────────────

    private Path fileFor(String collection) {
        return dataDir.resolve(collection + ".json");
    }

    private <T> List<T> readListInternal(String collection, Class<T> type) {
        Path file = fileFor(collection);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            var javaType = mapper.getTypeFactory().constructCollectionType(ArrayList.class, type);
            return mapper.readValue(file.toFile(), javaType);
        } catch (IOException e) {
            // Section 16 Reliability: scoped to the single affected collection, never the whole app.
            log.error("Failed to parse {} — starting with an empty collection for this data type. "
                    + "If this is unexpected, restore {} from a backup before making further changes.",
                    file.getFileName(), file.getFileName(), e);
            return new ArrayList<>();
        }
    }

    private void writeInternal(String collection, Object value) {
        writeValueInternal(collection, value);
    }

    private void writeValueInternal(String collection, Object value) {
        Path target = fileFor(collection);
        Path tmp = dataDir.resolve(collection + ".json.tmp");
        try {
            mapper.writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Extremely rare on a same-directory rename; fall back to non-atomic replace.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write collection " + collection, e);
        }
    }
}
