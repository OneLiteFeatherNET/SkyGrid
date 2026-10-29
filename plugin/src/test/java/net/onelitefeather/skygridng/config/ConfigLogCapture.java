package net.onelitefeather.skygridng.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures the warnings the config adapters log, so a test can assert on them instead of reading the
 * console. Attach it in a try-with-resources block; closing it detaches the handler again.
 */
final class ConfigLogCapture extends Handler implements AutoCloseable {

    private final Logger logger = Logger.getLogger(ConfigFormats.class.getName());
    private final List<String> warnings = new ArrayList<>();

    ConfigLogCapture() {
        logger.addHandler(this);
    }

    /** The messages of every WARNING record logged while this capture was attached. */
    List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public void publish(LogRecord record) {
        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
            warnings.add(record.getMessage());
        }
    }

    @Override
    public void flush() {
        // nothing is buffered
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
