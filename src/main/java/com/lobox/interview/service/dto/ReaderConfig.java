package com.lobox.interview.service.dto;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public record ReaderConfig(Path file, List<Function<String[], Void>> consumers) {
}
